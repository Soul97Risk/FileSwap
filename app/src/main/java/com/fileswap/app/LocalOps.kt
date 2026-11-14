package com.fileswap.app

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor

/**
 * 本地引擎（免 Shizuku / Root）的权限检查。
 *
 * 权限边界决定了它能做什么：
 * - **安卓 10**：仅当系统实际启用了 legacy storage 且授予
 *   `WRITE_EXTERNAL_STORAGE` 时，才认为具备旧式共享存储访问能力。对于 targetSdk 30+
 *   应用，单独授予该权限不能证明任意路径都可读写。
 * - **安卓 9 及以下**：`WRITE_EXTERNAL_STORAGE` 运行时权限可访问共享存储，
 *   包括当时尚未受限制的其他应用 `Android/data`。
 * - **安卓 11+**：需要 `MANAGE_EXTERNAL_STORAGE`（所有文件访问）。官方文档
 *   明确排除其他应用的 `Android/data` 与 `Android/obb`——QQ 接收目录、
 *   游戏数据目录这类目标在此模式下仍需 Root/Shizuku。
 */
object LocalStorage {

    /** 本地引擎当前是否已具备所需权限（纯本地检查，不起任何进程） */
    fun isReady(context: Context): Boolean =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Environment.isExternalStorageManager()

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                Environment.isExternalStorageLegacy() && hasWritePermission(context)

            else -> hasWritePermission(context)
        }

    private fun hasWritePermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * FileOps 的进程内实现：零 IPC，直接委托给与两个远程服务共享的 [FileOpsImpl]。
 *
 * 与 Shizuku/Root 引擎执行完全相同的复制代码，保证行为一致；
 * 差别只在权限身份——这里就是应用自己的 UID。
 */
class LocalOps : FileOps {
    override fun exists(path: String) = FileOpsImpl.exists(path)
    override fun getLastModified(path: String) = FileOpsImpl.getLastModified(path)
    override fun setLastModified(path: String, timeMillis: Long) = FileOpsImpl.setLastModified(path, timeMillis)
    override fun copyFile(srcPath: String, dstPath: String) = FileOpsImpl.copyFile(srcPath, dstPath)
    override fun openRead(path: String): ParcelFileDescriptor? = FileOpsImpl.openRead(path)
    override fun listDir(path: String) = FileOpsImpl.listDir(path)
    override fun copyFromFd(src: ParcelFileDescriptor, dstPath: String) = FileOpsImpl.copyFromFd(src, dstPath)
    override fun diagnoseRead(path: String): String = FileOpsImpl.diagnoseRead(path)
}
