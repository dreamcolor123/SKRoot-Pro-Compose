package com.linux.permissionmanager.customizer

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal
import kotlin.coroutines.coroutineContext

enum class CustomBuildStage(val label: String, val progress: Float) {
    IDLE("等待配置", 0f),
    PREPARING("正在准备当前 APK 模板", 0.08f),
    MANIFEST("正在修改 Manifest", 0.22f),
    ICONS("正在生成图标资源", 0.38f),
    REPACKING("正在重组并对齐 APK", 0.58f),
    SIGNING("正在使用本地身份签名", 0.78f),
    VERIFYING("正在校验定制 APK", 0.92f),
    INSTALLING("正在等待系统安装确认", 0.98f),
    COMPLETE("构建完成", 1f),
    FAILED("构建失败", 0f),
}

data class CustomBuildRequest(
    val packageName: String,
    val managerName: String,
    val iconUri: Uri,
)

data class CustomBuildArtifact(
    val file: File,
    val packageName: String,
    val managerName: String,
    val certificateSha256: String,
)

object PackageNameValidator {
    private val pattern = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")
    fun error(value: String): String? = when {
        value.isBlank() -> "请输入包名"
        value.length > 180 -> "包名过长"
        !pattern.matches(value) -> "至少包含两段；每段以字母开头，仅可使用字母、数字和下划线"
        else -> null
    }
}

class LocalCustomizerRepository(private val context: Context) {
    companion object {
        private const val KEYSTORE_ALIAS = "skroot_local_customizer_v1"
        private const val OUTPUT_DIR = "local_customizer_output"
        private const val WORK_DIR = "local_customizer_work"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        val iconEntries = linkedMapOf(
            "mdpi" to Triple(48, 108, "res/mipmap-mdpi-v4"),
            "hdpi" to Triple(72, 162, "res/mipmap-hdpi-v4"),
            "xhdpi" to Triple(96, 216, "res/mipmap-xhdpi-v4"),
            "xxhdpi" to Triple(144, 324, "res/mipmap-xxhdpi-v4"),
            "xxxhdpi" to Triple(192, 432, "res/mipmap-xxxhdpi-v4"),
        )
    }

    suspend fun build(
        request: CustomBuildRequest,
        onStage: (CustomBuildStage) -> Unit,
    ): CustomBuildArtifact = withContext(Dispatchers.IO) {
        require(PackageNameValidator.error(request.packageName) == null) { "包名格式错误" }
        require(request.managerName.isNotBlank()) { "请输入管理器名称" }
        onStage(CustomBuildStage.PREPARING)
        coroutineContext.ensureActive()

        val source = File(context.applicationInfo.sourceDir)
        require(source.isFile) { "当前 APK 模板不存在" }
        require(context.applicationInfo.splitSourceDirs.isNullOrEmpty()) { "当前安装包含 split APK，暂不支持本地重打包" }

        val work = File(context.cacheDir, "$WORK_DIR/${System.currentTimeMillis()}").apply { mkdirs() }
        val unsigned = File(work, "unsigned.apk")
        val signed = File(work, "signed.apk")
        try {
            onStage(CustomBuildStage.MANIFEST)
            val manifest = ZipFile(source).use { zip ->
                val entry = requireNotNull(zip.getEntry("AndroidManifest.xml")) { "模板缺少 AndroidManifest.xml" }
                zip.getInputStream(entry).use { it.readBytes() }
            }
            val editedManifest = BinaryManifestEditor.rewrite(manifest, request.packageName, request.managerName)
            coroutineContext.ensureActive()

            onStage(CustomBuildStage.ICONS)
            val icon = decodeIcon(request.iconUri)
            val replacements = try {
                generateIconEntries(icon).toMutableMap()
            } finally {
                icon.recycle()
            }
            replacements["AndroidManifest.xml"] = editedManifest.bytes
            coroutineContext.ensureActive()

            onStage(CustomBuildStage.REPACKING)
            repackAligned(source, unsigned, replacements)
            coroutineContext.ensureActive()

            onStage(CustomBuildStage.SIGNING)
            val identity = localIdentity()
            sign(unsigned, signed, identity)
            coroutineContext.ensureActive()

            onStage(CustomBuildStage.VERIFYING)
            verify(signed, request, editedManifest.originalPackage, identity.certificate)
            coroutineContext.ensureActive()

            val outputDir = File(context.filesDir, OUTPUT_DIR).apply { mkdirs() }
            val destination = File(outputDir, "${request.packageName}.apk")
            outputDir.listFiles()?.filter { it != destination }?.forEach(File::delete)
            signed.copyTo(destination, overwrite = true)
            onStage(CustomBuildStage.COMPLETE)
            CustomBuildArtifact(
                destination,
                request.packageName,
                request.managerName,
                sha256(identity.certificate.encoded),
            )
        } finally {
            work.deleteRecursively()
        }
    }

