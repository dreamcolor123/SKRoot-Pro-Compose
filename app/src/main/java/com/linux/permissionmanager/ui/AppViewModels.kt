package com.linux.permissionmanager.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linux.permissionmanager.AppSettings
import com.linux.permissionmanager.PermissionManagerApplication
import com.linux.permissionmanager.data.*
import com.linux.permissionmanager.helper.MagicaRootHelper
import com.linux.permissionmanager.utils.FileUtils
import com.linux.permissionmanager.utils.NetUtils
import com.linux.permissionmanager.utils.ShellUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

data class RootConfigUiState(
    val visible: Boolean = true,
    val rootKey: String = "",
    val hotload: Boolean = false,
    val hotloadCommand: String = "",
    val method: String = "SHELL",
    val busy: Boolean = false,
)

data class MainUiState(
    val selectedPage: Int = 0,
    val activeRootKey: String = "",
    val rootConfig: RootConfigUiState = RootConfigUiState(),
)

class MainViewModel(private val app: PermissionManagerApplication) : ViewModel() {
    private val container = app.container
    private val settings = container.settings
    private val native = container.native
    private val events = container.events

    private val mutableState = MutableStateFlow(
        MainUiState(
            activeRootKey = settings.rootKey,
            rootConfig = RootConfigUiState(
                visible = true,
                rootKey = settings.rootKey,
                hotload = settings.hotload,
                hotloadCommand = settings.hotloadCommand,
                method = settings.hotloadMethod,
            )
        )
    )
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()
    val rootKey: String get() = mutableState.value.activeRootKey

    fun selectPage(index: Int) = mutableState.update { it.copy(selectedPage = index.coerceIn(0, 3)) }
    fun showRootConfig() = mutableState.update { it.copy(rootConfig = it.rootConfig.copy(visible = true)) }
    fun dismissRootConfig() = mutableState.update { it.copy(rootConfig = it.rootConfig.copy(visible = false)) }
    fun updateRootKey(value: String) = mutableState.update { it.copy(rootConfig = it.rootConfig.copy(rootKey = value)) }
    fun updateMode(hotload: Boolean) = mutableState.update { it.copy(rootConfig = it.rootConfig.copy(hotload = hotload)) }

    fun importHotloadFile() = viewModelScope.launch {
        mutableState.update { it.copy(rootConfig = it.rootConfig.copy(busy = true)) }
        val script = withContext(Dispatchers.IO) { FileUtils.readTextFile(AppSettings.HOTLOAD_SHELL_PATH) }
        if (script.isNullOrBlank()) {
            events.emit(UiEffect.Snackbar("读取 1.h 失败或文件不存在"))
        } else {
            val key = extractConfig(script, "ROOT_KEY")
            val method = extractConfig(script, "METHOD").ifBlank { "SHELL" }
            if (key.isBlank()) {
                events.emit(UiEffect.Snackbar("未在 1.h 中找到 ROOT_KEY"))
            } else {
                mutableState.update {
                    it.copy(rootConfig = it.rootConfig.copy(
                        rootKey = key,
                        hotload = true,
                        hotloadCommand = script,
                        method = method,
                    ))
                }
                settings.rootKey = key
                settings.hotloadCommand = script
                settings.hotloadMethod = method
                events.emit(UiEffect.Snackbar("已导入热启动配置"))
            }
        }
        mutableState.update { it.copy(rootConfig = it.rootConfig.copy(busy = false)) }
    }

