/*
 * Copyright 2026 kndnb666
 * SPDX-License-Identifier: Apache-2.0
 */

package com.fileswap.app

import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.topjohnwu.superuser.ipc.RootService

/**
 * Root 引擎的远程文件服务，由 libsu 以 root UID (0) 在独立进程中启动。
 * libsu 6.x 的服务类需继承 [RootService]，在 onBind 里返回 AIDL Stub；
 * 实际逻辑在 [FileOpsImpl]，与 Shizuku 引擎的 FileService 共享同一份实现。
 */
class RootFileService : RootService() {

    override fun onBind(intent: Intent): IBinder {
        return object : IRootFileService.Stub() {
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
        }
    }
}
