/*
 * Copyright 2026 kndnb666
 * SPDX-License-Identifier: Apache-2.0
 */

package com.fileswap.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 执行引擎：文件操作跑在哪个权限下 */
enum class EngineMode(val label: String) {
    AUTO("自动"),
    ROOT("Root"),
    SHIZUKU("Shizuku"),
    LOCAL("本地存储");
}

private object EngineStore {
    private const val PREFS = "app_prefs"
    private const val KEY = "engine_mode"
    private const val KEY_RENAME = "rename_import_to_target"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    fun load(context: Context): EngineMode {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return EngineMode.entries.firstOrNull { it.name == name } ?: EngineMode.AUTO
    }

    fun save(context: Context, mode: EngineMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }

    fun loadRenameToTarget(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RENAME, false)

    fun saveRenameToTarget(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RENAME, value).apply()
    }

    /** 动态取色（壁纸取色）开关；默认关闭以呈现品牌 M3 色板 */
    fun loadDynamicColor(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC_COLOR, false)

    fun saveDynamicColor(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
    }
}

// @Immutable：承诺实例构造后不变（所有更新都走 copy 产生新实例）——
// 让接收这些类型的 Composable 可被跳过重组。List 字段虽被推断为
// unstable，但本注解是对不变性的显式且合法的承诺。
@Immutable
data class UiState(
    val importedFileName: String? = null,
    val importedLocalPath: String? = null,
    val targetPath: String = "",
    val backupPath: String = "",
    val targetLastModified: Long? = null,
    val shizukuGranted: Boolean = false,
    /** Shizuku 服务端 uid：0 = root，2000 = shell（ADB），-1 = 未知 */
    val shizukuUid: Int = -1,
    /** Shizuku 服务端版本（13.6.x 有 Android/data 可见性回归，诊断 #1278 用） */
    val shizukuServerVersion: Int = -1,
    /** Shizuku 应用发行版本名（如 13.6.0.r1086）；协议号看不出真实版本，诊断需要它 */
    val shizukuAppVersion: String = "",
    val engineMode: EngineMode = EngineMode.AUTO,
    /** 最近一次实际使用的引擎（Root / Shizuku）；null = 尚未执行过操作 */
    val activeEngine: String? = null,
    /** 导入的私有目录副本是否强制改名为与目标文件同名（需已填目标路径） */
    val renameToTarget: Boolean = false,
    /** 动态取色（壁纸取色）开关；默认 false，让品牌 M3 色板生效 */
    val dynamicColor: Boolean = false,
    /** Root 引擎实时可用性；null = 尚未探测（探测会起 su 进程，可能弹 KSU/Magisk 授权窗） */
    val rootReady: Boolean? = null,
    /** Root 探测进行中（授权按钮据此禁用） */
    val rootProbing: Boolean = false,
    /** 本地引擎（免 Shizuku/Root）权限状态；null = 尚未探测 */
    val localReady: Boolean? = null,
    val busy: Boolean = false,
    val busyLabel: String = "",
    /** 待 SAF 授权的替换/还原请求；非 null 时 UI 弹系统目录选择器 */
    val safPending: SafPending? = null,
    val presets: List<QuickPreset> = emptyList(),
    val browser: FileBrowserState? = null,
    val log: List<LogEntry> = emptyList()
) {
    /** 主操作可用性统一在这里派生，UI 各处（按钮 / 提示 / 保存快捷方式）共用同一份条件 */
    val canReplace: Boolean
        get() = !busy && safPending == null && importedLocalPath != null && targetPath.isNotBlank()

    val canRestore: Boolean
        get() = !busy && safPending == null && targetPath.isNotBlank() && backupPath.isNotBlank()

    /** 底部操作栏的一句话状态：告诉用户「还差什么」，而不是只把按钮置灰 */
    val actionHint: String
        get() = when {
            busy -> "正在处理，请稍候"
            safPending != null -> "请先完成系统目录授权"
            importedLocalPath == null -> "先导入要替换进目标的文件"
            targetPath.isBlank() -> "还差目标文件路径"
            !shizukuGranted && rootReady != true -> "尚未获得权限：点按顶部状态卡完成授权"
            else -> "准备就绪"
        }
}

/** 文件选择器里的一个条目 */
@Immutable
data class DirEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long
)

/**
 * 日志级别：DEBUG 是排查用过程细节（步骤耗时 / 诊断输出 / 引擎解析），
 * 时间线上弱化显示；INFO/WARN/ERROR 是用户级结果。导出时全量保留。
 */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** 单条日志；时间戳随条目记录，导出时用于还原时序（排查超时 / 卡死类问题必需） */
@Immutable
data class LogEntry(
    val time: Long,
    val level: LogLevel,
    val message: String
)

/** 待 SAF 目录授权的替换/还原请求；用户在系统选择器中选中目标所在目录后自动继续 */
@Immutable
data class SafPending(
    val isRestore: Boolean,
    val target: String,
    val backup: String,
    val sourcePath: String,
    val sourceName: String
)

/** 文件选择器的排序方式（文件夹始终排在文件前） */
enum class SortBy(val label: String) {
    NAME("名称"),
    SIZE("大小"),
    DATE("修改时间")
}