    fun exportHotloadFile() = viewModelScope.launch {
        val command = mutableState.value.rootConfig.hotloadCommand
        if (command.isBlank()) {
            events.emit(UiEffect.Snackbar("当前没有可导出的热启动脚本"))
            return@launch
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(AppSettings.HOTLOAD_SHELL_PATH)
                file.parentFile?.mkdirs()
                file.writeText(command)
                file
            }
        }
        result.onSuccess { events.emit(UiEffect.Snackbar("已导出至 ${it.absolutePath}")) }
            .onFailure { events.emit(UiEffect.Snackbar("导出失败：${it.message ?: "unknown"}")) }
    }

    fun saveRootConfig() {
        val config = mutableState.value.rootConfig
        val key = config.rootKey.trim()
        if (key.isBlank()) {
            events.emit(UiEffect.Snackbar("请输入 Root Key"))
            return
        }
        settings.rootKey = key
        settings.hotload = config.hotload
        settings.hotloadCommand = config.hotloadCommand
        settings.hotloadMethod = config.method
        mutableState.update {
            it.copy(
                selectedPage = 0,
                activeRootKey = key,
                rootConfig = config.copy(rootKey = key, visible = false),
            )
        }
        if (config.hotload && config.hotloadCommand.isNotBlank()) executeHotload(config.copy(rootKey = key))
    }

    private fun executeHotload(config: RootConfigUiState) = viewModelScope.launch {
        mutableState.update { it.copy(rootConfig = it.rootConfig.copy(busy = true)) }
        val channelOk = runCatching { native.testBasics(config.rootKey, "Channel").contains("OK") }.getOrDefault(false)
        if (channelOk) {
            oneplusStage1(config.rootKey, true)
            mutableState.update { it.copy(rootConfig = it.rootConfig.copy(busy = false)) }
            return@launch
        }
        events.emit(UiEffect.Snackbar("正在加载热启动补丁…"))
        val result = if (config.method.equals("MAGICA", true)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                "ERROR: MAGICA 模式需要 Android 10 或更高版本"
            } else executeMagica(app, config.hotloadCommand)
        } else {
            withContext(Dispatchers.IO) { ShellUtils.executeScript(app, config.hotloadCommand) }
        }
        val bypass = oneplusStage1(config.rootKey, false)
        delay(3_000)
        val ready = runCatching { native.testBasics(config.rootKey, "Channel").contains("OK") }.getOrDefault(false)
        mutableState.update { it.copy(rootConfig = it.rootConfig.copy(busy = false)) }
        if (ready) events.emit(UiEffect.Snackbar("热启动加载完成"))
        else events.emit(UiEffect.ShowLog("热启动日志", bypass + result))
    }

    private suspend fun oneplusStage1(key: String, showLog: Boolean): String {
        val result = runCatching {
            val status = JSONObject(native.systemStatusJson())
            if (status.optInt("selinux", -1) == 0) native.oneplusStage1(key) else ""
        }.getOrDefault("")
        if (showLog && result.isNotBlank()) {
            events.emit(UiEffect.ShowLog("一加/Oppo 内部接口拦截日志", result))
        }
        return if (result.isBlank()) "" else "$result\n"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun executeMagica(context: Context, script: String): String = suspendCancellableCoroutine { continuation ->
        MagicaRootHelper.executeMagicaRootScript(context, script) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }

    private fun extractConfig(text: String, key: String): String {
        val pattern = Regex("(?m)^\\s*#.*?\\b${Regex.escape(key)}\\s*=\\s*(\\S+)")
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }
}

data class HomeUiState(
    val loading: Boolean = true,
    val environment: EnvironmentInfo = EnvironmentInfo(),
    val system: SystemStatus = SystemStatus(),
    val console: String = "",
    val busyAction: String? = null,
    val error: String? = null,
)

class HomeViewModel(private val app: PermissionManagerApplication) : ViewModel() {
    private val native = app.container.native
    private val settings = app.container.settings
    private val events = app.container.events
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()
    private var key = ""
    private var refreshJob: Job? = null
    private var refreshRequestId = 0L

    fun setRootKey(value: String) {
        if (value == key && !mutableState.value.loading) return
        key = value
        refresh()
    }

    fun refresh() {
        val requestKey = key
        val requestId = ++refreshRequestId
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            runCatching {
                val raw = native.environmentState(requestKey)
                val version = native.installedVersion(requestKey)
                val sdk = native.sdkVersion()
                val systemRaw = native.systemStatusJson()
                val baseSystem = JsonParsers.system(systemRaw)
                val oplus = if (baseSystem.selinux != 0) native.oneplusNormal(requestKey) else false
                EnvironmentInfo(JsonParsers.environment(raw), raw, version, sdk, settings.hotload) to
                    baseSystem.copy(oplusIntercepted = oplus)
            }.onSuccess { (environment, system) ->
                if (requestId != refreshRequestId || requestKey != key) return@onSuccess
                mutableState.update { it.copy(loading = false, environment = environment, system = system) }
            }.onFailure { error ->
                if (error is CancellationException || requestId != refreshRequestId || requestKey != key) return@onFailure
                mutableState.update { it.copy(loading = false, error = error.message ?: "状态读取失败") }
            }
        }
    }

    fun install() = operation("安装环境") {
        val result = native.installEnvironment(key, settings.hotload)
        append(result)
        if (settings.hotload && result.contains("OK")) {
            native.runCommand(key, "rm -f ${AppSettings.HOTLOAD_SHELL_PATH}")
        }
        refresh()
    }

    fun uninstall() = operation("卸载环境") {
        append(native.uninstallEnvironment(key))
        refresh()
    }

    fun testRoot() = operation("测试 Root") { append(native.testRoot(key)) }

    fun runCommand(command: String) {
        val value = command.trim()
        if (value.isBlank()) return
        settings.lastRootCommand = value
        operation("执行命令") { append("$value\n${native.runCommand(key, value)}") }
    }

    fun clearConsole() = mutableState.update { it.copy(console = "") }
    fun copyConsole() = events.emit(UiEffect.CopyText(mutableState.value.console))

    private fun append(message: String) = mutableState.update {
        it.copy(console = listOf(it.console.trimEnd(), message.trimEnd()).filter(String::isNotBlank).joinToString("\n"))
    }

    private fun operation(name: String, block: suspend () -> Unit) = viewModelScope.launch {
        mutableState.update { it.copy(busyAction = name) }
        runCatching { block() }.onFailure {
            append("ERROR: ${it.message ?: name}")
            events.emit(UiEffect.Snackbar("$name 失败"))
        }
        mutableState.update { it.copy(busyAction = null) }
    }
}

