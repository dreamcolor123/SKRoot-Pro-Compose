package com.linux.permissionmanager

import android.app.Application
import android.os.Build
import android.os.Process
import com.linux.permissionmanager.data.AppContainer

class PermissionManagerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // MagicaService runs in an isolated app-zygote child. Keep that child
        // as close as possible to the upstream service process: initializing
        // preferences, repositories and coroutine infrastructure there can
        // interfere with the early setuid transition used by Magica.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Process.isIsolated()) return
        AppSettings.init(this)
        container = AppContainer(this)
    }
}