    suspend fun installedSignatureConflict(packageName: String): Boolean = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(
                packageName,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                },
            )
        }.getOrNull() ?: return@withContext false
        @Suppress("DEPRECATION")
        val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            packageInfo.signatures
        }
        val installed = installedSignatures?.map { sha256(it.toByteArray()) }.orEmpty()
        val local = sha256(localIdentity().certificate.encoded)
        installed.isNotEmpty() && local !in installed
    }

    private fun decodeIcon(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图标图片读取失败" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return requireNotNull(context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }) { "图标图片解码失败" }
    }

    private fun generateIconEntries(source: Bitmap): Map<String, ByteArray> = buildMap {
        iconEntries.values.forEach { (legacySize, foregroundSize, directory) ->
            val legacy = renderCenterCrop(source, legacySize)
            val foreground = renderCenterCrop(source, foregroundSize)
            try {
                val legacyPng = legacy.toPng()
                put("$directory/ic_launcher.png", legacyPng)
                put("$directory/ic_launcher_round.png", legacyPng)
                put("$directory/ic_launcher_foreground.png", foreground.toPng())
            } finally {
                legacy.recycle()
                foreground.recycle()
            }
        }
    }

    private fun renderCenterCrop(source: Bitmap, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val scale = maxOf(size.toFloat() / source.width, size.toFloat() / source.height)
        val width = source.width * scale
        val height = source.height * scale
        val left = (size - width) / 2f
        val top = (size - height) / 2f
        canvas.drawBitmap(source, null, android.graphics.RectF(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use { out ->
        require(compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG 图标生成失败" }
        out.toByteArray()
    }

    private suspend fun repackAligned(source: File, output: File, replacements: Map<String, ByteArray>) {
        ZipFile(source).use { input ->
            val counter = CountingOutputStream(output.outputStream().buffered())
            ZipOutputStream(counter).use { zip ->
                val entries = input.entries()
                while (entries.hasMoreElements()) {
                    coroutineContext.ensureActive()
                    val original = entries.nextElement()
                    if (original.isDirectory || isOldSignature(original.name)) continue
                    val bytes = replacements[original.name] ?: input.getInputStream(original).use { it.readBytes() }
                    val entry = ZipEntry(original.name).apply {
                        method = original.method
                        // DOS epoch avoids ZipOutputStream adding an implicit
                        // timestamp extra field after alignment is calculated.
                        time = 315_532_800_000L
                        if (method == ZipEntry.STORED) {
                            size = bytes.size.toLong()
                            compressedSize = bytes.size.toLong()
                            crc = CRC32().apply { update(bytes) }.value
                            extra = alignmentExtra(counter.count, name, 4)
                        }
                    }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun isOldSignature(name: String): Boolean {
        val upper = name.uppercase(Locale.ROOT)
        return upper == "META-INF/MANIFEST.MF" ||
            (upper.startsWith("META-INF/") && listOf(".SF", ".RSA", ".DSA", ".EC").any(upper::endsWith))
    }

    private fun alignmentExtra(offset: Long, name: String, alignment: Int): ByteArray {
        val nameSize = name.toByteArray(Charsets.UTF_8).size
        val baseWithHeader = offset + 30 + nameSize + 4
        val padding = ((alignment - (baseWithHeader % alignment)) % alignment).toInt()
        return ByteBuffer.allocate(4 + padding).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putShort(0xD935.toShort())
            .putShort(padding.toShort())
            .put(ByteArray(padding))
            .array()
    }

    private data class LocalIdentity(val privateKey: java.security.PrivateKey, val certificate: X509Certificate)

    private fun localIdentity(): LocalIdentity {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(KEYSTORE_ALIAS)) {
            val now = System.currentTimeMillis()
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=SKRoot Local Customizer,O=SKRoot Pro"))
                .setCertificateSerialNumber(BigInteger(128, SecureRandom()).abs().add(BigInteger.ONE))
                .setCertificateNotBefore(Date(now - 86_400_000L))
                .setCertificateNotAfter(Date(now + 20L * 365 * 86_400_000L))
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        val privateKey = store.getKey(KEYSTORE_ALIAS, null) as java.security.PrivateKey
        val certificate = store.getCertificate(KEYSTORE_ALIAS) as X509Certificate
        return LocalIdentity(privateKey, certificate)
    }

    private fun sign(input: File, output: File, identity: LocalIdentity) {
        val signer = ApkSigner.SignerConfig.Builder(
            "skroot-local",
            KeyConfig.Jca(identity.privateKey),
            listOf(identity.certificate),
        ).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setV4SigningEnabled(false)
            .setAlignmentPreserved(true)
            .setDebuggableApkPermitted(true)
            .build()
            .sign()
    }

    private fun verify(
        file: File,
        request: CustomBuildRequest,
        originalPackage: String,
        expectedCertificate: X509Certificate,
    ) {
        val signature = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(26).build().verify()
        require(signature.isVerified && signature.isVerifiedUsingV2Scheme && signature.isVerifiedUsingV3Scheme) {
            "APK v2/v3 签名校验失败: ${signature.errors.joinToString()}"
        }
        require(signature.signerCertificates.any { it.encoded.contentEquals(expectedCertificate.encoded) }) {
            "APK 签名身份校验失败"
        }

        val archiveFlags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, archiveFlags)
            ?: error("系统无法解析生成的 APK")
        require(archive.packageName == request.packageName) { "包名校验失败: ${archive.packageName}" }
        archive.applicationInfo?.let {
            it.sourceDir = file.absolutePath
            it.publicSourceDir = file.absolutePath
            val label = context.packageManager.getApplicationLabel(it).toString()
            require(label == request.managerName) { "管理器名称校验失败: $label" }
            require(it.className == "com.linux.permissionmanager.PermissionManagerApplication") {
                "Application 类名被意外修改: ${it.className}"
            }
        } ?: error("APK 缺少 applicationInfo")
        require(archive.activities.orEmpty().any { it.name == "com.linux.permissionmanager.MainActivity" }) {
            "MainActivity 类名校验失败"
        }
        require(archive.services.orEmpty().any { it.name == "com.linux.permissionmanager.helper.MagicaService" }) {
            "MagicaService 类名校验失败"
        }
        val authorityValues = archive.providers.orEmpty()
            .flatMap { provider -> provider.authority.orEmpty().split(';') }
            .filter(String::isNotBlank)
        val privatePermissions = archive.requestedPermissions.orEmpty().asList() +
            archive.permissions.orEmpty().map { it.name }
        if (originalPackage != request.packageName) {
            require(authorityValues.none { it == originalPackage || it.startsWith("$originalPackage.") }) {
                "Provider authorities 仍包含模板包名"
            }
            require(privatePermissions.none { it == originalPackage || it.startsWith("$originalPackage.") }) {
                "私有 permission 仍包含模板包名"
            }
        }

        ZipFile(file).use { zip ->
            listOf("libmagica.so", "libpermissionmanager.so", "libresetprop.so").forEach { library ->
                require(zip.getEntry("lib/arm64-v8a/$library") != null) { "缺少 arm64 Native 库: $library" }
            }
            iconEntries.values.forEach { (_, _, directory) ->
                require(zip.getEntry("$directory/ic_launcher.png") != null) { "缺少图标资源: $directory" }
                require(zip.getEntry("$directory/ic_launcher_round.png") != null) { "缺少圆形图标资源: $directory" }
                require(zip.getEntry("$directory/ic_launcher_foreground.png") != null) { "缺少自适应图标资源: $directory" }
            }
            require(zip.getEntry("res/mipmap-anydpi-v26/ic_launcher.xml") != null) { "缺少自适应图标定义" }
            require(zip.getEntry("res/mipmap-anydpi-v26/ic_launcher_round.xml") != null) { "缺少圆形自适应图标定义" }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(Locale.ROOT, it) }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var count: Long = 0
            private set

        override fun write(value: Int) {
            out.write(value)
            count++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
            count += length
        }
    }
}
