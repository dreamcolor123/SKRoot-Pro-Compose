package com.linux.permissionmanager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.text.TextUtils
import com.linux.permissionmanager.AppSettings
import com.linux.permissionmanager.BuildConfig
import com.linux.permissionmanager.bridge.NativeBridge
import com.linux.permissionmanager.utils.FileUtils
import com.linux.permissionmanager.utils.NetUtils
import com.linux.permissionmanager.customizer.LocalCustomizerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import kotlin.coroutines.resume

class UiEventBus {
    private val mutableEvents = MutableSharedFlow<UiEffect>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<UiEffect> = mutableEvents
    fun emit(effect: UiEffect) { mutableEvents.tryEmit(effect) }
}

class SettingsStore {
    var rootKey: String
        get() = AppSettings.getString("rootKey", "")
        set(value) = AppSettings.setString("rootKey", value)
    var hotload: Boolean
        get() = AppSettings.getBoolean(AppSettings.KEY_IS_HOTLOAD_MODE, false)
        set(value) = AppSettings.setBoolean(AppSettings.KEY_IS_HOTLOAD_MODE, value)
    var hotloadCommand: String
        get() = AppSettings.getString("hotloadCmd", "")
        set(value) = AppSettings.setString("hotloadCmd", value)
    var hotloadMethod: String
        get() = AppSettings.getString("hotloadMethod", "SHELL")
        set(value) = AppSettings.setString("hotloadMethod", value)
    var lastRootCommand: String
        get() = AppSettings.getString("lastInputCmd", "id")
        set(value) = AppSettings.setString("lastInputCmd", value)

    fun getString(key: String, fallback: String = "") = AppSettings.getString(key, fallback)
    fun putString(key: String, value: String) = AppSettings.setString(key, value)
}

class SkrootRepository(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val nativeMutex = Mutex()

    private suspend fun <T> native(block: () -> T): T = withContext(dispatcher) {
        nativeMutex.withLock { block() }
    }

    suspend fun environmentState(key: String) = native { NativeBridge.getSkrootEnvState(key) }
    suspend fun installedVersion(key: String) = native { NativeBridge.getInstalledSkrootEnvVersion(key) }
    suspend fun sdkVersion() = native { NativeBridge.getSdkVersion() }
    suspend fun systemStatusJson() = native { NativeBridge.getSystemStatusJson() }
    suspend fun installEnvironment(key: String, hotload: Boolean) = native { NativeBridge.installSkrootEnv(key, hotload) }
    suspend fun uninstallEnvironment(key: String) = native { NativeBridge.uninstallSkrootEnv(key) }
    suspend fun testRoot(key: String) = native { NativeBridge.testRoot(key) }
    suspend fun runCommand(key: String, command: String) = native { NativeBridge.runRootCmd(key, command) }
    suspend fun testBasics(key: String, item: String) = native { NativeBridge.testSkrootBasics(key, item) }
    suspend fun testDefaultModule(key: String, item: String) = native { NativeBridge.testSkrootDefaultModule(key, item) }
    suspend fun oneplusStage1(key: String) = native { NativeBridge.oneplusBypassWriteStage1(key) }
    suspend fun oneplusNormal(key: String) = native { NativeBridge.oneplusBypassIsWorkNormal(key) }

    suspend fun suList(key: String) = native { NativeBridge.getSuAuthList(key) }
    suspend fun addSu(key: String, packageName: String) = native { NativeBridge.addSuAuth(key, packageName) }
    suspend fun removeSu(key: String, packageName: String) = native { NativeBridge.removeSuAuth(key, packageName) }
    suspend fun clearSu(key: String) = native { NativeBridge.clearSuAuthList(key) }

    suspend fun moduleList(key: String) = native { NativeBridge.getSkrootModuleList(key) }
    suspend fun installModule(key: String, path: String, runOnce: Boolean) = native {
        NativeBridge.installSkrootModule(key, path, runOnce)
    }
    suspend fun removeModule(key: String, id: String) = native { NativeBridge.uninstallSkrootModule(key, id) }
    suspend fun openModuleWebUi(key: String, id: String) = native { NativeBridge.openSkrootModuleWebUI(key, id) }

    suspend fun bootFailEnabled(key: String) = native { NativeBridge.isBootFailProtectEnabled(key) }
    suspend fun setBootFailEnabled(key: String, enabled: Boolean) = native {
        NativeBridge.setBootFailProtectEnabled(key, enabled)
    }
    suspend fun adbForcedDisabled(key: String) = native { NativeBridge.isAdbForcedDisabled(key) }
    suspend fun setAdbForcedDisabled(key: String, enabled: Boolean) = native {
        NativeBridge.setAdbForcedDisabled(key, enabled)
    }
    suspend fun logEnabled(key: String) = native { NativeBridge.isSkrootLogEnabled(key) }
    suspend fun setLogEnabled(key: String, enabled: Boolean) = native {
        NativeBridge.setSkrootLogEnabled(key, enabled)
    }
    suspend fun readLog(key: String) = native { NativeBridge.readSkrootLog(key) }
    suspend fun clearLog(key: String) = native { NativeBridge.clearSkrootLog(key) }
    suspend fun softReboot(key: String) = native { NativeBridge.restartZygote64(key) }
}