/** 应用内文件选择器的浏览状态；null = 选择器关闭 */
@Immutable
data class FileBrowserState(
    val dir: String,
    val entries: List<DirEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 会话内返回栈：进入子目录前把当前目录压入，"上一级"/系统返回键弹出 */
    val stack: List<String> = emptyList(),
    /** 最近导入的源文件路径（跨会话持久化，见 BrowserMemory） */
    val recentImports: List<String> = emptyList(),
    /** 排序方式与方向（跨会话持久化） */
    val sortBy: SortBy = SortBy.NAME,
    val sortAsc: Boolean = true
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** 文件选择器的起始目录 */
        const val BROWSER_START_DIR = "/storage/emulated/0/Download"
    }

    private val _state = MutableStateFlow(
        UiState(
            presets = QuickPresetStore.load(app),
            engineMode = EngineStore.load(app),
            renameToTarget = EngineStore.loadRenameToTarget(app),
            dynamicColor = EngineStore.loadDynamicColor(app)
        )
    )
    val state: StateFlow<UiState> = _state

    /** 待 SAF 授权的请求（UI 选择器回调后消费）；不放 UiState，避免序列化语义混乱 */
    private var safInFlight: SafPending? = null
    private val operationLock = Any()
    private var operationInFlight = false

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun appendLog(line: String, level: LogLevel = LogLevel.INFO) {
        // 同步镜像 logcat：adb logcat -s FileSwap 可交叉核对完整过程
        when (level) {
            LogLevel.DEBUG -> Log.d("FileSwap", line)
            LogLevel.WARN -> Log.w("FileSwap", line)
            LogLevel.ERROR -> Log.e("FileSwap", line)
            else -> Log.i("FileSwap", line)
        }
        // 封顶 500 条；时间戳随条目记录：导出排查超时 / 卡死类问题时需要时序
        _state.update {
            it.copy(log = (it.log + LogEntry(System.currentTimeMillis(), level, line)).takeLast(500))
        }
    }

    /** 过程细节（步骤耗时 / 诊断输出 / 引擎解析）：时间线弱化显示，导出全量保留 */
    private fun appendDebug(line: String) = appendLog(line, LogLevel.DEBUG)

    private fun setBusy(busy: Boolean, label: String = "") {
        _state.update { it.copy(busy = busy, busyLabel = label) }
    }

    /** Claim the single file-operation slot synchronously, before a coroutine can be scheduled. */
    private fun beginOperation(label: String): Boolean {
        val started = synchronized(operationLock) {
            if (operationInFlight || _state.value.busy || _state.value.safPending != null) {
                false
            } else {
                operationInFlight = true
                _state.update { it.copy(busy = true, busyLabel = label) }
                true
            }
        }
        if (!started) appendLog("⚠️ 另一个文件操作正在进行或等待目录授权，请完成后再试")
        return started
    }

    /** Keep the slot reserved while the system SAF picker is open. */
    private fun endOperation(keepSafReservation: Boolean = false) {
        synchronized(operationLock) {
            val keep = keepSafReservation && _state.value.safPending != null
            if (!keep) operationInFlight = false
            _state.update { it.copy(busy = false, busyLabel = "") }
        }
    }

    // ------------------------------------------------------------------
    // 执行引擎：Root（uid 0）优先于 Shizuku（shell uid 2000）
    // ------------------------------------------------------------------

    /** 权限选择器：直接选定引擎（替代旧的循环切换） */
    fun setEngineMode(mode: EngineMode) {
        if (_state.value.engineMode == mode) return
        EngineStore.save(getApplication(), mode)
        _state.value = _state.value.copy(engineMode = mode, activeEngine = null)
        appendLog("⚙️ 执行引擎切换为：${mode.label}")
    }

    /** 导入文件改名同目标开关 */
    fun setRenameToTarget(enabled: Boolean) {
        EngineStore.saveRenameToTarget(getApplication(), enabled)
        _state.value = _state.value.copy(renameToTarget = enabled)
    }

    /** 动态取色（壁纸取色）开关；关闭时用品牌 M3 色板 */
    fun setDynamicColor(enabled: Boolean) {
        EngineStore.saveDynamicColor(getApplication(), enabled)
        _state.value = _state.value.copy(dynamicColor = enabled)
    }

    /** 导入副本的目标文件名：开关开启且已填目标路径时，强制用目标文件的文件名 */
    private fun resolveImportName(original: String): String {
        val s = _state.value
        val targetBase = s.targetPath.substringAfterLast('/').trim()
        return if (s.renameToTarget && targetBase.isNotBlank()) targetBase else original
    }

    /** 打开权限抽屉时刷新各引擎实时可用性 */
    fun probeEngines() {
        // checkShizuku 内含 Shizuku.pingBinder()（Binder IPC），不能留在主线程
        viewModelScope.launch {
            val (granted, uid) = withContext(Dispatchers.IO) {
                val g = ShizukuManager.hasPermission()
                g to (if (g) ShizukuManager.serverUid() else -1)
            }
            _state.update { it.copy(shizukuGranted = granted, shizukuUid = uid) }
        }
        probeRoot()
        probeLocal()
    }

    /** 本地引擎探测：纯本地权限检查，不起任何进程，可在 onResume 反复调用 */
    fun probeLocal() {
        val ready = LocalStorage.isReady(getApplication())
        if (_state.value.localReady != ready) {
            _state.update { it.copy(localReady = ready) }
        }
    }

    /** Root 探测：fresh build su（可能触发 KSU/Magisk 授权弹窗），结果写 rootReady */
    fun probeRoot() {
        if (_state.value.rootProbing) return
        _state.value = _state.value.copy(rootProbing = true, rootReady = null)
        viewModelScope.launch {
            val ok = RootManager.isRootGranted()
            _state.value = _state.value.copy(rootReady = ok, rootProbing = false)
        }
    }

    /** 按当前引擎模式解析出实际可用的文件操作门面 */
    private suspend fun resolveOps(): FileOps {
        val t0 = SystemClock.elapsedRealtime()
        val ops = when (_state.value.engineMode) {
            EngineMode.SHIZUKU -> shizukuOps()
            // 强制 Root：失败即报具体原因（su 未授权 / 服务超时），不静默降级
            EngineMode.ROOT -> try {
                rootOps()
            } catch (e: Exception) {
                appendLog("❌ Root 不可用：${e.message}")
                throw e
            }
            // 自动：Root 可用则优先；不可用静默回退 Shizuku（引擎 pill 会显示实际引擎）
            EngineMode.AUTO -> if (RootManager.isRootGranted()) {
                try {
                    rootOps()
                } catch (e: Exception) {
                    appendLog("⚠️ Root 绑定失败，本次回退 Shizuku：${e.message}")
                    shizukuOps()
                }
            } else {
                shizukuOps()
            }
            // 本地引擎：应用自身身份直接操作共享存储，零 IPC（实现见 LocalOps）。
            // 能力边界：11+ 拿不到其他应用的 Android/data（MANAGE 官方排除项），
            // 此类目标会得到具体报错而非静默失败。
            EngineMode.LOCAL -> {
                if (!LocalStorage.isReady(getApplication())) {
                    throw IllegalStateException(
                        "本地存储未授权：请在权限选择器里授予「所有文件访问」/ 存储权限"
                    )
                }
                _state.value = _state.value.copy(activeEngine = "本地")
                LocalOps()
            }
        }
        appendDebug(
            "引擎解析完成：${_state.value.activeEngine ?: _state.value.engineMode.label}" +
                "（${SystemClock.elapsedRealtime() - t0} ms）"
        )
        return ops
    }

    private suspend fun rootOps(): FileOps {
        val ops = RootOps(RootManager.getService(getApplication()))
        _state.value = _state.value.copy(activeEngine = "Root")
        return ops
    }

    private suspend fun shizukuOps(): FileOps {
        // 先查权限再绑定：没有 Shizuku 权限时 bindUserService 仍可能连上，
        // 于是每个文件操作都失败于通用错误，用户看不到「请先授权」的指引
        if (!withContext(Dispatchers.IO) { ShizukuManager.hasPermission() }) {
            throw IllegalStateException(
                "Shizuku 未授权：请点按状态卡选择权限来源并完成授权"
            )
        }
        val ops = ShizukuOps(ShizukuManager.getFileService())
        _state.value = _state.value.copy(activeEngine = "Shizuku")
        return ops
    }

    fun checkShizuku() {
        // 连同服务端 uid / 协议版本 / Shizuku 应用版本一起刷新：
        // 导出头部不再依赖用户是否打开过权限抽屉，且能分辨 13.6.0 这类问题版本
        viewModelScope.launch {
            val appVer = withContext(Dispatchers.IO) {
                ShizukuManager.appVersionName(getApplication())
            }
            val (granted, uid, ver) = withContext(Dispatchers.IO) {
                val g = ShizukuManager.hasPermission()
                Triple(
                    g,
                    if (g) ShizukuManager.serverUid() else -1,
                    if (g) ShizukuShell.serverVersion() else -1
                )
            }
            _state.update {
                it.copy(
                    shizukuGranted = granted,
                    shizukuUid = uid,
                    shizukuServerVersion = ver,
                    shizukuAppVersion = appVer
                )
            }
        }
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission { granted ->
            _state.update { it.copy(shizukuGranted = granted) }
            appendLog(if (granted) "✅ 已获得 Shizuku 权限" else "❌ Shizuku 权限被拒绝或不可用")
            if (granted) {
                viewModelScope.launch {
                    val uid = withContext(Dispatchers.IO) { ShizukuManager.serverUid() }
                    _state.update { it.copy(shizukuUid = uid) }
                    if (uid == 2000) {
                        appendLog("ℹ️ Shizuku 以 shell(ADB) 身份运行：无法读写应用私有目录，部分受限目录也可能无权访问")
                    }
                }
            }
        }
    }

    fun setTargetPath(path: String) {
        _state.value = _state.value.copy(targetPath = path, backupPath = defaultBackupPath(path))
    }

    fun setBackupPath(path: String) {
        _state.value = _state.value.copy(backupPath = path)
    }

    /** 备份路径固定为 目标.bak：路径稳定可预期，还原与预设都依赖这一点 */
    private fun defaultBackupPath(target: String): String {
        if (target.isBlank()) return ""
        return "$target.bak"
    }

    /** 导入替换文件到 App 私有目录（SAF 兜底路径；改名逻辑与选择器导入一致） */
    fun importFile(context: Context, uri: Uri) {
        if (!beginOperation("正在导入文件…")) return
        viewModelScope.launch {
            try {
                // 文件名查询（binder IPC）与落盘全部在 IO 线程
                var renamedFrom: String? = null
                val destFile = withContext(Dispatchers.IO) {
                    val original = queryFileName(context, uri) ?: "imported_file"
                    val name = resolveImportName(original)
                    if (name != original) renamedFrom = original
                    val destDir = File(context.filesDir, "imported").apply { mkdirs() }
                    val destFile = File(destDir, name)
                    // 写临时文件后改名：导入失败时不会把上一次的副本截成半截
                    val tmp = File(destDir, name + ".fswap-import-" + System.nanoTime())
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("无法读取所选文件（provider 未返回数据流）")
                        input.use { ins ->
                            tmp.outputStream().use { out -> ins.copyTo(out) }
                        }
                        if (!tmp.renameTo(destFile)) {
                            if (!destFile.delete() || !tmp.renameTo(destFile)) {
                                throw IllegalStateException("导入文件落盘失败")
                            }
                        }
                    } finally {
                        if (tmp.exists()) tmp.delete()
                    }
                    destFile
                }
                _state.update {
                    it.copy(
                        importedFileName = destFile.name,
                        importedLocalPath = destFile.absolutePath
                    )
                }
                appendLog(
                    if (renamedFrom != null) "📥 已导入文件：${destFile.name}（由 $renamedFrom 强制改名）"
                    else "📥 已导入文件：${destFile.name}"
                )
            } catch (e: Exception) {
                appendLog("❌ 导入失败：${e.message}")
            } finally {
                endOperation()
            }
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        return name
    }

    // ------------------------------------------------------------------
    // 应用内文件选择器（通过 Shizuku listDir 浏览，能到达 SAF 看不到的目录）
    // ------------------------------------------------------------------

    fun openBrowser() {
        // 记忆功能：从上次浏览的目录继续 + 恢复排序偏好，没有记录才回到默认起点
        val start = BrowserMemory.lastDir(getApplication()) ?: BROWSER_START_DIR
        _state.value = _state.value.copy(
            browser = FileBrowserState(
                dir = start,
                loading = true,
                recentImports = BrowserMemory.recentImports(getApplication()),
                sortBy = BrowserMemory.sortMode(getApplication()),
                sortAsc = BrowserMemory.sortAsc(getApplication())
            )
        )
        browseInternal(start)
    }

    fun closeBrowser() {
        _state.value = _state.value.copy(browser = null)
    }

    /** 导航到目标目录（把当前目录压入返回栈）。
     *  目标 == 当前目录时（重试同 chip / 重组前双击行）只重列、**不压栈**，
     *  否则返回栈会被同目录条目污染，「上一级」就会反复回到当前目录。 */
    fun browseTo(path: String) {
        val cur = _state.value.browser
        if (cur != null && path == cur.dir) {
            browseInternal(path)
            return
        }
        _state.update {
            it.copy(
                browser = if (cur != null) {
                    // cur.copy 保留 sortBy/sortAsc——重造 FileBrowserState 会静默丢失排序偏好
                    cur.copy(dir = path, loading = true, stack = cur.stack + cur.dir, error = null)
                } else {
                    FileBrowserState(dir = path, loading = true)
                }
            )
        }
        browseInternal(path)
    }

    /** 上一级（从返回栈弹出；栈空则不动，UI 会禁用按钮）。
     *  两条历史教训都体现在这里：
     *  1. 必须立即更新 dir + loading——否则 browseInternal 的过期结果守卫
     *     （current.dir != path）会把本次列目录结果整个丢弃（按钮"无效"）。
     *  2. 弹栈要跳过与当前目录相同的残留条目（历史同目录导航的污染），
     *     否则会"回到"当前目录本身，表现为返回旧列表。 */
    fun browseUp() {
        val cur = _state.value.browser ?: return
        var stack = cur.stack
        var target: String? = null
        while (stack.isNotEmpty()) {
            val candidate = stack.last()
            stack = stack.dropLast(1)
            if (candidate != cur.dir) {
                target = candidate
                break
            }
        }
        _state.update {
            it.copy(
                browser = cur.copy(
                    dir = target ?: cur.dir,
                    loading = target != null,
                    stack = stack,
                    error = null
                )
            )
        }
        target?.let { browseInternal(it) }
    }

    /**
     * 实际列目录。Binder 调用 + 条目解析 + 排序全部放在后台线程，
     * 大目录（数百条目）不再卡主线程；慢速连点时丢弃过期结果。
     */
    private fun browseInternal(path: String) {
        viewModelScope.launch {
            try {
                val sort = _state.value.browser?.let { it.sortBy to it.sortAsc }
                    ?: (SortBy.NAME to true)
                val entries: List<DirEntry>? = withContext(Dispatchers.Default) {
                    val ops = resolveOps()
                    var raw = ops.listDir(path)
                    if (raw == null) {
                        // listDir 失败只有 null，诊断补齐：为何读不了（存在性 / 权限 / 可遍历性）
                        val diag = runCatching { ops.diagnoseRead(path) }
                            .getOrElse { "诊断失败：${it.message}" }
                        appendDebug("listDir 失败：$path；诊断：$diag")
                        // Shizuku 引擎：UserService 子进程通道被拒 → 服务端 shell 通道
                        if (_state.value.engineMode == EngineMode.SHIZUKU) {
                            appendDebug("UserService 通道列目录失败，改试服务端 shell 通道…")
                            raw = ShizukuShell.listDir(path)
                            appendDebug(
                                "shell 列目录 → " +
                                    if (raw != null) "OK（${raw.size} 条）" else "FAIL"
                            )
                        }
                    }
                    raw?.let {
                        sortEntries(it.mapNotNull { e -> parseDirEntry(e) }, sort.first, sort.second)
                    }
                }
                val current = _state.value.browser ?: return@launch
                if (current.dir != path) return@launch // 已跳到别的目录，丢弃过期结果
                if (entries == null) {
                    _state.value = _state.value.copy(
                        browser = current.copy(
                            loading = false,
                            error = "无法读取该目录：路径可能不存在、不是文件夹，或当前引擎没有访问权限"
                        )
                    )
                } else {
                    BrowserMemory.setLastDir(getApplication(), path)
                    _state.value = _state.value.copy(
                        browser = current.copy(loading = false, entries = entries)
                    )
                }
            } catch (e: Exception) {
                appendLog("❌ 读取目录失败：${e.message}")
                _state.value = _state.value.copy(
                    browser = _state.value.browser?.copy(
                        loading = false,
                        error = "读取失败：${e.message}。请确认 Shizuku/Root 已启动并在本应用中完成授权"
                    )
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // 排序：切换即时生效（仅重排已列出的条目，不重新列目录），偏好持久化
    // ------------------------------------------------------------------

    fun setSortBy(by: SortBy) {
        val cur = _state.value.browser ?: return
        if (cur.sortBy == by) return
        BrowserMemory.saveSort(getApplication(), by, cur.sortAsc)
        resort(cur.copy(sortBy = by))
    }

    fun toggleSortDir() {
        val cur = _state.value.browser ?: return
        BrowserMemory.saveSort(getApplication(), cur.sortBy, !cur.sortAsc)
        resort(cur.copy(sortAsc = !cur.sortAsc))
    }

    /** 对当前已列出的条目就地重排；提交前校验目录未变（与列目录共用防抖语义）。
     *  写入在 Default 线程，用 update{} 保证原子 RMW（与其他线程写入并发安全）。 */
    private fun resort(updated: FileBrowserState) {
        val dir = updated.dir
        viewModelScope.launch(Dispatchers.Default) {
            val sorted = sortEntries(updated.entries, updated.sortBy, updated.sortAsc)
            _state.update { now ->
                val browser = now.browser ?: return@update now
                if (browser.dir != dir) return@update now // 已跳到别的目录，丢弃过期重排
                now.copy(
                    browser = browser.copy(
                        entries = sorted,
                        sortBy = updated.sortBy,
                        sortAsc = updated.sortAsc
                    )
                )
            }
        }
    }

    /**
     * 自然序文件名比较（仿 GNOME glib `g_utf8_collate_key_for_filename`，
     * 与 Material Files 的 CollatorFileNameExtensions 同源）。
     *
     * 处理两件事：数字按数值位数比较（file1 < file2 < file10），
     * `.` 前后分段独立比较（a.c 在 a.h 之前于 agen.c）；忽略大小写。
     * 返回可比较的键字符串，供排序稳定使用。
     */
    private fun naturalNameKey(name: String): String {
        val key = StringBuilder(name.length * 2)
        var i = 0
        while (i < name.length) {
            val c = name[i]
            when {
                c == '.' -> {
                    // 点号替换为哨兵字节（比任何正常字符都小），
                    // 保证 a.c 先于 a.h，而 agen.c 排在二者之后
                    key.append('\u0001').append('\u0001').append('\u0001').append('\u0001')
                    i++
                }
                c.isAsciiDigit() -> {
                    // 数字段：统计有效位数与前导零
                    var j = i
                    while (j < name.length && name[j].isAsciiDigit()) j++
                    val digits = name.substring(i, j)
                    val value = digits.trimStart('0')
                    // 前补 (位数-1) 个 ':'（':' 的码值 > '9'），
                    // 使 1 < 2 < ... < 10 < 100，而不是字典序
                    repeat(value.length - 1) { key.append(':') }
                    key.append(value.ifEmpty { "0" })
                    // 前导零个数作为次级键，使 file01 与 file1 可区分
                    key.append('\u0002')
                        .append((digits.length - value.length).toString())
                    i = j
                }
                else -> {
                    key.append(c.lowercaseChar())
                    i++
                }
            }
        }
        return key.toString()
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    /**
     * 排序：文件夹永远排前，且**文件夹也参与键排序**。
     *
     * 性能要点：
     * 1. 分治用一次 partition（而不是两次 filter 遍历）
     * 2. 名称键做**会话级缓存**，但 VM 不再常驻脏表
     * 3. SIZE/DATE 时全走 Comparable 数值/字符串直比，不 String.format
     */
    private fun sortEntries(entries: List<DirEntry>, by: SortBy, asc: Boolean): List<DirEntry> {
        val (dirs, files) = entries.partition { it.isDir }
        val nameKeyCache = HashMap<String, String>(dirs.size + files.size)
        fun cachedNameKey(name: String): String =
            nameKeyCache.getOrPut(name) { naturalNameKey(name) }

        val dirCmp: Comparator<DirEntry> = when (by) {
            SortBy.NAME -> compareBy { cachedNameKey(it.name) }
            SortBy.SIZE -> compareBy { cachedNameKey(it.name) }
            SortBy.DATE -> compareBy<DirEntry> { it.mtime }.thenBy { cachedNameKey(it.name) }
        }
        val fileCmp: Comparator<DirEntry> = when (by) {
            SortBy.NAME -> compareBy { cachedNameKey(it.name) }
            SortBy.SIZE -> compareBy<DirEntry> { it.size }.thenBy { cachedNameKey(it.name) }
            SortBy.DATE -> compareBy<DirEntry> { it.mtime }.thenBy { cachedNameKey(it.name) }
        }
        val dirSorted = dirs.sortedWith(dirCmp)
        val fileSorted = files.sortedWith(fileCmp)
        return if (asc) dirSorted + fileSorted
        else dirSorted.asReversed() + fileSorted.asReversed()
    }

    /** 从选择器里选定文件后，经当前引擎拷入 App 私有目录作为替换源 */
    fun importFromBrowser(path: String) {
        if (!beginOperation("正在导入…")) return
        viewModelScope.launch {
            try {
                val ops = resolveOps()
                val original = path.substringAfterLast('/').ifBlank { "imported_file" }
                val name = resolveImportName(original)
                val destDir = File(getApplication<Application>().filesDir, "imported").apply { mkdirs() }
                val dest = File(destDir, name)

                // 关键：让服务进程只负责「打开源文件并把 fd 交回来」，落盘由应用
                // 自己的 UID 完成。shell 域没有 app_data_file 权限（AOSP 12~16 一致），
                // 直接让 shell 服务写 /data/user/0/<pkg>/ 必然失败——这正是
                // 「Shizuku 下导入失败、root 下正常」的根因。
                val error = withContext(Dispatchers.IO) {
                    var t = SystemClock.elapsedRealtime()
                    val pfd = ops.openRead(path)
                        ?: return@withContext "无法打开源文件：$path；原因：" +
                            runCatching { ops.diagnoseRead(path) }
                                .getOrElse { "诊断失败：${it.message}" }
                    appendDebug("① 打开源 fd → OK（${SystemClock.elapsedRealtime() - t} ms）")
                    val tmp = File(destDir, name + ".fswap-import-" + System.nanoTime())
                    try {
                        var copied = 0L
                        t = SystemClock.elapsedRealtime()
                        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                            tmp.outputStream().use { output ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    if (n > 0) {
                                        output.write(buf, 0, n)
                                        copied += n
                                    }
                                }
                            }
                        }
                        appendDebug("② 落盘 → OK（${SystemClock.elapsedRealtime() - t} ms，$copied 字节）")
                        if (!tmp.renameTo(dest)) {
                            if (!dest.delete() || !tmp.renameTo(dest)) {
                                return@withContext "写入本地副本失败：rename 落盘未成功"
                            }
                        }
                        null
                    } catch (e: Exception) {
                        val m = e.message?.takeIf { it.isNotBlank() }
                        "写入本地副本失败：${e.javaClass.simpleName}${if (m != null) ": $m" else ""}"
                    } finally {
                        runCatching { if (tmp.exists()) tmp.delete() }
                    }
                }
                if (error != null) {
                    // Shizuku 引擎：UserService 通道打开失败 → 服务端 shell 通道
                    // （vivo 上其他应用 Android/data 的典型场景：QQ 接收目录等）
                    if (_state.value.engineMode == EngineMode.SHIZUKU) {
                        appendDebug("UserService 通道导入失败，改试服务端 shell 通道…")
                        val shellErr = runCatching {
                            val tmp = File(destDir, name + ".fswap-shell-" + System.nanoTime())
                            try {
                                val copied = ShizukuShell.pumpFromRemote(path, tmp)
                                appendDebug("shell 落盘 → OK（$copied 字节）")
                                if (!tmp.renameTo(dest) &&
                                    !(dest.delete() && tmp.renameTo(dest))
                                ) {
                                    return@runCatching "rename 落盘未成功"
                                }
                                null
                            } finally {
                                runCatching { if (tmp.exists()) tmp.delete() }
                            }
                        }.getOrElse { e -> e.message ?: e.javaClass.simpleName }
                        if (shellErr == null) {
                            _state.value = _state.value.copy(
                                importedFileName = name,
                                importedLocalPath = dest.absolutePath,
                                browser = null
                            )
                            BrowserMemory.pushRecentImport(getApplication(), path)
                            appendLog("📥 已导入文件：$name（来自 $path，服务端 shell 通道）")
                            return@launch
                        }
                        appendDebug("shell 导入失败：$shellErr")
                    }
                    appendLog("❌ 导入失败：$error")
                    return@launch
                }
                _state.value = _state.value.copy(
                    importedFileName = name,
                    importedLocalPath = dest.absolutePath,
                    browser = null
                )
                BrowserMemory.pushRecentImport(getApplication(), path)
                appendLog(
                    if (name != original) "📥 已导入文件：$name（由 $original 强制改名，来自 $path）"
                    else "📥 已导入文件：$name（来自 $path）"
                )
            } catch (e: Exception) {
                appendLog("❌ 导入失败：${e.message}")
            } finally {
                endOperation()
            }
        }
    }

    /** 解析 listDir 的编码条目："d|名称" / "f|名称|大小|mtime"。
     *  文件按「最后两段是大小和时间」切，名称里含 | 也不会错位。 */
    private fun parseDirEntry(raw: String): DirEntry? {
        if (raw.length < 3 || raw[1] != '|') return null
        val rest = raw.substring(2)
        return when (raw[0]) {
            'd' -> DirEntry(rest, isDir = true, size = 0L, mtime = 0L)
            'f' -> {
                val i1 = rest.lastIndexOf('|')
                if (i1 <= 0) return DirEntry(rest, isDir = false, size = 0L, mtime = 0L)
                val i2 = rest.lastIndexOf('|', i1 - 1)
                if (i2 < 0) return DirEntry(rest, isDir = false, size = 0L, mtime = 0L)
                try {
                    DirEntry(
                        name = rest.substring(0, i2),
                        isDir = false,
                        size = rest.substring(i2 + 1, i1).toLong(),
                        mtime = rest.substring(i1 + 1).toLong()
                    )
                } catch (e: NumberFormatException) {
                    DirEntry(rest, isDir = false, size = 0L, mtime = 0L)
                }
            }
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // 一键替换：读原时间戳 → 备份（时间戳随备份走）→ 替换 → 校正目标时间戳
    // 与旧五步流程的最终产物逐项等价（目标内容 + 目标 mtime）。
    // ------------------------------------------------------------------

    fun quickReplace(target: String, backup: String, sourcePath: String, sourceName: String) {
        if (target.isBlank()) {
            appendLog("⚠️ 请先填写目标文件路径")
            return
        }
        if (backup.isBlank()) {
            appendLog("⚠️ 备份路径为空")
            return
        }
        if (!beginOperation("正在备份原文件…")) return
        viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            appendDebug("▶ 替换开始：目标=$target，源=$sourcePath，备份=$backup")
            try {
                // 预检挪入 IO：源存在性 stat 与路径规范化不占主线程
                val preflightError = withContext(Dispatchers.IO) {
                    // 必须用规范化路径比较：absolutePath 不解析 ".."，形如
                    // /a/../b 与 /b 会判为不同却指向同一文件——那样 copyFile 会
                    // 边读边写同一路径，把目标截成 0 字节。
                    fun canon(p: String) = try {
                        File(p).canonicalPath
                    } catch (e: Exception) {
                        File(p).absolutePath
                    }
                    val normTarget = canon(target)
                    val normBackup = canon(backup)
                    val normSource = canon(sourcePath)
                    when {
                        !File(sourcePath).exists() -> "❌ 替换源文件不存在：$sourcePath"
                        File(target).isDirectory -> "❌ 目标路径是一个目录：$target"
                        File(backup).isDirectory -> "❌ 备份路径是一个目录：$backup"
                        normTarget == normBackup || normTarget == normSource || normBackup == normSource ->
                            "⚠️ 目标 / 备份 / 替换源路径不能相同（含 .. 等等价写法）"
                        else -> null
                    }
                }
                if (preflightError != null) {
                    appendLog(preflightError)
                    return@launch
                }
                val ops = resolveOps()

                // 1. 原时间戳（替换后要保持的值，也是还原时要恢复的值）
                var t = SystemClock.elapsedRealtime()
                val orig = withContext(Dispatchers.IO) { ops.getLastModified(target) }
                appendDebug(
                    "① 读取目标 mtime → ${if (orig >= 0) "OK" else "FAIL"}" +
                        "（${SystemClock.elapsedRealtime() - t} ms，mtime=$orig）"
                )
                if (orig < 0) {
                    // getLastModified 只有 -1，区分不了「真不存在」与「无权访问」，
                    // 用诊断补齐：uid、exists、canRead、封锁边界、gids、SELinux
                    val diag = runCatching { ops.diagnoseRead(target) }
                        .getOrElse { "诊断失败：${it.message}" }
                    appendLog("❌ 目标文件不存在或不可读：$target")
                    appendDebug("诊断：$diag")

                    // 回退链：服务端 shell → 跨引擎矩阵 → 本地引擎 → SAF
                    val canShell = _state.value.engineMode == EngineMode.SHIZUKU ||
                        (_state.value.engineMode == EngineMode.LOCAL && _state.value.shizukuGranted)
                    if (canShell) {
                        appendDebug("UserService 通道不可达，改试服务端 shell 通道…")
                        val shellOk = runCatching { replaceViaShell(target, backup, sourcePath, sourceName) }
                            .onFailure { appendDebug("shell 通道失败：${it.message}") }
                            .getOrDefault(false)
                        if (shellOk) return@launch
                    }
                    diagMatrix(target)
                    if (_state.value.localReady == true) {
                        // MT 的可能机制：Shizuku 作引导给自己授 MANAGE，然后用自己 uid 干活。
                        // 我们本地引擎已授权 MANAGE（vivo 上实测即知 vivo 是否给 MANAGE 应用放行）
                        appendDebug("改试本地引擎回退（应用 uid + MANAGE）…")
                        val localOk = runCatching { replaceViaLocal(target, backup, sourcePath, sourceName) }
                            .onFailure { appendDebug("本地回退异常：${it.message}") }
                            .getOrDefault(false)
                        if (localOk) return@launch
                    } else {
                        appendLog("💡 本地存储未授权，已跳过「应用 uid + MANAGE」通道——授权后重试可多一条通路")
                    }
                    // SAF 树授权：低版本设备仍可尝试，Android 11+ 对受限目录不弹无效选择器。
                    if (trySafReplaceOrPrompt(target, backup, sourcePath, sourceName)) return@launch
                    appendLog("❌ 所有通道均无法访问目标（跨引擎诊断矩阵见 DEBUG 日志）")
                    return@launch
                }

                // 2. 备份，并把备份文件的 mtime 设为原目标 mtime——
                //    还原时从备份文件本身就能取回原始时间戳
                t = SystemClock.elapsedRealtime()
                val backupErr = withContext(Dispatchers.IO) { ops.copyFile(target, backup) }
                appendDebug(
                    "② 备份 → ${if (backupErr == null) "OK" else "FAIL"}" +
                        "（${SystemClock.elapsedRealtime() - t} ms）${backupErr?.let { "：$it" } ?: ""}"
                )
                if (backupErr != null) {
                    appendLog("❌ 备份失败，已中止替换：$backupErr")
                    return@launch
                }
                // 时间戳校正失败不算致命：FUSE 上 utimensat 可能被拒（非属主）
                if (!withContext(Dispatchers.IO) { ops.setLastModified(backup, orig) }) {
                    appendLog("⚠️ 备份时间戳未能保持（不影响内容）")
                }
                appendLog("🗄️ 已备份：$target -> $backup")

                // 3. 替换
                setBusy(true, "正在替换…")
                t = SystemClock.elapsedRealtime()
                // 替换源在应用私有目录：shell(2000) 读不了，必须由应用自己打开源文件，
                // 把 fd 交给服务去写目标（root 引擎恰好读得了，所以只有 shell 环境才暴露）
                val replaceErr = withContext(Dispatchers.IO) {
                    val pfd = try {
                        ParcelFileDescriptor.open(File(sourcePath), ParcelFileDescriptor.MODE_READ_ONLY)
                    } catch (e: Exception) {
                        return@withContext "无法读取替换源文件：${e.javaClass.simpleName}: ${e.message}"
                    }
                    pfd.use { ops.copyFromFd(it, target) }
                }
                appendDebug(
                    "③ 写入目标 → ${if (replaceErr == null) "OK" else "FAIL"}" +
                        "（${SystemClock.elapsedRealtime() - t} ms）${replaceErr?.let { "：$it" } ?: ""}"
                )
                if (replaceErr != null) {
                    // 目标仍存在不代表原内容完整：受限目录会直接覆盖原 inode，
                    // I/O 中断可能留下部分内容，因此任意写失败都从已完成的备份恢复。
                    val rollbackErr = withContext(Dispatchers.IO) { ops.copyFile(backup, target) }
                    if (rollbackErr != null) {
                        appendLog("❌ 替换失败且回滚失败（$rollbackErr）！请手动从备份还原：$backup")
                        return@launch
                    }

                    withContext(Dispatchers.IO) { ops.setLastModified(target, orig) }
                    appendLog("❌ 当前引擎替换失败，已从备份恢复目标：$replaceErr")

                    val canShell = _state.value.engineMode == EngineMode.SHIZUKU ||
                        (_state.value.engineMode == EngineMode.LOCAL && _state.value.shizukuGranted)
                    if (canShell) {
                        val shellOk = runCatching {
                            replaceViaShell(target, backup, sourcePath, sourceName)
                        }.onFailure { appendDebug("shell 回退异常：${it.message}") }.getOrDefault(false)
                        if (shellOk) return@launch
                    }
                    if (_state.value.localReady == true) {
                        val localOk = runCatching {
                            replaceViaLocal(target, backup, sourcePath, sourceName)
                        }.onFailure { appendDebug("本地回退异常：${it.message}") }.getOrDefault(false)
                        if (localOk) return@launch
                    }
                    if (trySafReplaceOrPrompt(target, backup, sourcePath, sourceName)) return@launch
                    appendLog("❌ 替换失败：$replaceErr")
                    return@launch
                }
                appendLog("🔁 已替换：$target ← $sourceName")

                // 4. 校正目标时间戳
                setBusy(true, "正在校正时间戳…")
                t = SystemClock.elapsedRealtime()
                val timeOk = withContext(Dispatchers.IO) { ops.setLastModified(target, orig) }
                appendDebug(
                    "④ 校正时间戳 → ${if (timeOk) "OK" else "FAIL"}" +
                        "（${SystemClock.elapsedRealtime() - t} ms）"
                )
                if (timeOk) {
                    _state.value = _state.value.copy(targetLastModified = orig)
                    appendLog("✅ 替换完成，修改时间保持 ${sdf.format(Date(orig))}")
                } else {
                    appendLog("⚠️ 替换成功，但校正时间失败")
                }
            } catch (e: Exception) {
                appendLog("❌ 替换失败：${e.message}")
            } finally {
                appendDebug("替换流程结束（总耗时 ${SystemClock.elapsedRealtime() - t0} ms）")
                endOperation(keepSafReservation = _state.value.safPending != null)
            }
        }
    }

    /** 表单当前配置的一键替换 */
    fun quickReplaceFromForm() {
        val s = _state.value
        val source = s.importedLocalPath
        if (source == null) {
            appendLog("⚠️ 请先导入替换文件")
            return
        }
        quickReplace(s.targetPath, s.backupPath, source, s.importedFileName ?: source)
    }

    /** 预设卡的一键替换 */
    fun quickReplacePreset(preset: QuickPreset) {
        quickReplace(preset.targetPath, preset.backupPath, preset.sourcePath, preset.sourceName)
    }

    // ------------------------------------------------------------------
    // 回退链：服务端 shell → 本地引擎（应用 uid + MANAGE）→ SAF 树授权
    // ------------------------------------------------------------------

    /**
     * 跨引擎诊断矩阵：用每个可用身份各探测一次目标路径，一次拿齐
     * 「vivo 到底给谁放行」。定性依据，不改变执行结果。
     */
    private suspend fun diagMatrix(target: String) {
        val b = StringBuilder("┌ 跨引擎诊断矩阵：$target")
        var shellOut: String? = null
        val canShell = _state.value.engineMode == EngineMode.SHIZUKU ||
            (_state.value.engineMode == EngineMode.LOCAL && _state.value.shizukuGranted)
        if (canShell) {
            runCatching {
                val aDataIdx = target.indexOf("/Android/data/")
                val aData = if (aDataIdx >= 0) target.substring(0, aDataIdx + "/Android/data".length)
                else "/storage/emulated/0/Android/data"
                val pkgDir = target.substringBeforeLast('/').substringBeforeLast('/')
                val parent = target.substringBeforeLast('/')
                val (c, o) = ShizukuShell.exec(
                    "echo ==identity==; id 2>&1; " +
                        "echo ==mount==; cat /proc/self/mountinfo 2>/dev/null | grep -E 'emulated|sdcardfs|fuse' | head -4; " +
                        "echo ==mediaprovider==; pm list packages 2>/dev/null | grep -i mediaprovider; " +
                        "echo ==modes==; stat -c '%A %U %G %n' " +
                        "${ShizukuShell.quote(aData)} ${ShizukuShell.quote(pkgDir)} " +
                        "${ShizukuShell.quote(parent)} ${ShizukuShell.quote(target)} 2>&1; " +
                        "echo ==lower==; ls -ld /data/media/0/Android/data 2>&1; " +
                        "ls -ld /mnt/pass_through/0/Android/data 2>&1; " +
                        "echo ==target==; stat ${ShizukuShell.quote(target)} 2>&1"
                )
                b.append("\n│ [服务端 shell(code=$c)]\n│ ").append(o.replace("\n", "\n│ "))
                shellOut = o
            }.onFailure { b.append("\n│ [服务端 shell] 不可用：${it.message}") }
        } else {
            b.append("\n│ [服务端 shell] 当前引擎不适用，跳过")
        }
        // 判定：shell 是否持有 ext_data_rw(1078) 组——AOSP adbd 提交 4da604b
        // 专门为「shell 写 Android/data（无 sdcardfs 设备）」加了 1078/1079；
        // 缺这两个组的 ROM（实测 vivo）会在内核组 DAC 上被 Android/data 的
        // media_rw:ext_data_rw 2771 权限直接挡下——这就是两个 shell 通道
        // 同时 EACCES 的直接原因。
        shellOut?.let { o ->
            val hasGroup1078 = Regex("\\b1078\\b").containsMatchIn(o)
            val adataGroupExtDataRw = o.contains("ext_data_rw")
            if (!hasGroup1078 && adataGroupExtDataRw) {
                b.append(
                    "\n│ 判定：shell 缺少 ext_data_rw(1078) 组，而 Android/data 属组为 " +
                        "ext_data_rw(2771)——ROM 未授予 shell 该组，故两通道均被内核 DAC 拒绝。" +
                        "AOSP 对应放行机制为 adbd 提交 4da604b（vivo 未采用）。"
                )
            }
        }
        if (_state.value.localReady == true) {
            runCatching {
                val d = withContext(Dispatchers.IO) { LocalOps().diagnoseRead(target) }
                b.append("\n│ [本地 应用uid+MANAGE] ").append(d)
            }.onFailure { b.append("\n│ [本地] 诊断失败：${it.message}") }
        } else {
            b.append("\n│ [本地] 未授权，跳过——如需验证「MT 式机制」(Shizuku 引导 + 应用uid + MANAGE)，")
            b.append("\n│        请到权限抽屉授权「本地存储」后重试")
        }
        if (_state.value.rootReady == true) {
            runCatching {
                val ops = RootOps(RootManager.getService(getApplication()))
                val d = withContext(Dispatchers.IO) { ops.diagnoseRead(target) }
                b.append("\n│ [Root] ").append(d)
            }.onFailure { b.append("\n│ [Root] 诊断失败：${it.message}") }
        } else {
            b.append("\n│ [Root] 未就绪，跳过")
        }
        b.append("\n└ 矩阵结束")
        appendDebug(b.toString())
    }

    /**
     * 本地引擎回退：应用 uid + MANAGE_EXTERNAL_STORAGE 直接读写目标。
     * MT 管理器的可能机制即「Shizuku 作引导给自己授 MANAGE，之后用自己的 uid」；
     * vivo 是否给 MANAGE 应用放行 Android/data，此路径一次实测即知。
     */
    private suspend fun replaceViaLocal(
        target: String,
        backup: String,
        sourcePath: String,
        sourceName: String
    ): Boolean {
        val t0 = SystemClock.elapsedRealtime()
        setBusy(true, "正在以本地引擎备份…")
            val local = LocalOps()
            val readable = withContext(Dispatchers.IO) { local.diagnoseRead(target) }
            if (!readable.contains("open成功")) {
                appendDebug("本地通道诊断：$readable")
                return false
            }
            var t = SystemClock.elapsedRealtime()
            val orig = withContext(Dispatchers.IO) { local.getLastModified(target) }
            appendDebug("local① 读 mtime → OK（${SystemClock.elapsedRealtime() - t} ms，mtime=$orig）")
            t = SystemClock.elapsedRealtime()
            val backupErr = withContext(Dispatchers.IO) { local.copyFile(target, backup) }
            appendDebug(
                "local② 备份 → ${if (backupErr == null) "OK" else "FAIL"}" +
                    "（${SystemClock.elapsedRealtime() - t} ms）${backupErr?.let { "：$it" } ?: ""}"
            )
            if (backupErr != null) {
                appendLog("❌ 备份失败（本地通道）：$backupErr")
                return false
            }
            withContext(Dispatchers.IO) { local.setLastModified(backup, orig) }
            appendLog("🗄️ 已备份：$target -> $backup")
            setBusy(true, "正在替换…")
            t = SystemClock.elapsedRealtime()
            val err = withContext(Dispatchers.IO) { local.copyFile(sourcePath, target) }
            appendDebug(
                "local③ 写入 → ${if (err == null) "OK" else "FAIL"}" +
                    "（${SystemClock.elapsedRealtime() - t} ms）${err?.let { "：$it" } ?: ""}"
            )
            if (err != null) {
                val rb = withContext(Dispatchers.IO) { local.copyFile(backup, target) }
                if (rb == null) {
                    withContext(Dispatchers.IO) { local.setLastModified(target, orig) }
                    appendLog("❌ 替换失败，已自动从备份回滚：$err")
                } else {
                    appendLog("❌ 替换失败且回滚失败（$rb）！请手动从备份还原：$backup")
                }
                return false
            }
            appendLog("🔁 已替换：$target ← $sourceName（本地引擎回退）")
            t = SystemClock.elapsedRealtime()
            val timeOk = withContext(Dispatchers.IO) { local.setLastModified(target, orig) }
            if (timeOk) {
                _state.value = _state.value.copy(targetLastModified = orig)
                appendLog("✅ 替换完成，修改时间保持 ${sdf.format(Date(orig))}")
            } else {
                appendLog("⚠️ 替换成功，但校正时间失败")
            }
            appendDebug("本地回退流程结束（总耗时 ${SystemClock.elapsedRealtime() - t0} ms）")
            return true
    }

    /**
     * 解析可用的 SAF 树 URI，来源按「零用户操作优先」排序：
     * ① **直接构造** `primary:...` 树 URI（XFileTool 同款机制；依赖 MANAGE_EXTERNAL_STORAGE，
     *    无需用户在选择器里操作）
     * ② 已持久化且与目标父目录完全匹配的选择器授权
     * ③ 都没有 → null，调用方转选择器授权流程
     */
    private suspend fun resolveSafTree(target: String): Uri? {
        val context = getApplication<Application>()
        val dirPath = target.substringBeforeLast('/')
        val name = target.substringAfterLast('/')
        SafOps.fabricatedTreeUri(dirPath)?.let { fab ->
            val ok = withContext(Dispatchers.IO) {
                runCatching { SafOps.hasFile(context, fab, name) }.getOrDefault(false)
            }
            appendDebug(
                "SAF 直接构造树 URI → " +
                    if (ok) "可用（MANAGE 生效）" else "不可用（provider 拒绝或未授 MANAGE）"
            )
            if (ok) return fab
        }
        SafOps.grantFor(context, dirPath)?.let { return it }
        return null
    }

    /**
     * SAF 树授权回退。返回 true 表示已成功或已转授权流程（结果另行汇报）；
     * 返回 false 表示 SAF 也无法完成（矩阵日志已覆盖）。
     */
    private suspend fun trySafReplaceOrPrompt(
        target: String,
        backup: String,
        sourcePath: String,
        sourceName: String
    ): Boolean {
        val tree = resolveSafTree(target)
        if (tree != null) {
            appendDebug("改试 SAF 通道…")
            if (replaceViaSaf(target, backup, sourcePath, sourceName, tree)) return true
        }
        if (isAndroidDataOrObbPath(target)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                appendLog("❌ Android 11 及以上禁止通过系统目录选择器授权其他应用的 Android/data 或 Android/obb；请使用 Root 或 Shizuku")
                return true
            }
            // Android 10 及以下仍尝试 SAF 授权；OEM 文档提供器可能有不同限制。
            appendLog(
                "💡 请求 SAF 目录授权：在系统选择器中选择目标文件所在目录；是否可访问取决于 Android 版本与设备提供器。"
            )
            val req = SafPending(false, target, backup, sourcePath, sourceName)
            safInFlight = req
            _state.update { it.copy(safPending = req) }
            return true
        }
        return false
    }

    private fun isAndroidDataOrObbPath(path: String): Boolean =
        path.contains("/Android/data/") || path.contains("/Android/obb/")

    private suspend fun replaceViaSaf(
        target: String,
        backup: String,
        sourcePath: String,
        sourceName: String,
        treeUri: Uri
    ): Boolean {
        val t0 = SystemClock.elapsedRealtime()
        val context = getApplication<Application>()
        val dirPath = target.substringBeforeLast('/')
        val targetName = target.substringAfterLast('/')
        setBusy(true, "正在通过 SAF 通道备份…")
        val tmpBak = File(
            File(context.filesDir, "imported").apply { mkdirs() },
            "saf-backup-${UUID.randomUUID()}"
        )
        var backupReady = false
        try {
            // ① 备份：SAF 读目标 → 本地临时 → 写备份
            var t = SystemClock.elapsedRealtime()
            val bakBytes = withContext(Dispatchers.IO) {
                SafOps.readFile(context, treeUri, targetName, tmpBak)
            }
            appendDebug("saf① 读目标 → OK（${SystemClock.elapsedRealtime() - t} ms，$bakBytes 字节）")
            setBusy(true, "正在写入备份…")
            t = SystemClock.elapsedRealtime()
            val backupInTargetDir = runCatching {
                File(backup).canonicalFile.parentFile == File(dirPath).canonicalFile
            }.getOrDefault(false)
            if (backupInTargetDir) {
                withContext(Dispatchers.IO) {
                    SafOps.writeFile(context, treeUri, backup.substringAfterLast('/'), tmpBak)
                }
            } else {
                // 备份在别处：本地引擎直写（普通目录 MANAGE 可写）
                val ok = withContext(Dispatchers.IO) { LocalOps().copyFile(tmpBak.absolutePath, backup) }
                if (ok != null) throw IllegalStateException("写入备份失败：$ok")
            }
            backupReady = true
            appendDebug("saf② 写备份 → OK（${SystemClock.elapsedRealtime() - t} ms）")
            appendLog("🗄️ 已备份：$target -> $backup")
            // ③ 写入源内容（先删旧文档再新建）
            setBusy(true, "正在替换…")
            t = SystemClock.elapsedRealtime()
            val copied = withContext(Dispatchers.IO) {
                SafOps.writeFile(context, treeUri, targetName, File(sourcePath))
            }
            appendDebug("saf③ 写目标 → OK（${SystemClock.elapsedRealtime() - t} ms，$copied 字节）")
            appendLog("🔁 已替换：$target ← $sourceName")
            _state.value = _state.value.copy(activeEngine = "SAF")
            appendLog("✅ 替换完成（SAF 通道；该通道无法保持修改时间，系统限制）")
            appendDebug("SAF 流程结束（总耗时 ${SystemClock.elapsedRealtime() - t0} ms）")
            return true
        } catch (e: Exception) {
            appendDebug("SAF 流程失败：${e.javaClass.simpleName}: ${e.message}")
            if (backupReady) {
                val restored = runCatching {
                    withContext(Dispatchers.IO) { SafOps.writeFile(context, treeUri, targetName, tmpBak) }
                }
                if (restored.isSuccess) {
                    appendLog("❌ SAF 替换失败，已从临时备份恢复目标：${e.message}")
                } else {
                    appendLog("❌ SAF 替换失败且自动恢复失败；备份仍保存在：$backup。恢复错误：${restored.exceptionOrNull()?.message}")
                }
            }
            return false
        } finally {
            runCatching { if (tmpBak.exists()) tmpBak.delete() }
        }
    }

    /** SAF 还原：备份 → 目标，均经授权树（时间戳无法保持） */
    private suspend fun safRestore(req: SafPending, treeUri: Uri): Boolean {
        val context = getApplication<Application>()
        setBusy(true, "正在通过 SAF 通道还原…")
        try {
            val tmp = File(File(context.filesDir, "imported").apply { mkdirs() }, "saf-restore-" + System.nanoTime())
            val original = File(File(context.filesDir, "imported").apply { mkdirs() }, "saf-original-${UUID.randomUUID()}")
            try {
                val backupInTargetDir = runCatching {
                    File(req.backup).canonicalFile.parentFile == File(req.target.substringBeforeLast('/')).canonicalFile
                }.getOrDefault(false)
                val n1 = if (backupInTargetDir) {
                    withContext(Dispatchers.IO) {
                        SafOps.readFile(context, treeUri, req.backup.substringAfterLast('/'), tmp)
                    }
                } else {
                    val err = withContext(Dispatchers.IO) { LocalOps().copyFile(req.backup, tmp.absolutePath) }
                    if (err != null) throw IllegalStateException("读取备份失败：$err")
                    tmp.length()
                }
                appendDebug("saf 还原① 读备份 → OK（$n1 字节）")
                val targetName = req.target.substringAfterLast('/')
                withContext(Dispatchers.IO) { SafOps.readFile(context, treeUri, targetName, original) }
                val n2 = try {
                    withContext(Dispatchers.IO) { SafOps.writeFile(context, treeUri, targetName, tmp) }
                } catch (e: Exception) {
                    runCatching {
                        withContext(Dispatchers.IO) { SafOps.writeFile(context, treeUri, targetName, original) }
                    }
                        .onFailure { appendLog("❌ SAF 还原失败且目标恢复失败：${it.message}") }
                    throw e
                }
                appendDebug("saf 还原② 写目标 → OK（$n2 字节）")
                _state.value = _state.value.copy(activeEngine = "SAF")
                appendLog("✅ 已还原（SAF 通道；时间戳无法保持）：${req.backup} -> ${req.target}")
                return true
            } finally {
                runCatching { if (tmp.exists()) tmp.delete() }
                runCatching { if (original.exists()) original.delete() }
            }
        } catch (e: Exception) {
            appendDebug("SAF 还原失败：${e.javaClass.simpleName}: ${e.message}")
            return false
        }
    }

    /** UI 的目录选择器回调：授权成功则继续挂起的替换/还原 */
    fun onSafTreePicked(uri: Uri?) {
        val req = safInFlight
        safInFlight = null
        _state.update { it.copy(safPending = null) }
        if (req == null) return
        if (uri == null) {
            appendLog("❌ 未选择目录，操作已终止")
            endOperation()
            return
        }
        viewModelScope.launch {
            try {
                val dirPath = req.target.substringBeforeLast('/')
                if (!SafOps.isTreeForDirectory(uri, dirPath)) {
                    appendLog("❌ 所选目录不是目标文件所在目录，操作已终止")
                    return@launch
                }
                runCatching { withContext(Dispatchers.IO) { SafOps.saveGrant(getApplication(), dirPath, uri) } }
                    .onFailure { appendLog("⚠️ 持久化目录授权失败：${it.message}") }
                // 校验所选目录里确实有目标文件，避免「授权了但选错目录」的静默失败
                val targetExists = withContext(Dispatchers.IO) {
                    runCatching { SafOps.hasFile(getApplication(), uri, req.target.substringAfterLast('/')) }
                        .getOrDefault(false)
                }
                if (!targetExists) {
                    appendLog("❌ 所选目录中未找到目标文件「${req.target.substringAfterLast('/')}」——请选中目标文件所在目录后重试")
                    return@launch
                }
                appendDebug("SAF 授权成功：$dirPath")
                setBusy(true, "正在通过 SAF 执行文件操作…")
                val ok = if (req.isRestore) {
                    runCatching { safRestore(req, uri) }
                        .onFailure { appendDebug("SAF 还原异常：${it.message}") }
                        .getOrDefault(false)
                } else {
                    runCatching { replaceViaSaf(req.target, req.backup, req.sourcePath, req.sourceName, uri) }
                        .onFailure { appendDebug("SAF 替换异常：${it.message}") }
                        .getOrDefault(false)
                }
                if (!ok) appendLog("❌ SAF 通道未能完成操作（过程细节见 DEBUG 日志）")
            } catch (e: Exception) {
                appendLog("❌ SAF 目录授权处理失败：${e.message}")
            } finally {
                endOperation()
            }
        }
    }

    /**
     * 服务端 shell 通道替换（UserService 通道 EACCES 时的回退，MT 管理器同款机制）。
     * 四步全部经 [ShizukuShell.exec]/[ShizukuShell.pumpWrite] 在服务端上下文完成：
     * ① stat 读原 mtime ② cp 备份 + touch 保持 mtime ③ cat>目标（stdin 由应用泵入源内容）
     * ④ touch 校正目标 mtime。任一步失败抛异常，由调用方记录后放弃。
     */
    private suspend fun replaceViaShell(
        target: String,
        backup: String,
        sourcePath: String,
        sourceName: String
    ): Boolean {
        val t0 = SystemClock.elapsedRealtime()
        setBusy(true, "正在备份原文件…")
        appendDebug("Shizuku 服务端版本：${ShizukuShell.serverVersion()}（13.6.x 有 Android/data 可见性回归，Shizuku issue #1278）")
        // ① 原时间戳
        val (c1, o1) = ShizukuShell.exec("stat -c %Y ${ShizukuShell.quote(target)}")
        if (c1 != 0) {
            appendDebug("shell① stat → FAIL(code=$c1)：$o1")
            // 服务端通道也打不开：输出环境诊断包，一次性拿全
            // （uid/gids/SELinux/封锁边界），与 MT 的差异定位全靠它
            val aDataIdx = target.indexOf("/Android/data/")
            val aData = if (aDataIdx >= 0) {
                target.substring(0, aDataIdx + "/Android/data".length)
            } else {
                "/storage/emulated/0/Android/data"
            }
            val pkgDir = target.substringBeforeLast('/').substringBeforeLast('/')
            val (dc, dout) = ShizukuShell.exec(
                "echo --id--; id 2>&1; " +
                    "echo --groups--; cat /proc/self/groups 2>&1; " +
                    "echo --enforce--; getenforce 2>/dev/null; " +
                    "echo --adata--; ls -ld ${ShizukuShell.quote(aData)} 2>&1; " +
                    "echo --adata-ls--; ls ${ShizukuShell.quote(aData)} 2>&1 | head -3; " +
                    "echo --pkg--; ls -ld ${ShizukuShell.quote(pkgDir)} 2>&1; " +
                    "echo --parent--; ls ${ShizukuShell.quote(target.substringBeforeLast('/'))} 2>&1 | head -5; " +
                    "echo --stat--; stat ${ShizukuShell.quote(target)} 2>&1"
            )
            appendDebug("shell 通道环境诊断(code=$dc)：\n$dout")
            return false
        }
        val origMs = o1.trim().toLongOrNull()?.times(1000L)
        if (origMs == null) {
            appendDebug("shell① stat 输出异常：$o1")
            return false
        }
        appendDebug("shell① stat → OK（mtime=$origMs）")
        // ② 备份（cp -f 不保留 mtime，用 touch 显式补上）
        val (c2, o2) = ShizukuShell.exec(
            "cp -f ${ShizukuShell.quote(target)} ${ShizukuShell.quote(backup)} && " +
                "touch -d @${origMs / 1000} ${ShizukuShell.quote(backup)}"
        )
        if (c2 != 0) {
            appendDebug("shell② 备份 → FAIL：$o2")
            return false
        }
        appendDebug("shell② 备份 → OK（含原 mtime）")
        appendLog("🗄️ 已备份：$target -> $backup")
        // ③ 写入：服务端 cat > 目标，内容由应用经 stdin 泵入
        setBusy(true, "正在替换…")
        val copied = try {
            ShizukuShell.pumpWrite(target, File(sourcePath))
        } catch (e: Exception) {
            val rollback = runCatching {
                ShizukuShell.exec(
                    "cp -f ${ShizukuShell.quote(backup)} ${ShizukuShell.quote(target)} && " +
                        "touch -d @${origMs / 1000} ${ShizukuShell.quote(target)}"
                )
            }.getOrNull()
            if (rollback?.first == 0) {
                appendLog("❌ 服务端替换失败，已从备份恢复目标：${e.message}")
            } else {
                appendLog("❌ 服务端替换失败且自动恢复失败；请手动从备份还原：$backup")
                appendDebug("shell 回滚结果：${rollback?.second ?: "命令执行异常"}")
            }
            return false
        }
        appendDebug("shell③ 写入 → OK（$copied 字节）")
        appendLog("🔁 已替换：$target ← $sourceName")
        // ④ 校正目标时间戳
        setBusy(true, "正在校正时间戳…")
        val (timeCode, timeOutput) = ShizukuShell.exec(
            "touch -d @${origMs / 1000} ${ShizukuShell.quote(target)}"
        )
        if (timeCode == 0) {
            _state.value = _state.value.copy(targetLastModified = origMs)
            appendLog("✅ 替换完成，修改时间保持 ${sdf.format(Date(origMs))}")
        } else {
            appendLog("⚠️ 替换成功，但时间戳校正失败：$timeOutput")
        }
        appendDebug("shell 回退流程结束（总耗时 ${SystemClock.elapsedRealtime() - t0} ms）")
        return true
    }

    // ------------------------------------------------------------------
    // 一键还原：备份 → 目标，时间戳从备份文件取回
    // ------------------------------------------------------------------

    fun quickRestore(target: String, backup: String) {
        if (target.isBlank() || backup.isBlank()) {
            appendLog("⚠️ 目标或备份路径为空")
            return
        }
        val samePath = runCatching {
            File(target).canonicalPath == File(backup).canonicalPath
        }.getOrDefault(File(target).absolutePath == File(backup).absolutePath)
        if (samePath) {
            appendLog("❌ 目标路径与备份路径相同，已取消还原")
            return
        }
        if (!beginOperation("正在还原备份…")) return
        viewModelScope.launch {
            val t0 = SystemClock.elapsedRealtime()
            val rollbackFile = File(
                File(getApplication<Application>().cacheDir, "restore-rollback").apply { mkdirs() },
                "${UUID.randomUUID()}.tmp"
            )
            appendDebug("▶ 还原开始：目标=$target，备份=$backup")
            try {
                val ops = resolveOps()
                var t = SystemClock.elapsedRealtime()
                val exists = withContext(Dispatchers.IO) { ops.exists(backup) }
                if (!exists) {
                    val diag = runCatching { ops.diagnoseRead(backup) }
                        .getOrElse { "诊断失败：${it.message}" }
                    appendLog("❌ 备份文件不存在：$backup")
                    appendDebug("诊断：$diag")
                    return@launch
                }
                appendDebug("① 检查备份存在 → OK（${SystemClock.elapsedRealtime() - t} ms）")
                val bmt = withContext(Dispatchers.IO) { ops.getLastModified(backup) }

                // Direct in-place fallback may partially write on restricted directories.
                // Snapshot an existing target first so a failed restore can put it back.
                val targetExisted = withContext(Dispatchers.IO) { ops.exists(target) }
                var targetMtime = -1L
                var rollbackReady = false
                if (targetExisted) {
                    targetMtime = withContext(Dispatchers.IO) { ops.getLastModified(target) }
                    val snapshotError = withContext(Dispatchers.IO) {
                        runCatching {
                            val pfd = ops.openRead(target)
                                ?: throw IllegalStateException("当前目标不可读")
                            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                                rollbackFile.outputStream().use { output ->
                                    input.copyTo(output, 64 * 1024)
                                    output.flush()
                                }
                            }
                        }.exceptionOrNull()?.message
                    }
                    if (snapshotError != null) {
                        appendLog("❌ 无法安全还原：当前目标无法暂存，未修改目标。请切换 Root/Shizuku 后重试（$snapshotError）")
                        return@launch
                    }
                    rollbackReady = true
                }
                t = SystemClock.elapsedRealtime()
                val restoreErr = withContext(Dispatchers.IO) { ops.copyFile(backup, target) }
                appendDebug(
                    "② 还原写入 → ${if (restoreErr == null) "OK" else "FAIL"}" +
                        "（${SystemClock.elapsedRealtime() - t} ms）${restoreErr?.let { "：$it" } ?: ""}"
                )
                if (restoreErr != null) {
                    if (rollbackReady) {
                        val rollbackErr = withContext(Dispatchers.IO) {
                            runCatching {
                                val pfd = ParcelFileDescriptor.open(
                                    rollbackFile,
                                    ParcelFileDescriptor.MODE_READ_ONLY
                                )
                                ops.copyFromFd(pfd, target)
                            }.getOrElse { it.message ?: it.javaClass.simpleName }
                        }
                        if (rollbackErr == null) {
                            if (targetMtime >= 0) {
                                withContext(Dispatchers.IO) { ops.setLastModified(target, targetMtime) }
                            }
                            appendLog("❌ 还原失败，已恢复操作前的目标文件：$restoreErr")
                        } else {
                            appendLog("❌ 还原失败且无法恢复操作前的目标（$rollbackErr）；目标可能不完整，请检查：$target")
                        }
                    } else {
                        appendLog("❌ 还原失败：$restoreErr")
                    }
                    // Shizuku 引擎且备份 mtime 有效时，试服务端 shell 通道还原
                    if (_state.value.engineMode == EngineMode.SHIZUKU && bmt >= 0) {
                        appendDebug("UserService 通道写入失败，改试服务端 shell 通道…")
                        val (c, o) = runCatching {
                            ShizukuShell.exec(
                                "cp -f ${ShizukuShell.quote(backup)} ${ShizukuShell.quote(target)} && " +
                                    "touch -d @${bmt / 1000} ${ShizukuShell.quote(target)}"
                            )
                        }.onFailure { appendDebug("shell 还原失败：${it.message}") }
                            .getOrDefault(1 to "未执行")
                        if (c == 0) {
                            _state.value = _state.value.copy(targetLastModified = bmt)
                            appendLog("✅ 已还原（服务端 shell 通道）：$backup -> $target（时间戳 ${sdf.format(Date(bmt))}）")
                            return@launch
                        }
                        appendDebug("shell 还原失败：$o")
                    }
                    // SAF 回退：直接构造树 URI / 已持久化授权 → 否则转选择器授权
                    val tree = resolveSafTree(target)
                    if (tree != null) {
                        appendDebug("改试 SAF 通道还原…")
                        val safOk = runCatching { safRestore(SafPending(true, target, backup, "", ""), tree) }
                            .getOrDefault(false)
                        if (safOk) return@launch
                    }
                    if (isAndroidDataOrObbPath(target)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            appendLog("❌ Android 11 及以上禁止通过系统目录选择器授权其他应用的 Android/data 或 Android/obb；请使用 Root 或 Shizuku")
                            return@launch
                        }
                        appendLog("💡 将请求 SAF 目录授权：请在弹出的选择器中选中目标文件所在目录")
                        val req = SafPending(true, target, backup, "", "")
                        safInFlight = req
                        _state.update { it.copy(safPending = req) }
                        return@launch
                    }
                    return@launch
                }
                if (bmt >= 0) {
                    t = SystemClock.elapsedRealtime()
                    val timeOk = withContext(Dispatchers.IO) { ops.setLastModified(target, bmt) }
                    appendDebug(
                        "③ 校正时间戳 → ${if (timeOk) "OK" else "FAIL"}" +
                            "（${SystemClock.elapsedRealtime() - t} ms）"
                    )
                    if (!timeOk) {
                        appendLog("✅ 已还原：$backup -> $target（时间戳校正被拒，内容已还原）")
                        return@launch
                    }
                    _state.value = _state.value.copy(targetLastModified = bmt)
                    appendLog("✅ 已还原：$backup -> $target（时间戳 ${sdf.format(Date(bmt))}）")
                } else {
                    appendLog("✅ 已还原：$backup -> $target（备份无有效时间戳，未校正）")
                }
            } catch (e: Exception) {
                appendLog("❌ 还原失败：${e.message}")
            } finally {
                runCatching { if (rollbackFile.exists()) rollbackFile.delete() }
                appendDebug("还原流程结束（总耗时 ${SystemClock.elapsedRealtime() - t0} ms）")
                endOperation(keepSafReservation = _state.value.safPending != null)
            }
        }
    }

    /** 表单当前配置的一键还原 */
    fun quickRestoreFromForm() {
        val s = _state.value
        quickRestore(s.targetPath, s.backupPath)
    }

    /** 预设卡的一键还原 */
    fun quickRestorePreset(preset: QuickPreset) {
        quickRestore(preset.targetPath, preset.backupPath)
    }

    // ------------------------------------------------------------------
    // 快捷预设管理
    // ------------------------------------------------------------------

    fun saveQuickPreset(name: String) {
        val s = _state.value
        val source = s.importedLocalPath
        if (s.targetPath.isBlank() || s.backupPath.isBlank()) {
            appendLog("⚠️ 请先填写目标与备份路径")
            return
        }
        if (source == null) {
            appendLog("⚠️ 请先导入替换文件")
            return
        }
        val preset = QuickPreset(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "快捷 ${s.presets.size + 1}" },
            targetPath = s.targetPath,
            backupPath = s.backupPath,
            sourcePath = source,
            sourceName = s.importedFileName ?: File(source).name
        )
        _state.value = _state.value.copy(presets = _state.value.presets + preset)
        persistPresets()
        appendLog("⭐ 已保存快捷方式：${preset.name}")
    }

    fun deleteQuickPreset(id: String) {
        _state.value = _state.value.copy(presets = _state.value.presets.filter { it.id != id })
        persistPresets()
    }

    /** 把某个预设的替换源更新为当前导入的文件（mod 文件出新版时用） */
    fun rebindPresetSource(id: String) {
        val s = _state.value
        val source = s.importedLocalPath
        if (source == null) {
            appendLog("⚠️ 请先导入文件，再更新快捷方式的替换源")
            return
        }
        _state.value = _state.value.copy(
            presets = _state.value.presets.map {
                if (it.id == id) {
                    it.copy(sourcePath = source, sourceName = s.importedFileName ?: File(source).name)
                } else it
            }
        )
        persistPresets()
        appendLog("🔄 已将快捷方式的替换源更新为：${s.importedFileName}")
    }

    fun loadPresetToForm(preset: QuickPreset) {
        _state.value = _state.value.copy(
            targetPath = preset.targetPath,
            backupPath = preset.backupPath,
            importedFileName = preset.sourceName,
            importedLocalPath = preset.sourcePath
        )
        appendLog("📋 已加载快捷方式到表单：${preset.name}")
    }

    private fun persistPresets() {
        QuickPresetStore.save(getApplication(), _state.value.presets)
    }

    override fun onCleared() {
        super.onCleared()
        ShizukuManager.unbind()
        RootManager.stop(getApplication())
    }
}