data class SuperUserUiState(
    val loading: Boolean = true,
    val grants: List<SuGrant> = emptyList(),
    val apps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val pickerQuery: String = "",
    val showSystemApps: Boolean = false,
    val showThirdPartyApps: Boolean = true,
    val pickerVisible: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val filteredGrants: List<SuGrant> get() {
        val q = query.trim().lowercase(Locale.ROOT)
        return if (q.isBlank()) grants else grants.filter {
            it.label.lowercase(Locale.ROOT).contains(q) || it.packageName.lowercase(Locale.ROOT).contains(q)
        }
    }
    val filteredApps: List<InstalledApp> get() {
        val q = pickerQuery.trim().lowercase(Locale.ROOT)
        return apps.filter { app ->
            ((app.system && showSystemApps) || (!app.system && showThirdPartyApps)) &&
                (q.isBlank() || app.label.lowercase(Locale.ROOT).contains(q) || app.packageName.lowercase(Locale.ROOT).contains(q))
        }
    }
}

class SuperUserViewModel(private val app: PermissionManagerApplication) : ViewModel() {
    private val native = app.container.native
    private val packages = app.container.packages
    private val events = app.container.events
    private val mutableState = MutableStateFlow(SuperUserUiState())
    val state: StateFlow<SuperUserUiState> = mutableState.asStateFlow()
    private var key = ""
    private var refreshJob: Job? = null
    private var refreshRequestId = 0L

    fun setRootKey(value: String) { if (value != key || mutableState.value.loading) { key = value; refresh() } }
    fun setQuery(value: String) = mutableState.update { it.copy(query = value) }
    fun setPickerQuery(value: String) = mutableState.update { it.copy(pickerQuery = value) }
    fun setFilters(system: Boolean? = null, thirdParty: Boolean? = null) = mutableState.update {
        it.copy(showSystemApps = system ?: it.showSystemApps, showThirdPartyApps = thirdParty ?: it.showThirdPartyApps)
    }
    fun showPicker() = mutableState.update { it.copy(pickerVisible = true) }
    fun hidePicker() = mutableState.update { it.copy(pickerVisible = false) }

    fun refresh() {
        val requestKey = key
        val requestId = ++refreshRequestId
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            runCatching {
                val apps = packages.installedApps()
                val grants = JsonParsers.su(native.suList(requestKey), apps.associateBy { it.packageName })
                apps to grants
            }.onSuccess { (apps, grants) ->
                if (requestId != refreshRequestId || requestKey != key) return@onSuccess
                mutableState.update { it.copy(loading = false, apps = apps, grants = grants) }
            }.onFailure { error ->
                if (error is CancellationException || requestId != refreshRequestId || requestKey != key) return@onFailure
                mutableState.update { it.copy(loading = false, error = error.message ?: "授权列表读取失败") }
            }
        }
    }

    fun add(appEntry: InstalledApp) = mutate { native.addSu(key, appEntry.packageName) }
    fun addAdb() = mutate { native.addSu(key, "com.android.shell") }
    fun remove(grant: SuGrant) = mutate { native.removeSu(key, grant.packageName) }
    fun clear() = mutate { native.clearSu(key) }

    private fun mutate(block: suspend () -> String) = viewModelScope.launch {
        mutableState.update { it.copy(busy = true, pickerVisible = false) }
        runCatching { block() }.onSuccess { events.emit(UiEffect.Snackbar(it)) }
            .onFailure { events.emit(UiEffect.Snackbar(it.message ?: "操作失败")) }
        mutableState.update { it.copy(busy = false) }
        refresh()
    }
}

