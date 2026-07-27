package com.linux.permissionmanager.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linux.permissionmanager.PermissionManagerApplication
import com.linux.permissionmanager.customizer.CustomBuildArtifact
import com.linux.permissionmanager.customizer.CustomBuildRequest
import com.linux.permissionmanager.customizer.CustomBuildStage
import com.linux.permissionmanager.customizer.PackageNameValidator
import com.linux.permissionmanager.data.UiEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalCustomizerUiState(
    val visible: Boolean = false,
    val packageName: String = "",
    val managerName: String = "",
    val defaultPackageName: String = "com.example.manager",
    val defaultManagerName: String = "SKRoot Pro",
    val iconUri: Uri? = null,
    val packageError: String? = null,
    val signatureConflict: String? = null,
    val checkingPackage: Boolean = false,
    val building: Boolean = false,
    val stage: CustomBuildStage = CustomBuildStage.IDLE,
    val error: String? = null,
    val artifact: CustomBuildArtifact? = null,
) {
    val effectivePackageName: String get() = packageName.trim().ifBlank { defaultPackageName }
    val effectiveManagerName: String get() = managerName.trim().ifBlank { defaultManagerName }
    val canBuild: Boolean get() =
        !building && !checkingPackage && packageError == null
    val canInstall: Boolean get() = canBuild && signatureConflict == null
}

class LocalCustomizerViewModel(private val app: PermissionManagerApplication) : ViewModel() {
    private val repository = app.container.customizer
    private val events = app.container.events
    private val defaultName = runCatching {
        app.packageManager.getApplicationLabel(app.applicationInfo).toString()
    }.getOrDefault("SKRoot Pro")
    private val defaultPackageName = "${app.packageName}.custom"
    private val mutableState = MutableStateFlow(
        LocalCustomizerUiState(
            defaultPackageName = defaultPackageName,
            defaultManagerName = defaultName,
        )
    )
    val state: StateFlow<LocalCustomizerUiState> = mutableState.asStateFlow()
    private var buildJob: Job? = null
    private var packageCheckJob: Job? = null

    fun show() {
        mutableState.update { it.copy(visible = true, error = null) }
        scheduleSignatureCheck(mutableState.value.packageName)
    }

    fun dismiss() {
        buildJob?.cancel()
        packageCheckJob?.cancel()
        mutableState.update {
            it.copy(
                visible = false,
                building = it.stage == CustomBuildStage.INSTALLING,
                stage = if (it.stage == CustomBuildStage.INSTALLING) it.stage else CustomBuildStage.IDLE,
            )
        }
    }

    fun setPackageName(value: String) {
        val normalized = value.trim()
        val syntaxError = if (normalized.isBlank()) null else PackageNameValidator.error(normalized)
        mutableState.update {
            it.copy(
                packageName = value,
                packageError = syntaxError,
                signatureConflict = null,
                checkingPackage = syntaxError == null,
                error = null,
                artifact = null,
            )
        }
        scheduleSignatureCheck(value)
    }

    fun setManagerName(value: String) = mutableState.update {
        it.copy(managerName = value.take(80), error = null, artifact = null)
    }

    fun requestIcon() = events.emit(UiEffect.PickCustomizerIcon)

    fun setIcon(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        mutableState.update { it.copy(iconUri = uri, error = null, artifact = null) }
    }

    fun useDefaultIcon() {
        mutableState.value.iconUri?.let { uri ->
            runCatching {
                app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        mutableState.update { it.copy(iconUri = null, error = null, artifact = null) }
    }

    fun buildAndExport() = build(install = false)
    fun buildAndInstall() = build(install = true)

    fun installResult(success: Boolean, message: String) {
        mutableState.update {
            it.copy(
                building = false,
                stage = if (success) CustomBuildStage.COMPLETE else CustomBuildStage.FAILED,
                error = if (success) null else message,
            )
        }
        events.emit(UiEffect.Snackbar(message))
    }

    fun exportResult(success: Boolean, message: String) {
        if (!success) {
            mutableState.update {
                it.copy(building = false, stage = CustomBuildStage.FAILED, error = message)
            }
        }
        events.emit(UiEffect.Snackbar(message))
    }

    private fun build(install: Boolean) {
        if (install && !mutableState.value.canInstall) return
        if (!install && !mutableState.value.canBuild) return
        buildJob?.cancel()
        buildJob = viewModelScope.launch {
            val input = mutableState.value
            val packageName = input.effectivePackageName
            val managerName = input.effectiveManagerName
            mutableState.update { it.copy(building = true, stage = CustomBuildStage.PREPARING, error = null, artifact = null) }
            try {
                if (install && repository.installedSignatureConflict(packageName)) {
                    error("设备中已安装同包名应用，但签名与本地定制身份不同；请更换包名或先卸载原应用")
                }
                val artifact = repository.build(
                    CustomBuildRequest(packageName, managerName, input.iconUri),
                ) { stage -> mutableState.update { it.copy(stage = stage) } }
                if (install) {
                    mutableState.update { it.copy(building = true, stage = CustomBuildStage.INSTALLING, artifact = artifact) }
                    events.emit(UiEffect.InstallCustomizedApk(artifact.file, artifact.packageName))
                } else {
                    mutableState.update { it.copy(building = false, stage = CustomBuildStage.COMPLETE, artifact = artifact) }
                    events.emit(UiEffect.ExportCustomizedApk(artifact.file, "${artifact.packageName}.apk"))
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(building = false, stage = CustomBuildStage.IDLE) }
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        building = false,
                        stage = CustomBuildStage.FAILED,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private fun scheduleSignatureCheck(packageInput: String) {
        packageCheckJob?.cancel()
        val packageName = packageInput.trim().ifBlank { defaultPackageName }
        if (PackageNameValidator.error(packageName) != null || !mutableState.value.visible) {
            mutableState.update { it.copy(checkingPackage = false, signatureConflict = null) }
            return
        }
        mutableState.update { current ->
            if (current.effectivePackageName == packageName) {
                current.copy(checkingPackage = true, signatureConflict = null)
            } else current
        }
        packageCheckJob = viewModelScope.launch {
            delay(250)
            val conflict = try {
                repository.installedSignatureConflict(packageName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            mutableState.update { current ->
                if (current.effectivePackageName != packageName) current else current.copy(
                    checkingPackage = false,
                    signatureConflict = if (conflict) {
                        "设备中已安装同包名应用，但签名不同；仍可导出 APK，安装前需更换包名或卸载原应用"
                    } else null,
                )
            }
        }
    }
}
