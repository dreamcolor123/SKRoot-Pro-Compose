package com.linux.permissionmanager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonParsersTest {
    @Test
    fun environmentStatesMatchNativeProtocol() {
        assertEquals(EnvironmentState.RUNNING, JsonParsers.environment("Running"))
        assertEquals(EnvironmentState.NOT_INSTALLED, JsonParsers.environment("NotInstalled"))
        assertEquals(EnvironmentState.FAULT, JsonParsers.environment("Fault: channel"))
        assertEquals(EnvironmentState.UNKNOWN, JsonParsers.environment(""))
    }

    @Test
    fun systemStatusUsesDefaultsForMissingFields() {
        val status = JsonParsers.system("{\"selinux\":1,\"seccomp\":2,\"adb\":true}", true)
        assertEquals(1, status.selinux)
        assertEquals(2, status.seccomp)
        assertTrue(status.adbEnabled)
        assertTrue(status.oplusIntercepted)

        val empty = JsonParsers.system("{}")
        assertEquals(-1, empty.selinux)
        assertEquals(-1, empty.seccomp)
        assertFalse(empty.adbEnabled)
    }

    @Test
    fun suListDecodesPackageNamesAndEnrichesApps() {
        val app = InstalledApp("com.example.app", "Example", null, false)
        val grants = JsonParsers.su(
            "[{\"app_package_name\":\"com.example.app\"},{\"app_package_name\":\"com.android.shell\"}]",
            mapOf(app.packageName to app),
        )
        assertEquals(2, grants.size)
        assertEquals("Example", grants[0].label)
        assertEquals("com.android.shell", grants[1].packageName)
    }

    @Test
    fun moduleParserPreservesMetadataAndRunStates() {
        val raw = """[
          {"desc":{"name":"Demo","desc":"Description","ver":"1.0","id32":"demo","author":"author","update_json":"https%3A%2F%2Fexample.com%2Fu.json","web_ui":true,"min_sdk_ver":"4.0"},"state":"Running"},
          {"desc":{"name":"Removed","id32":"removed"},"state":"RemovedPendingReboot"}
        ]"""
        val modules = JsonParsers.modules(raw)
        assertEquals(2, modules.size)
        assertEquals(ModuleRunState.RUNNING, modules[0].runState)
        assertTrue(modules[0].hasWebUi)
        assertEquals("https://example.com/u.json", modules[0].updateJson)
        assertEquals(ModuleRunState.REMOVED_PENDING_REBOOT, modules[1].runState)
    }

    @Test
    fun marketFiltersBannedEntries() {
        val raw = """{"module_list":[
          {"chn_name":"Visible","id32":"one","download_url":"https://example.com/one.zip"},
          {"chn_name":"Hidden","id32":"two","ban":true}
        ]}"""
        val modules = JsonParsers.market(raw)
        assertEquals(1, modules.size)
        assertEquals("one", modules.single().id)
    }

    @Test
    fun updateParsersRequireVersionAndUrl() {
        val module = JsonParsers.moduleUpdate(
            "{\"version\":\"2.0\",\"zipUrl\":\"https://example.com/a.zip\",\"changelog\":\"https://example.com/c\"}",
            "1.0",
        )
        assertTrue(module!!.hasNewVersion)
        assertEquals("2.0", module.latestVersion)
        assertNull(JsonParsers.moduleUpdate("{\"version\":\"2.0\"}", "1.0"))

        val app = JsonParsers.appUpdate(
            "{\"version\":\"4.5.3\",\"appUrl\":\"https://example.com/app.apk\"}",
            "4.5.3",
        )
        assertFalse(app!!.hasNewVersion)
    }

    @Test
    fun appearanceDefaultsToIndigoWithoutBackground() {
        val appearance = AppearanceSettings()
        assertEquals(PaletteId.INDIGO, appearance.palette)
        assertNull(appearance.backgroundUri)
        assertFalse(appearance.backgroundEnabled)
        assertEquals(0.28f, appearance.backgroundAlpha, 0.0001f)
        assertEquals(5, PaletteId.values().size)
    }

    @Test
    fun marketModuleInstallationFlagDefaultsToFalse() {
        val module = MarketModule(
            chineseName = "Demo",
            englishName = "Demo",
            description = "",
            version = "1.0",
            id = "demo",
            author = "author",
            updateDate = "",
            sourceUrl = "",
            downloadUrl = "https://example.com/demo.zip",
            chineseAlert = "",
            englishAlert = "",
        )
        assertFalse(module.isInstalled)
    }
}