data class ModuleUiState(
    val selectedTab: Int = 0,
    val installedLoading: Boolean = true,
    val marketLoading: Boolean = true,
    val installed: List<InstalledModule> = emptyList(),
    val market: List<MarketModule> = emptyList(),
    val marketQuery: String = "",
    val installedError: String? = null,
    val marketError: String? = null,
    val busy: Boolean = false,
    val download: DownloadProgress? = null,
) {
    val filteredMarket: List<MarketModule> get() {
        val q = marketQuery.trim().lowercase(Locale.ROOT)
        return if (q.isBlank()) market else market.filter { module ->
            listOf(module.chineseName, module.englishName, module.author, module.description, module.id, module.version)
                .any { it.lowercase(Locale.ROOT).contains(q) }
        }
    }
}

class ModuleViewModel(private val app: PermissionManagerApplication) : ViewModel() {
    private val repository = app.container.modules
    private val network = app.container.network
    private val events = app.container.events
    private val mutableState = MutableStateFlow(ModuleUiState())
    val state: StateFlow<ModuleUiState> = mutableState.asStateFlow()
    private var key = ""
    private var downloadHandle: NetUtils.DownloadHandle? = null
    private var installedRefreshJob: Job? = null
    private var installedRefreshRequestId = 0L
    @Volatile private var downloadCancelled = false

    fun setRootKey(value: String) {
        if (value == key && !mutableState.value.installedLoading) return
        key = value
        refreshInstalled()
        if (mutableState.value.market.isEmpty()) refreshMarket()
    }
    fun selectTab(tab: Int) = mutableState.update { it.copy(selectedTab = tab.coerceIn(0, 1)) }
    fun setMarketQuery(value: String) = mutableState.update { it.copy(marketQuery = value) }
    fun requestPick(runOnce: Boolean) = events.emit(UiEffect.PickModule(runOnce))

    fun refreshInstalled() {
        val requestKey = key
        val requestId = ++installedRefreshRequestId
        installedRefreshJob?.cancel()
        installedRefreshJob = viewModelScope.launch {
            mutableState.update { it.copy(installedLoading = true, installedError = null) }
            runCatching { repository.installed(requestKey).map { it.copy(update = repository.cachedUpdate(it)) } }
            .onSuccess { modules ->
                if (requestId != installedRefreshRequestId || requestKey != key) return@onSuccess
                mutableState.update { it.copy(installedLoading = false, installed = modules) }
                modules.filter { it.updateJson.isNotBlank() }.forEach { checkUpdate(it, silent = true) }
            }
            .onFailure { error ->
                if (error is CancellationException || requestId != installedRefreshRequestId || requestKey != key) return@onFailure
                mutableState.update { state -> state.copy(installedLoading = false, installedError = error.message ?: "模块列表读取失败") }
            }
        }
    }

    fun refreshMarket() = viewModelScope.launch {
        mutableState.update { it.copy(marketLoading = true, marketError = null) }
        runCatching { repository.market() }
            .onSuccess { mutableState.update { state -> state.copy(marketLoading = false, market = it) } }
            .onFailure { mutableState.update { state -> state.copy(marketLoading = false, marketError = it.message ?: "模块市场加载失败") } }
    }

    fun installUri(uri: Uri, runOnce: Boolean) = viewModelScope.launch {
        mutableState.update { it.copy(busy = true) }
        runCatching { repository.install(key, repository.copyUriToCache(uri), runOnce) }
            .onSuccess { events.emit(UiEffect.Snackbar(it)) }
            .onFailure { events.emit(UiEffect.Snackbar(it.message ?: "模块安装失败")) }
        mutableState.update { it.copy(busy = false) }
        refreshInstalled()
    }

    fun remove(module: InstalledModule) = mutate({ repository.remove(key, module) }) { refreshInstalled() }
    fun openWebUi(module: InstalledModule) = mutate({ repository.openWebUi(key, module) })

    fun showDetails(module: InstalledModule) {
        val text = buildString {
            appendLine("名称：${module.name}")
            appendLine("版本：${module.version}")
            appendLine("作者：${module.author}")
            appendLine("ID：${module.id}")
            appendLine("最低 SDK：${module.minSdk}")
            appendLine("状态：${module.runState}")
            appendLine()
            append(module.description)
        }
        events.emit(UiEffect.ShowLog("模块详情", text))
    }

