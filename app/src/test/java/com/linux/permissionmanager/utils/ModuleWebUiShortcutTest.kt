package com.linux.permissionmanager.utils

import com.linux.permissionmanager.data.InstalledModule
import com.linux.permissionmanager.data.ModuleRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleWebUiShortcutTest {
    @Test
    fun shortcutSpecIsStableAndLauncherFriendly() {
        val module = module(id = "demo.module", name = "演示模块")
        val first = ModuleWebUiShortcut.shortcutSpec(module)
        val second = ModuleWebUiShortcut.shortcutSpec(module)

        assertEquals(first, second)
        assertTrue(first.shortcutId.startsWith("skroot_webui_"))
        assertEquals("演示模块 WebUI", first.shortLabel)
        assertEquals("演示模块 WebUI", first.longLabel)
        assertTrue(first.shortLabel.length <= 10)
        assertTrue(first.longLabel.length <= 25)
        assertEquals(ModuleWebUiShortcut.ACTION_OPEN, "com.linux.permissionmanager.action.OPEN_MODULE_WEBUI")
    }

    @Test
    fun differentModulesReceiveDifferentShortcutIds() {
        val first = ModuleWebUiShortcut.shortcutSpec(module("module.one", "One"))
        val second = ModuleWebUiShortcut.shortcutSpec(module("module.two", "Two"))
        assertNotEquals(first.shortcutId, second.shortcutId)
    }

    @Test
    fun longLabelsAreBounded() {
        val spec = ModuleWebUiShortcut.shortcutSpec(
            module("long.module", "这是一个非常非常长的模块名称用于桌面快捷方式"),
        )
        assertEquals(10, spec.shortLabel.length)
        assertTrue(spec.longLabel.length <= 25)
    }

    @Test
    fun controlCharactersAreRemovedAndBlankNamesUseModuleId() {
        val cleaned = ModuleWebUiShortcut.shortcutSpec(
            module("clean.module", "  Demo\n\tModule  "),
        )
        val fallback = ModuleWebUiShortcut.shortcutSpec(
            module("fallback.module", "\n\t"),
        )

        assertEquals("Demo Modul", cleaned.shortLabel)
        assertEquals("Demo Module WebUI", cleaned.longLabel)
        assertTrue(fallback.longLabel.startsWith("fallback.module"))
    }

    private fun module(id: String, name: String) = InstalledModule(
        name = name,
        description = "",
        version = "1.0",
        id = id,
        author = "",
        updateJson = "",
        minSdk = "",
        hasWebUi = true,
        runState = ModuleRunState.RUNNING,
    )
}
