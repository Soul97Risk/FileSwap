package com.fileswap.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Root 引擎管理：libsu 获取 root shell（首次触发 KSU/Magisk 授权弹窗），
 * 并把 [RootFileService] 绑定为 root 进程中的远程服务。
 * Root 能力强于 shell：可读写其他应用的私有数据目录等。
 *
 * 关键背景（libsu 6.0 源码核实，KSU「已授权但应用无权限」的根因）：
 * libsu 的 su 探测会**静默降级**——首次 su 握手失败/超时（默认 20 秒，典型场景：
 * KSU 授权弹窗未及时确认、或首次触发时还未授权）会缓存一个非 root 主 shell，
 * 之后即使在管理器里授权，缓存的非 root shell 也不会重建，
 * `Shell.getShell()` 永远拿到非 root，且 `RootService.bind` 在
 * confirmed-non-root 时**静默返回**（连接回调永远不来）。
 * 解法：`Shell.Builder.create().build()` 显式重建——build 成功会把新 shell
 * 设为主缓存（setCached），拿到 root 即自愈，无需重启应用。
 */
object RootManager {

    /**
     * 等待 Root 服务绑定的超时；libsu 在 confirmed-non-root 时静默返回，不加超时会永远挂起。
     * 90 秒：vivo 安卓 11 冷启动实测 58~90+ 秒，20/60 秒都会在服务起来前连环超时。
     */
    private const val BIND_TIMEOUT_MS = 90_000L

    /** 绑定串行化锁：防止并发调用重复 bind */
    private val bindMutex = Mutex()

    private var service: IRootFileService? = null

    /** 在途绑定：所有等待者共享同一个 deferred，服务上线一起成功 */
    private var pending: CompletableDeferred<IRootFileService>? = null

    /**
     * 显式重建主 shell。FLAG_MOUNT_MASTER 进 Magisk 全局命名空间（跨应用目录需要），
     * su 不支持该参数时 libsu 自动回退普通 su；握手超时放宽到 30 秒，
     * 给 KSU/Magisk 授权弹窗留足确认时间。
     */
    private fun buildRootShell(): Shell =
        Shell.Builder.create()
            .setFlags(Shell.FLAG_MOUNT_MASTER)
            .setTimeout(30L)
            .build()

    /** root 是否真正可用。
     *  缓存 shell 已确认 root 时直接信任（避免 AUTO 模式每次操作都 fork 新 su 进程）；
     *  未确认或非 root 时才 fresh build 探测（自愈被污染的缓存语义保留）。
     *  只捕获 Exception：Throwable 会把 OOM 之类也当成「未授权」静默降级。 */
    suspend fun isRootGranted(): Boolean = try {
        withContext(Dispatchers.IO) {
            if (Shell.isAppGrantedRoot() == true) {
                true
            } else {
                buildRootShell().isRoot()
            }
        }
    } catch (e: Exception) {
        false
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = if (binder != null && binder.pingBinder()) {
                IRootFileService.Stub.asInterface(binder)
            } else null
            service = s
            if (s != null) {
                pending?.complete(s)
            } else {
                pending?.completeExceptionally(IllegalStateException("Root 服务连接失败"))
            }
            pending = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            // 清掉在途信号，避免后到者 await 到已死的 binder
            pending = null
        }
    }

    suspend fun getService(context: Context): IRootFileService {
        service?.let { return it }
        // 共享等待：已有绑定在途时，后到的调用挂到同一个 deferred 上
        // （su 探测只由创建绑定者的那一次执行），服务上线所有等待者一起成功
        val deferred: CompletableDeferred<IRootFileService> = bindMutex.withLock {
            // 双检：排队期间服务可能已被前一个调用者绑好
            service?.let { return it }
            pending ?: run {
                withContext(Dispatchers.IO) {
                    val shell = buildRootShell()
                    if (!shell.isRoot()) {
                        // 到这里说明 su 握手失败/被拒；fresh build 已刷新缓存状态，
                        // 用户补授权后重试本方法即可自愈，无需重启应用
                        throw IllegalStateException(
                            "su 未取得 root：请在 KSU/Magisk 中确认已授权本应用；" +
                                "若刚授权请直接重试一次"
                        )
                    }
                }
                val d = CompletableDeferred<IRootFileService>()
                pending = d
                // libsu 6.0 硬性要求 bind 在主线程调用（RootServiceManager.enforceMainThread
                // 会抛 IllegalStateException），而 resolveOps 可能运行在 Default 线程
                // （browseInternal 的后台排序块），因此必须显式切回主线程
                withContext(Dispatchers.Main) {
                    RootService.bind(Intent(context, RootFileService::class.java), connection)
                }
                d
            }
        }
        return try {
            val svc = withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
            // 防「已完成的 deferred 交回死 binder」：拿到手再 ping 一次
            if (!svc.asBinder().pingBinder()) {
                throw IllegalStateException("Root 服务已断开，请重试")
            }
            svc
        } catch (e: TimeoutCancellationException) {
            // 刻意保留 pending：绑定可能仍在途，后到的调用继续共享等待
            throw IllegalStateException(
                "Root 服务启动超时（${BIND_TIMEOUT_MS / 1000} 秒）——部分 ROM 首次启动较慢，稍等重试即可"
            )
        }
    }

    fun stop(context: Context) {
        service = null
        pending = null
        try {
            RootService.stop(Intent(context, RootFileService::class.java))
        } catch (e: Throwable) {
            // ignore
        }
    }
}