    fun checkUpdate(module: InstalledModule, silent: Boolean = false) = viewModelScope.launch {
        runCatching { repository.checkUpdate(module) }
            .onSuccess { update ->
                mutableState.update { state ->
                    state.copy(installed = state.installed.map { if (it.id == module.id) it.copy(update = update) else it })
                }
                if (!silent && (update == null || !update.hasNewVersion)) events.emit(UiEffect.Snackbar("当前已是最新版本"))
            }
            .onFailure { if (!silent) events.emit(UiEffect.Snackbar("检查更新失败：${it.message}")) }
    }

    fun showChangelog(module: InstalledModule) = viewModelScope.launch {
        val update = module.update ?: return@launch
        runCatching { repository.changelog(update) }
            .onSuccess { events.emit(UiEffect.ShowLog("更新日志", it)) }
            .onFailure { events.emit(UiEffect.Snackbar("更新日志下载失败：${it.message}")) }
    }

    fun downloadUpdate(module: InstalledModule) {
        val update = module.update ?: return
        startDownload(module.id, update.latestVersion, update.downloadUrl) { file ->
            viewModelScope.launch {
                runCatching { repository.install(key, file.absolutePath, false) }
                    .onSuccess { events.emit(UiEffect.Snackbar(it)) }
                    .onFailure { events.emit(UiEffect.Snackbar(it.message ?: "更新安装失败")) }
                repository.delete(file)
                mutableState.update { it.copy(download = null) }
                refreshInstalled()
            }
        }
    }

    fun downloadMarket(module: MarketModule) {
        startDownload(module.id, module.version, module.downloadUrl) { file ->
            viewModelScope.launch {
                runCatching { repository.install(key, file.absolutePath, false) }
                    .onSuccess { events.emit(UiEffect.Snackbar(it)) }
                    .onFailure { events.emit(UiEffect.Snackbar(it.message ?: "模块安装失败")) }
                repository.delete(file)
                mutableState.update { it.copy(download = null) }
                refreshInstalled()
            }
        }
    }

    fun cancelDownload() {
        downloadCancelled = true
        downloadHandle?.cancel()
        downloadHandle = null
        mutableState.update { it.copy(download = null) }
        events.emit(UiEffect.Snackbar("已取消下载"))
    }

    private fun startDownload(id: String, version: String, url: String, complete: (File) -> Unit) {
        if (url.isBlank()) { events.emit(UiEffect.Snackbar("未提供下载地址")); return }
        val destination = repository.cacheFile(repository.downloadFileName(id, version))
        repository.delete(destination)
        downloadCancelled = false
        mutableState.update { it.copy(download = DownloadProgress("正在下载 $id")) }
        downloadHandle = network.download(
            url,
            destination,
            onStart = { total -> mutableState.update { it.copy(download = it.download?.copy(totalBytes = total)) } },
            onProgress = { current, total -> mutableState.update { it.copy(download = it.download?.copy(downloadedBytes = current, totalBytes = total)) } },
            onComplete = { result ->
                downloadHandle = null
                result.onSuccess(complete).onFailure { error ->
                    mutableState.update { it.copy(download = null) }
                    if (!downloadCancelled) events.emit(UiEffect.Snackbar("下载失败：${error.message ?: "unknown"}"))
                }
            },
        )
    }

    private fun mutate(block: suspend () -> String, after: (() -> Unit)? = null) = viewModelScope.launch {
        mutableState.update { it.copy(busy = true) }
        runCatching { block() }.onSuccess { events.emit(UiEffect.Snackbar(it)) }
            .onFailure { events.emit(UiEffect.Snackbar(it.message ?: "操作失败")) }
        mutableState.update { it.copy(busy = false) }
        after?.invoke()
    }
}

data class SettingsUiState(
    val loading: Boolean = true,
    val bootFailProtect: Boolean = false,
    val adbForcedDisabled: Boolean = false,
    val logEnabled: Boolean = false,
    val sdkVersion: String = "-",
    val update: AppUpdate? = null,
    val busyItem: String? = null,
    val error: String? = null,
)

class SettingsViewModel(private val app: PermissionManagerApplication) : ViewModel() {
    private val native = app.container.native
    private val updates = app.container.updates
    private val events = app.container.events
    private val mutableState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()
    private var key = ""
    private var refreshJob: Job? = null
    private var refreshRequestId = 0L