class PackageRepository(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun installedApps(): List<InstalledApp> = withContext(dispatcher) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        pm.getInstalledPackages(0).asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { info ->
                val app = info.applicationInfo ?: return@mapNotNull null
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                    icon = runCatching { app.loadIcon(pm) }.getOrNull(),
                    system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }
}

object JsonParsers {
    fun environment(raw: String): EnvironmentState = when {
        raw.contains("NotInstalled", true) -> EnvironmentState.NOT_INSTALLED
        raw.contains("Fault", true) -> EnvironmentState.FAULT
        raw.contains("Running", true) -> EnvironmentState.RUNNING
        raw.isBlank() -> EnvironmentState.UNKNOWN
        else -> EnvironmentState.UNKNOWN
    }

    fun effectiveEnvironment(
        raw: String,
        installedVersion: String,
        sdkVersion: String,
        pendingReboot: Boolean = false,
    ): EnvironmentState {
        if (pendingReboot) return EnvironmentState.PENDING_REBOOT
        val base = environment(raw)
        return if (base != EnvironmentState.NOT_INSTALLED && isVersionOlder(installedVersion, sdkVersion)) {
            EnvironmentState.OUTDATED
        } else base
    }

    fun isVersionOlder(installedVersion: String, sdkVersion: String): Boolean {
        val installed = parseVersion(installedVersion) ?: return false
        val sdk = parseVersion(sdkVersion) ?: return false
        return installed.zip(sdk).firstOrNull { (left, right) -> left != right }
            ?.let { (left, right) -> left < right }
            ?: false
    }

    private fun parseVersion(value: String): List<Int>? {
        val match = Regex("^\\s*[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?\\s*$").matchEntire(value)
            ?: return null
        return match.groupValues.drop(1).mapNotNull(String::toIntOrNull).takeIf { it.size == 3 }
    }

    fun system(raw: String, oplus: Boolean = false): SystemStatus {
        val json = JSONObject(raw)
        return SystemStatus(
            selinux = json.optInt("selinux", -1),
            seccomp = json.optInt("seccomp", -1),
            adbEnabled = json.optBoolean("adb", false),
            oplusIntercepted = oplus,
        )
    }

    fun su(raw: String, apps: Map<String, InstalledApp>): List<SuGrant> {
        val array = JSONArray(raw)
        return buildList {
            repeat(array.length()) { index ->
                val packageName = URLDecoder.decode(
                    array.getJSONObject(index).getString("app_package_name"), "UTF-8"
                )
                val app = apps[packageName]
                add(SuGrant(packageName, app?.label.orEmpty(), app?.icon))
            }
        }
    }

    fun modules(raw: String): List<InstalledModule> {
        val array = JSONArray(raw)
        return buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val desc = item.optJSONObject("desc") ?: JSONObject()
                fun decoded(key: String) = URLDecoder.decode(desc.optString(key, ""), "UTF-8")
                val state = when (item.optString("state", "").lowercase(Locale.ROOT)) {
                    "running" -> ModuleRunState.RUNNING
                    "abnormal" -> ModuleRunState.ABNORMAL
                    "removedpendingreboot", "removed_pending_reboot", "remove_pending_reboot" -> ModuleRunState.REMOVED_PENDING_REBOOT
                    else -> ModuleRunState.NOT_RUNNING
                }
                add(
                    InstalledModule(
                        name = decoded("name"),
                        description = decoded("desc"),
                        version = decoded("ver"),
                        id = decoded("id32"),
                        author = decoded("author"),
                        updateJson = decoded("update_json"),
                        minSdk = decoded("min_sdk_ver"),
                        hasWebUi = desc.optBoolean("web_ui", false),
                        runState = state,
                    )
                )
            }
        }
    }

    fun market(raw: String): List<MarketModule> {
        val root = JSONObject(raw)
        val array = root.optJSONArray("module_list") ?: return emptyList()
        return buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                if (item.optBoolean("ban", false)) return@repeat
                add(
                    MarketModule(
                        chineseName = item.optString("chn_name", ""),
                        englishName = item.optString("eng_name", ""),
                        description = item.optString("desc", ""),
                        version = item.optString("ver", ""),
                        id = item.optString("id32", ""),
                        author = item.optString("author", ""),
                        updateDate = item.optString("update_date", ""),
                        sourceUrl = item.optString("source_url", ""),
                        downloadUrl = item.optString("download_url", ""),
                        chineseAlert = item.optString("download_chn_alert", ""),
                        englishAlert = item.optString("download_eng_alert", ""),
                    )
                )
            }
        }
    }

    fun moduleUpdate(raw: String, current: String): ModuleUpdate? {
        if (raw.isBlank()) return null
        val json = JSONObject(raw)
        val version = json.optString("version", "")
        val url = json.optString("zipUrl", "")
        if (version.isBlank() || url.isBlank()) return null
        return ModuleUpdate(version != current, version, url, json.optString("changelog", ""))
    }

    fun appUpdate(raw: String, current: String): AppUpdate? {
        if (raw.isBlank()) return null
        val json = JSONObject(raw)
        val version = json.optString("version", "")
        val url = json.optString("appUrl", "")
        if (version.isBlank() || url.isBlank()) return null
        return AppUpdate(version != current, version, url, json.optString("changelog", ""))
    }
}

