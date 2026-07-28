package com.linux.permissionmanager.data

import com.linux.permissionmanager.ui.SettingsUiState
import com.linux.permissionmanager.ui.theme.AppearanceTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class JsonParsersTest {
    @Test
    fun environmentStatesMatchNativeProtocol() {
        assertEquals(EnvironmentState.RUNNING, JsonParsers.environment("Running"))
        assertEquals(EnvironmentState.NOT_INSTALLED, JsonParsers.environment("NotInstalled"))
        assertEquals(EnvironmentState.FAULT, JsonParsers.environment("Fault: channel"))
        assertEquals(EnvironmentState.UNKNOWN, JsonParsers.environment(""))
    }

    @Test
    fun environmentDetectsOutdatedCoreAndPendingReboot() {
        assertTrue(JsonParsers.isVersionOlder("4.5.3", "4.5.4"))
        assertFalse(JsonParsers.isVersionOlder("4.5.4", "4.5.4"))
        assertFalse(JsonParsers.isVersionOlder("ERR_MODULE", "4.5.4"))
        assertEquals(
            EnvironmentState.OUTDATED,
            JsonParsers.effectiveEnvironment("Running", "4.5.3", "4.5.4"),
        )
        assertEquals(
            EnvironmentState.PENDING_REBOOT,
            JsonParsers.effectiveEnvironment("Fault", "4.5.4", "4.5.4", pendingReboot = true),
        )
        assertEquals(
            EnvironmentState.NOT_INSTALLED,
            JsonParsers.effectiveEnvironment("NotInstalled", "4.5.3", "4.5.4"),
        )
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
    fun moduleUpdateParserKeepsUpstreamProtocol() {
        val module = JsonParsers.moduleUpdate(
            "{\"version\":\"2.0\",\"zipUrl\":\"https://example.com/a.zip\",\"changelog\":\"https://example.com/c\"}",
            "1.0",
        )
        assertTrue(module!!.hasNewVersion)
        assertEquals("2.0", module.latestVersion)
        assertNull(JsonParsers.moduleUpdate("{\"version\":\"2.0\"}", "1.0"))
        assertEquals(
            "https://abcz316.github.io/SKRoot-linuxKernelRoot/skroot_pro_app/module_market.json",
            ModuleRepository.MARKET_URL,
        )
    }

    @Test
    fun managerUpdateUsesRepositoryReleaseAndCanonicalVersion() {
        val update = JsonParsers.managerRelease(
            """{
              "tag_name":"v4.5.4.11",
              "body":"Release notes",
              "assets":[
                {"name":"source.zip","browser_download_url":"https://example.com/source.zip"},
                {"name":"v4.5.4.11-UI重构版-SKRoot Pro.apk","browser_download_url":"https://example.com/manager.apk"}
              ]
            }""",
            "4.5.4.10",
        )
        assertTrue(update!!.hasNewVersion)
        assertEquals("4.5.4.11", update.latestVersion)
        assertEquals("https://example.com/manager.apk", update.downloadUrl)
        assertEquals("Release notes", update.releaseNotes)
        assertEquals("4.5.4.9", JsonParsers.normalizeManagerVersion("v4.5.4-compose.9"))
        assertFalse(JsonParsers.isManagerVersionNewer("v4.5.4.9", "4.5.4.10"))
        assertTrue(JsonParsers.isManagerVersionNewer("v4.5.5.1", "4.5.4.999"))
        assertNull(JsonParsers.managerRelease("{\"version\":\"4.5.4\",\"appUrl\":\"https://example.com/official.apk\"}", "4.5.4.10"))
        assertEquals(
            "https://api.github.com/repos/dreamcolor123/SKRoot-Pro-Compose/releases/latest",
            UpdateRepository.UPDATE_URL,
        )

        val legacyRelease = JsonParsers.managerRelease(
            """{
              "tag_name":"v4.5.4-compose.9",
              "body":"Legacy notes",
              "assets":[
                {"name":"v4.5.4-compose.9-UI.-SKRoot.Pro.apk","browser_download_url":"https://example.com/legacy.apk"}
              ]
            }""",
            "4.5.4.8",
        )
        assertTrue(legacyRelease!!.hasNewVersion)
        assertEquals("4.5.4.9", legacyRelease.latestVersion)
        assertEquals("https://example.com/legacy.apk", legacyRelease.downloadUrl)
    }

    @Test
    fun parsesLiveRepositoryReleaseWhenFixtureIsProvided() {
        val fixture = System.getenv("SKP_RELEASE_JSON")?.let(::File)
        assumeTrue("Set SKP_RELEASE_JSON to a GitHub latest-release response", fixture?.isFile == true)
        val update = JsonParsers.managerRelease(fixture!!.readText(), "0.0.0.0")
        assertTrue(update != null)
        assertTrue(update!!.hasNewVersion)
        assertTrue(update.downloadUrl.startsWith("https://github.com/dreamcolor123/SKRoot-Pro-Compose/releases/download/"))
        assertTrue(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(update.latestVersion))
    }

    @Test
    fun appearanceDefaultsToIndigoWithoutBackground() {
        val appearance = AppearanceSettings()
        assertEquals(PaletteId.INDIGO, appearance.palette)
        assertNull(appearance.backgroundUri)
        assertFalse(appearance.backgroundEnabled)
        assertEquals(0.28f, appearance.backgroundAlpha, 0.0001f)
        assertEquals(0f, appearance.chromeTransparency, 0.0001f)
        assertEquals(1f, appearance.chromeSurfaceAlpha, 0.0001f)
        assertEquals(0f, appearance.copy(chromeTransparency = 1f).chromeSurfaceAlpha, 0.0001f)
        assertEquals(0.24f, appearance.controlTransparency, 0.0001f)
        assertEquals(0.76f, appearance.controlSurfaceAlpha, 0.0001f)
        assertEquals(0f, appearance.copy(controlTransparency = 1f).controlSurfaceAlpha, 0.0001f)
        assertTrue(appearance.glassNavigationEnabled)
        assertEquals(0.5f, appearance.glassNavigationTransparency, 0.0001f)
        assertEquals(0.5f, appearance.glassNavigationOpacity, 0.0001f)
        assertEquals(1f, appearance.copy(glassNavigationTransparency = -1f).glassNavigationOpacity, 0.0001f)
        assertEquals(0f, appearance.copy(glassNavigationTransparency = 1f).glassNavigationOpacity, 0.0001f)
        assertEquals(0f, appearance.copy(glassNavigationTransparency = 2f).glassNavigationOpacity, 0.0001f)
        assertEquals(0f, AppearanceTokens.pageSurfaceAlpha, 0.0001f)
        assertEquals(5, PaletteId.values().size)
    }

    @Test
    fun managerUpdateDetectionDefaultsToDisabled() {
        assertFalse(SettingsUiState().updateCheckEnabled)
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
