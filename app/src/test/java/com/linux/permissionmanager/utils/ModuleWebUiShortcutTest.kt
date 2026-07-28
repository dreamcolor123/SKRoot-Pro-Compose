package com.linux.permissionmanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleWebUiShortcutTest {
    @Test
    fun shortcutSpecUsesOnlyOpaqueIdentityAndCustomLabel() {
        val request = request(moduleId = "demo.module", moduleName = "演示模块", shortcutName = "我的工具")
        val opaqueId = "AbCdEfGhIjKlMnOpQrStUvWxYz012345"
        val first = ModuleWebUiShortcut.shortcutSpec(request, opaqueId)
        val second = ModuleWebUiShortcut.shortcutSpec(request, opaqueId)

        assertEquals(first, second)
        assertEquals(opaqueId, first.opaqueId)
        assertTrue(first.shortcutId.startsWith("skroot_webui_v2_"))
        assertFalse(first.shortcutId.contains(request.moduleId))
        assertEquals("我的工具", first.shortLabel)
        assertEquals("我的工具", first.longLabel)
        assertEquals(ModuleWebUiShortcut.ACTION_OPEN, "com.linux.permissionmanager.action.OPEN_MODULE_WEBUI")
        assertEquals(ModuleWebUiShortcut.EXTRA_OPAQUE_ID, "module_webui_shortcut_id")
    }

    @Test
    fun differentOpaqueIdsReceiveDifferentShortcutIds() {
        val request = request("module.one", "One", "One WebUI")
        val first = ModuleWebUiShortcut.shortcutSpec(request, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        val second = ModuleWebUiShortcut.shortcutSpec(request, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
        assertNotEquals(first.shortcutId, second.shortcutId)
    }

    @Test
    fun longAndControlCharacterLabelsAreNormalized() {
        val long = ModuleWebUiShortcut.shortcutSpec(
            request("long.module", "模块", "这是一个非常非常长的快捷方式名称用于测试"),
            "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
        )
        val cleaned = ModuleWebUiShortcut.shortcutSpec(
            request("clean.module", "Demo", "  Demo\n\tModule  "),
            "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD",
        )

        assertEquals(10, long.shortLabel.length)
        assertTrue(long.longLabel.length <= 25)
        assertEquals("Demo Modul", cleaned.shortLabel)
        assertEquals("Demo Module", cleaned.longLabel)
    }

    @Test
    fun blankCustomNameFallsBackToModuleName() {
        val spec = ModuleWebUiShortcut.shortcutSpec(
            request("fallback.module", "Fallback", "\n\t"),
            "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
        )
        assertEquals("Fallback W", spec.shortLabel)
        assertEquals("Fallback WebUI", spec.longLabel)
    }

    @Test
    fun opaqueIdIsUrlSafeAndMappingRoundTrips() {
        val opaqueId = ModuleWebUiShortcut.opaqueIdFromBytes(ByteArray(24) { it.toByte() })
        val encoded = ModuleWebUiShortcut.encodeMappings(mapOf(opaqueId to "module.private"))
        val decoded = ModuleWebUiShortcut.decodeMappings(encoded)

        assertEquals(32, opaqueId.length)
        assertTrue(opaqueId.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals("module.private", decoded[opaqueId])
        assertEquals(1, decoded.size)
    }

    @Test
    fun invalidMappingDataIsIgnored() {
        assertTrue(ModuleWebUiShortcut.decodeMappings("not-json").isEmpty())
        assertTrue(ModuleWebUiShortcut.decodeMappings("{\"short\":\"module\"}").isEmpty())
    }

    private fun request(
        moduleId: String,
        moduleName: String,
        shortcutName: String,
    ) = ModuleWebUiShortcutRequest(
        moduleId = moduleId,
        moduleName = moduleName,
        shortcutName = shortcutName,
    )
}
