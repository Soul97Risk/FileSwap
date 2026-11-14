/*
 * Copyright 2026 kndnb666
 * SPDX-License-Identifier: Apache-2.0
 */

package com.fileswap.app

import android.os.ParcelFileDescriptor

/**
 * 该类由 Shizuku 以独立进程启动，运行在 shell UID (2000) 下。
 * 必须提供无参构造函数，Shizuku 通过反射创建实例。
 * 不要在这里持有 Context —— UserService 进程默认没有完整的 Application 环境。
 * 实际逻辑在 [FileOpsImpl]，与 RootFileService 共享同一份实现。
 */
class FileService : IFileService.Stub() {

    override fun exists(path: String): Boolean = FileOpsImpl.exists(path)

    override fun getLastModified(path: String): Long = FileOpsImpl.getLastModified(path)

    override fun setLastModified(path: String, timeMillis: Long): Boolean =
        FileOpsImpl.setLastModified(path, timeMillis)

    override fun copyFile(srcPath: String, dstPath: String): String? =
        FileOpsImpl.copyFile(srcPath, dstPath)

    override fun openRead(path: String): ParcelFileDescriptor? = FileOpsImpl.openRead(path)

    override fun listDir(path: String): Array<String>? = FileOpsImpl.listDir(path)

    override fun copyFromFd(src: ParcelFileDescriptor, dstPath: String): String? =
        FileOpsImpl.copyFromFd(src, dstPath)

    override fun diagnoseRead(path: String): String = FileOpsImpl.diagnoseRead(path)

    override fun destroy() {
        // 不做任何强杀：exit(0) 会在 Binder 线程里直接掀掉进程，
        // 正在拷贝的调用会被腰斩成半截文件。Shizuku 自己在 unbind 后回收进程。
    }
}