    fun setRootKey(value: String) { if (value != key || mutableState.value.loading) { key = value; refresh() } }

    fun refresh() {
        val requestKey = key
        val requestId = ++refreshRequestId
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            runCatching {
                SettingsUiState(
                    loading = false,
                    bootFailProtect = native.bootFailEnabled(requestKey),
                    adbForcedDisabled = native.adbForcedDisabled(requestKey),
                    logEnabled = native.logEnabled(requestKey),
                    sdkVersion = native.sdkVersion(),
                    update = updates.cached(),
                )
            }.onSuccess { state ->
                if (requestId != refreshRequestId || requestKey != key) return@onSuccess
                mutableState.value = state
            }.onFailure { error ->
                if (error is CancellationException || requestId != refreshRequestId || requestKey != key) return@onFailure
                mutableState.update { state -> state.copy(loading = false, error = error.message ?: "设置读取失败") }
            }
            if (requestId == refreshRequestId && requestKey == key) {
                runCatching { updates.refresh() }.onSuccess { update ->
                    if (requestId == refreshRequestId && requestKey == key) {
                        mutableState.update { it.copy(update = update) }
                    }
                }
            }
        }
    }

    fun setBootFail(enabled: Boolean) = setting(
        id = "boot",
        call = { native.setBootFailEnabled(key, enabled) },
        updateState = { mutableState.update { it.copy(bootFailProtect = enabled) } },
    )
    fun setAdbDisabled(enabled: Boolean) = setting(
        id = "adb",
        call = { native.setAdbForcedDisabled(key, enabled) },
        updateState = { mutableState.update { it.copy(adbForcedDisabled = enabled) } },
    )
    fun setLogEnabled(enabled: Boolean) = setting(
        id = "log",
        call = { native.setLogEnabled(key, enabled) },
        updateState = { mutableState.update { it.copy(logEnabled = enabled) } },
    )

    fun testBasic(item: String) = viewModelScope.launch {
        val result = runCatching { native.testBasics(key, item) }.getOrElse { "ERROR: ${it.message}" }
        events.emit(UiEffect.ShowLog("单项检查", decorateDiagnostic(result)))
    }
    fun testDefaultModule(item: String) = viewModelScope.launch {
        val result = runCatching { native.testDefaultModule(key, item) }.getOrElse { "ERROR: ${it.message}" }
        events.emit(UiEffect.ShowLog("核心模块检查", decorateDiagnostic(result)))
    }
    fun showLog() = viewModelScope.launch {
        val result = runCatching { native.readLog(key) }.getOrElse { "ERROR: ${it.message}" }
        events.emit(UiEffect.ShowLog("SKRoot 日志", result))
    }
    fun clearLog() = setting(id = "clearLog", call = { native.clearLog(key) })
    fun reboot(command: String?, soft: Boolean) = setting(
        id = "reboot",
        call = { if (soft) native.softReboot(key) else native.runCommand(key, command.orEmpty()) },
    )
    fun openUrl(url: String) = events.emit(UiEffect.OpenUrl(url))
    fun showAppChangelog() = viewModelScope.launch {
        val update = mutableState.value.update ?: return@launch
        val text = runCatching { updates.changelog(update) }.getOrElse { "ERROR: ${it.message}" }
        events.emit(UiEffect.ShowLog("应用更新日志", text))
    }

    private fun setting(id: String, call: suspend () -> String, updateState: (() -> Unit)? = null) = viewModelScope.launch {
        mutableState.update { it.copy(busyItem = id) }
        runCatching { call() }.onSuccess {
            updateState?.invoke()
            events.emit(UiEffect.Snackbar(it))
        }.onFailure { events.emit(UiEffect.Snackbar(it.message ?: "操作失败")) }
        mutableState.update { it.copy(busyItem = null) }
    }

    private fun decorateDiagnostic(result: String): String = if (result.contains("ERR_MODULE_MUST_UNINSTALL")) {
        "$result\n请先卸载 SKRoot 环境，再重试。"
    } else result
}

class AppViewModelFactory(private val app: PermissionManagerApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(app)
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(app)
        modelClass.isAssignableFrom(SuperUserViewModel::class.java) -> SuperUserViewModel(app)
        modelClass.isAssignableFrom(ModuleViewModel::class.java) -> ModuleViewModel(app)
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(app)
        else -> error("Unknown ViewModel: ${modelClass.name}")
    } as T
}
