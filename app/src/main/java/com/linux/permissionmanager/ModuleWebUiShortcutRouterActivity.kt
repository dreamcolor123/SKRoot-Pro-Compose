package com.linux.permissionmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.linux.permissionmanager.utils.ModuleWebUiShortcut
import com.linux.permissionmanager.utils.looksLikeRootKeyFailure
import com.linux.permissionmanager.utils.moduleWebUiUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Minimal shortcut entry point. A configured key is handled without creating the full manager
 * task; only missing/invalid key flows are forwarded to MainActivity for user configuration.
 */
class ModuleWebUiShortcutRouterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finishAndRemoveTask()
            return
        }
        val opaqueId = ModuleWebUiShortcut.opaqueIdFrom(intent)
        val moduleId = opaqueId?.let(ModuleWebUiShortcut::resolveModuleId)
        if (opaqueId == null || moduleId == null) {
            toast("快捷方式已失效，请从模块菜单重新创建")
            finishAndRemoveTask()
            return
        }

        val application = application as PermissionManagerApplication
        val rootKey = application.container.settings.rootKey
        if (rootKey.isBlank()) {
            openManagerForConfiguration(opaqueId)
            return
        }

        lifecycleScope.launch {
            runCatching { application.container.modules.openWebUi(rootKey, moduleId) }
                .onSuccess { result ->
                    val url = moduleWebUiUrl(result)
                    when {
                        looksLikeRootKeyFailure(result) -> openManagerForConfiguration(opaqueId)
                        url != null -> {
                            // The Native endpoint starts the local server asynchronously.
                            // Give it a brief head start before the browser performs its first request.
                            delay(180)
                            if (openBrowser(url)) {
                                // The browser must remain outside this short-lived, excluded task.
                                // Removing the whole router task here can hide/destroy a browser
                                // activity before a WebUI has completed its first request.
                                finish()
                            } else {
                                finishAndRemoveTask()
                            }
                        }
                        else -> {
                            toast(result.ifBlank { "打开模块 WebUI 失败" })
                            finishAndRemoveTask()
                        }
                    }
                }
                .onFailure { error ->
                    if (looksLikeRootKeyFailure(error.message)) {
                        openManagerForConfiguration(opaqueId)
                    } else {
                        toast(error.message ?: "打开模块 WebUI 失败")
                        finishAndRemoveTask()
                    }
                }
        }
    }

    private fun openManagerForConfiguration(opaqueId: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = ModuleWebUiShortcut.ACTION_OPEN
                putExtra(ModuleWebUiShortcut.EXTRA_OPAQUE_ID, opaqueId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
        )
        finishAndRemoveTask()
    }

    private fun openBrowser(url: String): Boolean = runCatching {
        // Starting from the application context plus NEW_TASK keeps the browser in
        // its own task. The router can then finish without removing the new page.
        applicationContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    }.getOrElse {
        toast("未找到可打开 WebUI 的浏览器")
        false
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
