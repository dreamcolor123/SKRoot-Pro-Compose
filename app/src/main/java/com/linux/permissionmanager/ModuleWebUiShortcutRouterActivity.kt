package com.linux.permissionmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.linux.permissionmanager.utils.ModuleWebUiShortcut
import com.linux.permissionmanager.utils.looksLikeRootKeyFailure
import com.linux.permissionmanager.utils.moduleWebUiPort
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal shortcut entry point. A configured key is handled without creating the full manager
 * task; only missing/invalid key flows are forwarded to MainActivity for user configuration.
 */
class ModuleWebUiShortcutRouterActivity : ComponentActivity() {
    private var resumed = false
    private var webUiStarted = false
    private var browserWasForegrounded = false

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
                    val port = moduleWebUiPort(result)
                    when {
                        looksLikeRootKeyFailure(result) -> openManagerForConfiguration(opaqueId)
                        port != null -> keepAliveUntilWebUiStops(port)
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

    override fun onResume() {
        super.onResume()
        resumed = true
        if (webUiStarted && browserWasForegrounded) {
            finishAndRemoveTask()
        }
    }

    override fun onPause() {
        if (webUiStarted) browserWasForegrounded = true
        resumed = false
        super.onPause()
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

    private fun keepAliveUntilWebUiStops(port: Int) {
        // The native WebUI loader opens the browser itself with `am start`. Opening the
        // returned URL again here creates a second tab; the terminal module treats the
        // first tab becoming hidden as an exit and shuts its server down.
        webUiStarted = true
        if (!resumed) browserWasForegrounded = true

        // Keep this excluded router activity in the stopped task while the browser uses
        // the WebUI. This mirrors MainActivity's lifetime and keeps the loader's parent
        // process alive, then removes the hidden task once the local server has stopped.
        lifecycleScope.launch {
            delay(2_000)
            var failures = 0
            while (isActive) {
                val reachable = withContext(Dispatchers.IO) { isLoopbackPortReachable(port) }
                failures = if (reachable) 0 else failures + 1
                if (failures >= 2) {
                    finishAndRemoveTask()
                    return@launch
                }
                delay(1_500)
            }
        }
    }

    private fun isLoopbackPortReachable(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 350)
        }
    }.isSuccess

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