class NetworkRepository {
    suspend fun text(url: String): String = suspendCancellableCoroutine { continuation ->
        val handle = NetUtils.downloadText(url, object : NetUtils.TextDownloadCallback {
            override fun onSuccess(content: String) {
                if (continuation.isActive) continuation.resume(content)
            }
            override fun onError(e: Exception) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
        })
        continuation.invokeOnCancellation { handle.cancel() }
    }

    fun download(
        url: String,
        destination: File,
        onStart: (Long) -> Unit,
        onProgress: (Long, Long) -> Unit,
        onComplete: (Result<File>) -> Unit,
    ): NetUtils.DownloadHandle = NetUtils.downloadFile(url, destination, object : NetUtils.DownloadCallback {
        override fun onStart(totalBytes: Long) = onStart(totalBytes)
        override fun onProgress(downloadedBytes: Long, totalBytes: Long) = onProgress(downloadedBytes, totalBytes)
        override fun onCompleted(destFile: File) = onComplete(Result.success(destFile))
        override fun onError(e: Exception) = onComplete(Result.failure(e))
    })
}

class ModuleRepository(
    private val context: Context,
    private val native: SkrootRepository,
    private val network: NetworkRepository,
    private val settings: SettingsStore,
) {
    companion object {
        const val MARKET_URL = "https://abcz316.github.io/SKRoot-linuxKernelRoot/skroot_pro_app/module_market.json"
    }

    suspend fun installed(key: String) = JsonParsers.modules(native.moduleList(key))
    suspend fun market() = JsonParsers.market(network.text(MARKET_URL))

    suspend fun copyUriToCache(uri: Uri): String = withContext(Dispatchers.IO) {
        FileUtils.getPathFromUriByCopy(context, uri) ?: error("无效文件或复制失败")
    }

    suspend fun install(key: String, path: String, runOnce: Boolean): String {
        var result = native.installModule(key, path, runOnce)
        result += when {
            result.contains("OK") -> if (settings.hotload) "，已生效" else "，重启后生效"
            result.contains("ERR_MODULE_REQUIRE_MIN_SDK") -> "，当前 SKRoot 环境版本太低，请先升级"
            result.contains("ERR_MODULE_SDK_TOO_OLD") -> "，该模块 SDK 版本太低"
            else -> ""
        }
        return result
    }

    suspend fun remove(key: String, module: InstalledModule): String {
        var result = native.removeModule(key, module.id)
        if (result.contains("OK")) result += "，重启后生效"
        return result
    }

    suspend fun openWebUi(key: String, module: InstalledModule) = native.openModuleWebUi(key, module.id)

    suspend fun checkUpdate(module: InstalledModule): ModuleUpdate? {
        if (module.updateJson.isBlank()) return null
        val raw = network.text(module.updateJson)
        settings.putString("module_update_${module.id}", raw)
        return JsonParsers.moduleUpdate(raw, module.version)
    }

    fun cachedUpdate(module: InstalledModule): ModuleUpdate? = runCatching {
        JsonParsers.moduleUpdate(settings.getString("module_update_${module.id}"), module.version)
    }.getOrNull()

    suspend fun changelog(update: ModuleUpdate) = network.text(update.changelogUrl)
    fun downloadFileName(moduleId: String, version: String) = "${moduleId}_${version}.zip"
    fun cacheFile(name: String) = File(File(context.cacheDir, "skrmods").apply { mkdirs() }, name)
    fun delete(file: File) = FileUtils.deleteFile(file)
}

class UpdateRepository(
    private val network: NetworkRepository,
    private val settings: SettingsStore,
) {
    companion object {
        const val UPDATE_URL = "https://abcz316.github.io/SKRoot-linuxKernelRoot/skroot_pro_app/update.json"
    }
    fun cached(): AppUpdate? = runCatching {
        JsonParsers.appUpdate(settings.getString("app_update_cache"), BuildConfig.VERSION_NAME)
    }.getOrNull()
    suspend fun refresh(): AppUpdate? {
        val raw = network.text(UPDATE_URL)
        settings.putString("app_update_cache", raw)
        return JsonParsers.appUpdate(raw, BuildConfig.VERSION_NAME)
    }
    suspend fun changelog(update: AppUpdate) = network.text(update.changelogUrl)
}

class AppContainer(context: Context) {
    val settings = SettingsStore()
    val appearance = AppearanceStore(context.applicationContext)
    val customizer = LocalCustomizerRepository(context.applicationContext)
    val events = UiEventBus()
    val native = SkrootRepository()
    val packages = PackageRepository(context.applicationContext)
    val network = NetworkRepository()
    val modules = ModuleRepository(context.applicationContext, native, network, settings)
    val updates = UpdateRepository(network, settings)
}
