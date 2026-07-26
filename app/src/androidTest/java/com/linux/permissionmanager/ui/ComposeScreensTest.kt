package com.linux.permissionmanager.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linux.permissionmanager.data.EnvironmentInfo
import com.linux.permissionmanager.data.EnvironmentState
import com.linux.permissionmanager.data.SystemStatus
import com.linux.permissionmanager.ui.screens.HomeScreen
import com.linux.permissionmanager.ui.screens.ModuleScreen
import com.linux.permissionmanager.ui.screens.RootConfigDialog
import com.linux.permissionmanager.ui.screens.SuperUserScreen
import com.linux.permissionmanager.ui.theme.SkpTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeShowsEnvironmentStatusAndConsole() {
        compose.setContent {
            SkpTheme {
                HomeScreen(
                    state = HomeUiState(
                        loading = false,
                        environment = EnvironmentInfo(
                            state = EnvironmentState.RUNNING,
                            installedVersion = "4.5.3",
                            sdkVersion = "35",
                        ),
                        system = SystemStatus(selinux = 0, seccomp = 2, adbEnabled = false),
                        console = "uid=0(root)",
                    ),
                    bottomPadding = PaddingValues(0.dp),
                    onConfigureRoot = {},
                    onRefresh = {},
                    onInstall = {},
                    onUninstall = {},
                    onTestRoot = {},
                    onRunCommand = {},
                    onCopyConsole = {},
                    onClearConsole = {},
                    onReboot = { _, _ -> },
                )
            }
        }

        compose.onNodeWithText("正常运行").assertExists()
        compose.onNodeWithText("核心 4.5.3").assertExists()
    }

    @Test
    fun rootDialogUpdatesModeKeyAndConfirmAction() {
        var state by mutableStateOf(RootConfigUiState(visible = true))
        var confirmed = false
        compose.setContent {
            SkpTheme {
                RootConfigDialog(
                    state = state,
                    onDismiss = {},
                    onRootKeyChange = { state = state.copy(rootKey = it) },
                    onModeChange = { state = state.copy(hotload = it) },
                    onImport = {},
                    onExport = {},
                    onConfirm = { confirmed = true },
                )
            }
        }

        compose.onNodeWithText("热启动").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("test-key")
        compose.onNodeWithText("从 1.h 导入").assertExists()
        compose.onNodeWithText("确定").performClick()

        compose.runOnIdle {
            assertTrue(state.hotload)
            assertEquals("test-key", state.rootKey)
            assertTrue(confirmed)
        }
    }

    @Test
    fun authorizationEmptyStateCanOpenAppPicker() {
        var pickerVisible = false
        compose.setContent {
            SkpTheme {
                SuperUserScreen(
                    state = SuperUserUiState(loading = false, pickerVisible = pickerVisible),
                    bottomPadding = PaddingValues(0.dp),
                    onRefresh = {},
                    onSearch = {},
                    onShowPicker = { pickerVisible = true },
                    onHidePicker = { pickerVisible = false },
                    onPickerSearch = {},
                    onFilterSystem = {},
                    onFilterThirdParty = {},
                    onAdd = {},
                    onAddAdb = {},
                    onRemove = {},
                    onClear = {},
                )
            }
        }

        compose.onNodeWithText("暂无 SU 授权").assertExists()
        compose.onNodeWithContentDescription("添加").performClick()
        compose.onNodeWithText("添加 SU 授权").performClick()
        compose.runOnIdle { assertTrue(pickerVisible) }
    }

    @Test
    fun moduleTabsSwitchBetweenInstalledAndMarketEmptyStates() {
        var selectedTab by mutableStateOf(0)
        compose.setContent {
            SkpTheme {
                ModuleScreen(
                    state = ModuleUiState(
                        selectedTab = selectedTab,
                        installedLoading = false,
                        marketLoading = false,
                    ),
                    bottomPadding = PaddingValues(0.dp),
                    onSelectTab = { selectedTab = it },
                    onRefreshInstalled = {},
                    onRefreshMarket = {},
                    onPickModule = {},
                    onOpenGuide = {},
                    onMarketQuery = {},
                    onRemove = {},
                    onDetails = {},
                    onWebUi = {},
                    onCheckUpdate = {},
                    onChangelog = {},
                    onDownloadUpdate = {},
                    onDownloadMarket = {},
                    onOpenUrl = {},
                    onCancelDownload = {},
                )
            }
        }

        compose.onNodeWithText("暂无已安装模块").assertExists()
        compose.onNodeWithText("模块市场").performClick()
        compose.onNodeWithText("没有匹配的模块").assertExists()
        compose.runOnIdle {
            assertEquals(1, selectedTab)
            assertFalse(selectedTab == 0)
        }
    }
}
