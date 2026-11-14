package com.fileswap.app

import android.os.ParcelFileDescriptor

/**
 * 引擎无关的文件操作门面。ViewModel 只面向此接口，
 * 底层按当前引擎模式绑定 Shizuku（shell UID）或 Root（root UID）服务。
 */
interface FileOps {
    fun exists(path: String): Boolean
    fun getLastModified(path: String): Long
    fun setLastModified(path: String, timeMillis: Long): Boolean

    /** 复制文件：成功返回 null，失败返回可读原因 */
    fun copyFile(srcPath: String, dstPath: String): String?

    /**
     * 只读打开文件，把 fd 交回应用进程。
     *
     * 导入必须走这条路径：应用私有目录（`/data/user/0/<pkg>/`）对 shell UID
     * 不可写（AOSP shell 域没有 app_data_file 权限），但应用自己可以；
     * 因此服务只负责「打开」，落盘交给应用完成。
     */
    fun openRead(path: String): ParcelFileDescriptor?

    fun listDir(path: String): Array<String>?

    /**
     * 由应用进程打开源文件、把 fd 交给服务写入 [dstPath]。
     * 替换源在应用私有目录时必须走这条：shell(2000) 读不了私有目录。
     */
    fun copyFromFd(src: ParcelFileDescriptor, dstPath: String): String?

    /** 以服务身份探测 [path] 的可读性，返回可读原因（含 uid） */
    fun diagnoseRead(path: String): String
}

/** Shizuku 引擎适配（shell UID 2000） */
class ShizukuOps(private val svc: IFileService) : FileOps {
    override fun exists(path: String) = svc.exists(path)
    override fun getLastModified(path: String) = svc.getLastModified(path)
    override fun setLastModified(path: String, timeMillis: Long) = svc.setLastModified(path, timeMillis)
    override fun copyFile(srcPath: String, dstPath: String) = svc.copyFile(srcPath, dstPath)
    override fun openRead(path: String): ParcelFileDescriptor? = svc.openRead(path)
    override fun listDir(path: String) = svc.listDir(path)
    override fun copyFromFd(src: ParcelFileDescriptor, dstPath: String) = svc.copyFromFd(src, dstPath)
    override fun diagnoseRead(path: String): String = svc.diagnoseRead(path)
}

/** Root 引擎适配（root UID 0） */
class RootOps(private val svc: IRootFileService) : FileOps {
    override fun exists(path: String) = svc.exists(path)
    override fun getLastModified(path: String) = svc.getLastModified(path)
    override fun setLastModified(path: String, timeMillis: Long) = svc.setLastModified(path, timeMillis)
    override fun copyFile(srcPath: String, dstPath: String) = svc.copyFile(srcPath, dstPath)
    override fun openRead(path: String): ParcelFileDescriptor? = svc.openRead(path)
    override fun listDir(path: String) = svc.listDir(path)
    override fun copyFromFd(src: ParcelFileDescriptor, dstPath: String) = svc.copyFromFd(src, dstPath)
    override fun diagnoseRead(path: String): String = svc.diagnoseRead(path)
}
