package com.fileswap.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/**
 * 封装 Shizuku 权限申请 + UserService(以 shell 权限运行的远程文件操作服务) 的绑定逻辑。
 * 使用前提：设备已安装并启动 Shizuku（通过 ADB 或已 root 设备启动均可）。
 */
object ShizukuManager {

    private const val REQUEST_CODE = 1001

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    /**
     * UserService 绑定超时。90 秒而非 20 秒：部分 ROM（实测 vivo 安卓 11）
     * 首次冷启动 UserService 可达约 58 秒，20 秒会在服务起来前连环超时。
     */
    private const val BIND_TIMEOUT_MS = 90_000L

    /** 绑定串行化锁：防止并发调用重复 bind */
    private val bindMutex = Mutex()

    private var service: IFileService? = null

    /** 在途绑定：所有等待者共享同一个 deferred，服务上线一起成功 */
    private var pending: CompletableDeferred<IFileService>? = null

    private val args by lazy {
        Shizuku.UserServiceArgs(
            // 用 BuildConfig.APPLICATION_ID 而非硬编码包名：将来再改 applicationId 不会忘改这里
            ComponentName(
                BuildConfig.APPLICATION_ID,
                FileService::class.java.name
            )
        )
            .daemon(false)
            .processNameSuffix("file_service")
            .debuggable(false)
            // AIDL 增加方法时必须递增：Shizuku 靠版本号决定是否重启 UserService
            // 进程，不升级会继续用旧进程，新方法直接 NoSuchMethod。
            // v3：copyFile 改为返回错误原因 + 新增 openRead（fd 交回应用落盘）
            // v4：新增 copyFromFd（替换源走 fd 传递，shell 读不了应用私有目录）
            //     与 diagnoseRead（失败原因诊断）
            .version(4)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = if (binder != null && binder.pingBinder()) {
                IFileService.Stub.asInterface(binder)
            } else null
            service = s
            val d = pending
            if (s != null) {
                d?.complete(s)
            } else {
                d?.completeExceptionally(IllegalStateException("Shizuku 服务连接失败"))
            }
            pending = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            // 同时清掉在途信号：否则下次 await 会拿到上一个已完成的 deferred，
            // 直接返回一个已死的 binder
            pending = null
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Shizuku 服务端的 uid：0 = root 启动，2000 = ADB / 无线调试启动（shell）。
     * UserService 与服务端同身份，这决定了文件操作实际拥有的权限。失败返回 -1。
     */
    fun serverUid(): Int {
        return try {
            Shizuku.getUid()
        } catch (e: Throwable) {
            -1
        }
    }

    /**
     * 已安装的 Shizuku 应用版本名（如 `13.6.0.r1086`）。
     *
     * 与 `serverVersion()`（`IShizukuService.getVersion()`）的区别：后者是
     * **协议号**（当前恒为 13），看不出发行版本；而 13.6.0 有 Android/data
     * 可见性与 UserService 启动崩溃两组已知问题（Shizuku #1278 / #1171），
     * 诊断必须能分辨。manifest 已声明 `<queries>` 包可见性，故可查询。
     * 未安装或查询失败返回空串。
     */
    fun appVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    SHIZUKU_PACKAGE,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(SHIZUKU_PACKAGE, 0)
            }
            info.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (!isShizukuAvailable()) {
            onResult(false)
            return
        }
        if (hasPermission()) {
            onResult(true)
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    onResult(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST_CODE)
    }

    /**
     * 获取文件服务。
     *
     * 共享等待模式（vivo 上 UserService 冷启动实测可达约 58 秒的教训）：
     * 若已有绑定在途，后到的调用**挂到同一个 deferred 上**而不是各自排队重绑——
     * 服务一旦上线，所有等待者一起成功。此前的「每个调用各自 20s 串行等待」
     * 会把一次冷启动放大成连环超时（实测 5 连超时 + 16 个排队调用）。
     */
    suspend fun getFileService(): IFileService {
        service?.let { return it }
        val deferred: CompletableDeferred<IFileService> = bindMutex.withLock {
            // 双检：排队期间服务可能已被前一个调用者绑好
            service?.let { return it }
            pending ?: CompletableDeferred<IFileService>().also {
                pending = it
                withContext(Dispatchers.Main) {
                    Shizuku.bindUserService(args, connection)
                }
            }
        }
        return try {
            val svc = withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
            // 防「已完成的 deferred 交回死 binder」：拿到手再 ping 一次
            if (!svc.asBinder().pingBinder()) {
                throw IllegalStateException("Shizuku 服务已断开，请重试")
            }
            svc
        } catch (e: TimeoutCancellationException) {
            // 刻意保留 pending：绑定可能仍在途，后到的调用继续共享等待
            throw IllegalStateException(
                "Shizuku 服务绑定超时（${BIND_TIMEOUT_MS / 1000} 秒）——请确认 Shizuku 正在运行；" +
                    "部分 ROM 首次启动服务较慢，稍等片刻重试即可"
            )
        }
    }

    fun unbind() {
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (e: Throwable) {
            // ignore
        }
        service = null
        pending = null
    }
}
