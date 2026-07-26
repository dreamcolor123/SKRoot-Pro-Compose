package com.linux.permissionmanager

import android.app.Application
import com.linux.permissionmanager.data.AppContainer

class PermissionManagerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        container = AppContainer(this)
    }
}
