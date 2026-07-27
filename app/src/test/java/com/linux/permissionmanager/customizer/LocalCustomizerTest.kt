package com.linux.permissionmanager.customizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import com.linux.permissionmanager.ui.LocalCustomizerUiState
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LocalCustomizerTest {
    @Test
    fun adaptiveIconUsesStandardSafeContentArea() {
        assertEquals(72, adaptiveIconContentSize(108))
        assertEquals(288, adaptiveIconContentSize(432))
    }

    @Test
    fun incompleteCustomizerInputUsesDefaultsAndCanBuild() {
        val state = LocalCustomizerUiState(
            defaultPackageName = "com.example.defaultmanager",
            defaultManagerName = "Default Manager",
        )
        assertEquals("com.example.defaultmanager", state.effectivePackageName)
        assertEquals("Default Manager", state.effectiveManagerName)
        assertTrue(state.canBuild)

        val customized = state.copy(packageName = " com.example.custom ", managerName = " Custom ")
        assertEquals("com.example.custom", customized.effectivePackageName)
        assertEquals("Custom", customized.effectiveManagerName)
    }

    @Test
    fun packageNameValidationFollowsApplicationIdRules() {
        assertNull(PackageNameValidator.error("com.example.manager"))
        assertNull(PackageNameValidator.error("Com.Example2.manager_build"))
        assertTrue(PackageNameValidator.error("manager") != null)
        assertTrue(PackageNameValidator.error("com.2manager") != null)
        assertTrue(PackageNameValidator.error("com.example-manager") != null)
        assertTrue(PackageNameValidator.error(" com.example.manager") != null)
    }

    @Test
    fun binaryManifestRewriteOnlyChangesApplicationIdDerivedValues() {
        val source = manifestFixture()
        val result = BinaryManifestEditor.rewrite(source, "com.example.custom", "Custom Manager")

        assertEquals("com.linux.permissionmanager", result.originalPackage)
        assertEquals(5, result.changedValues)
        assertTrue(result.bytes.containsEncodedString("com.example.custom.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
        assertTrue(result.bytes.containsEncodedString("unrelated.provider;com.example.custom.androidx-startup"))
        assertTrue(result.bytes.containsEncodedString("Custom Manager"))
        assertTrue(result.bytes.containsEncodedString("SKRoot(Pro)"))
        assertTrue(result.bytes.containsEncodedString("com.linux.permissionmanager.MainActivity"))
        assertFalse(result.bytes.containsEncodedString("Custom Manager.MainActivity"))

        val second = BinaryManifestEditor.rewrite(result.bytes, "org.sample.manager", "Another Manager")
        assertEquals("com.example.custom", second.originalPackage)
    }

    @Test
    fun rewritesRealAaptManifestWhenFixtureIsProvided() {
        val fixture = System.getenv("SKP_REAL_MANIFEST")?.let(::File)
        assumeTrue("Set SKP_REAL_MANIFEST to exercise an assembled APK manifest", fixture?.isFile == true)
        val result = BinaryManifestEditor.rewrite(
            fixture!!.readBytes(),
            "com.example.devicebuild",
            "设备内定制管理器",
        )
        assertEquals("com.linux.permissionmanager", result.originalPackage)
        assertTrue(result.changedValues >= 4)
        assertTrue(result.bytes.containsEncodedString("com.example.devicebuild.androidx-startup"))
        assertTrue(result.bytes.containsEncodedString("com.example.devicebuild.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
        assertTrue(result.bytes.containsEncodedString("com.linux.permissionmanager.MainActivity"))
        assertTrue(result.bytes.containsEncodedString("设备内定制管理器"))
        assertEquals(
            "com.example.devicebuild",
            BinaryManifestEditor.rewrite(result.bytes, "org.example.second", "Second").originalPackage,
        )
    }

    private fun manifestFixture(): ByteArray {
        val values = listOf(
            "manifest",
            "package",
            "com.linux.permissionmanager",
            "permission",
            "uses-permission",
            "name",
            "com.linux.permissionmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            "application",
            "label",
            "SKRoot(Pro)",
            "provider",
            "authorities",
            "unrelated.provider;com.linux.permissionmanager.androidx-startup",
            "activity",
            "com.linux.permissionmanager.MainActivity",
            "meta-data",
            "value",
        )
        val index = values.withIndex().associate { it.value to it.index }
        val chunks = listOf(
            stringPool(values),
            startElement(index.getValue("manifest"), listOf(attribute(index, "package", "com.linux.permissionmanager"))),
            startElement(index.getValue("permission"), listOf(attribute(index, "name", "com.linux.permissionmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))),
            startElement(index.getValue("uses-permission"), listOf(attribute(index, "name", "com.linux.permissionmanager.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))),
            startElement(index.getValue("application"), listOf(attribute(index, "label", "SKRoot(Pro)"))),
            startElement(index.getValue("provider"), listOf(attribute(index, "authorities", "unrelated.provider;com.linux.permissionmanager.androidx-startup"))),
            startElement(index.getValue("activity"), listOf(attribute(index, "name", "com.linux.permissionmanager.MainActivity"))),
            startElement(index.getValue("meta-data"), listOf(attribute(index, "value", "SKRoot(Pro)"))),
        )
        val size = 8 + chunks.sumOf(ByteArray::size)
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0003)
            putShort(8)
            putInt(size)
            chunks.forEach(::put)
        }.array()
    }

    private fun stringPool(values: List<String>): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = IntArray(values.size)
        values.forEachIndexed { position, value ->
            offsets[position] = data.size()
            val bytes = value.toByteArray(Charsets.UTF_8)
            writeLength8(data, value.length)
            writeLength8(data, bytes.size)
            data.write(bytes)
            data.write(0)
        }
        while (data.size() % 4 != 0) data.write(0)
        val headerSize = 28
        val stringsStart = headerSize + offsets.size * 4
        val totalSize = stringsStart + data.size()
        return ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0001)
            putShort(headerSize.toShort())
            putInt(totalSize)
            putInt(values.size)
            putInt(0)
            putInt(0x00000100)
            putInt(stringsStart)
            putInt(0)
            offsets.forEach(::putInt)
            put(data.toByteArray())
        }.array()
    }

    private data class Attribute(val name: Int, val value: Int)

    private fun attribute(index: Map<String, Int>, name: String, value: String) =
        Attribute(index.getValue(name), index.getValue(value))

    private fun startElement(name: Int, attributes: List<Attribute>): ByteArray {
        val size = 36 + attributes.size * 20
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0102)
            putShort(16)
            putInt(size)
            putInt(1)
            putInt(-1)
            putInt(-1)
            putInt(name)
            putShort(20)
            putShort(20)
            putShort(attributes.size.toShort())
            putShort(0)
            putShort(0)
            putShort(0)
            attributes.forEach { attribute ->
                putInt(-1)
                putInt(attribute.name)
                putInt(attribute.value)
                putShort(8)
                put(0)
                put(0x03)
                putInt(attribute.value)
            }
        }.array()
    }

    private fun writeLength8(output: ByteArrayOutputStream, length: Int) {
        if (length >= 0x80) output.write((length shr 8) or 0x80)
        output.write(length and 0xff)
    }

    private fun ByteArray.containsEncodedString(value: String): Boolean =
        containsBytes(value.toByteArray(Charsets.UTF_8)) || containsBytes(value.toByteArray(Charsets.UTF_16LE))

    private fun ByteArray.containsBytes(needle: ByteArray): Boolean {
        return indices.any { start ->
            start + needle.size <= size && needle.indices.all { offset -> this[start + offset] == needle[offset] }
        }
    }
}
