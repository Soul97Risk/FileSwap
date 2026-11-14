/*
 * Copyright 2026 kndnb666
 * SPDX-License-Identifier: Apache-2.0
 */

package com.fileswap.app

import android.annotation.TargetApi
import android.os.Build
import android.os.Process
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

/**
 * 文件操作的纯本地实现，供两条引擎共享：
 * - FileService（Shizuku UserService；ADB 启动为 shell uid 2000，root 启动为 uid 0）
 * - RootFileService（libsu RootService，root UID 0）
 *
 * 注意：Shizuku 是 root 启动还是 ADB 启动，决定了这里的代码以什么身份运行。
 * root 下几乎不受权限限制，shell 下受限很多——所以所有失败信息都带上 uid，
 * 方便用户反馈日志时一眼分清环境。
 */
internal object FileOpsImpl {

    /** 大文件（游戏资源可达数十 MB）导入用大缓冲，比默认 8KB 快数倍 */
    private const val COPY_BUFFER_SIZE = 64 * 1024

    private fun uidTag(): String = "uid=${Process.myUid()}"

    /** 异常转成可读原因，用于把真机失败细节带回应用侧日志 */
    private fun describe(e: Throwable): String {
        val m = e.message?.takeIf { it.isNotBlank() }
        val base = if (m != null) "${e.javaClass.simpleName}: $m" else e.javaClass.simpleName
        return "$base（${uidTag()}）"
    }

    /**
     * File.exists() 在「没有目录遍历权限」时也返回 false，和「真的不存在」无法区分。
     * 这里把最近一个可见的上级目录带出来，用户/开发者据此判断是权限还是路径问题。
     */
    private fun describeMissing(f: File): String {
        var p: File? = f.parentFile
        while (p != null && !p.exists()) p = p.parentFile
        return "源文件不存在，或当前身份无权访问：${f.path}（${uidTag()}，最近可见上级：${p?.path ?: "无"}）"
    }

    fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: Exception) {
            false
        }
    }

    fun getLastModified(path: String): Long {
        return try {
            val f = File(path)
            if (!f.exists()) -1L else f.lastModified()
        } catch (e: Exception) {
            -1L
        }
    }

    fun setLastModified(path: String, timeMillis: Long): Boolean {
        return try {
            File(path).setLastModified(timeMillis)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 服务身份可直接读到的源文件 → 目标。成功返回 null，失败返回可读原因。
     * 源在应用私有目录时不要用这个（shell 读不了），改用 [copyFromFd]。
     */
    fun copyFile(srcPath: String, dstPath: String): String? {
        val src = File(srcPath)
        if (!src.exists()) return describeMissing(src)
        if (!src.canRead()) return "源文件无读取权限：$srcPath（${uidTag()}）"
        return writeTo({ FileInputStream(src) }, File(dstPath))
    }

    /**
     * 由应用进程打开好的 fd → 目标。
     * fd 是应用用自己的 UID 打开的，所以源在应用私有目录也没问题；
     * 服务只需要有目标路径的写权限。
     */
    fun copyFromFd(src: ParcelFileDescriptor, dstPath: String): String? {
        // 两段策略可能需要读两遍：每次读之前把 fd 的偏移复位到 0
        val opener = {
            val d = src.dup()
            Os.lseek(d.fileDescriptor, 0, OsConstants.SEEK_SET)
            ParcelFileDescriptor.AutoCloseInputStream(d) as InputStream
        }
        return try {
            writeTo(opener, File(dstPath))
        } finally {
            runCatching { src.close() }
        }
    }

    /**
     * 写入目标：成功返回 null，失败返回可读原因。
     *
     * 两段策略：
     * 1. 首选「同目录临时文件 + 原子 rename」——写失败不会破坏原目标。
     * 2. 临时文件建不出来时（典型：`/sdcard/Android/data/<pkg>/` 这类目录，
     *    本 UID 无法在目录内新建条目）回退为**直接就地覆盖**。此时牺牲原子性，
     *    上层已先做过备份，失败会走回滚。
     *
     * 传输用固定缓冲的流拷贝，**刻意不用** `FileChannel.transferTo`：它在
     * Android 上会走 sendfile/mmap 路径，FUSE（/sdcard）下可能返回 0 或抛异常，
     * 而 `while (pos < total) pos += transferTo(...)` 遇到 0 返回会死循环。
     */
    private fun writeTo(opener: () -> InputStream, dst: File): String? {
        try {
            dst.parentFile?.mkdirs()
        } catch (e: Exception) {
            return "无法创建目标目录（${dst.parentFile}）：${describe(e)}"
        }

        // 临时文件必须与目标同目录：跨设备 rename 会退化为失败
        val tmp = File(dst.parentFile, "." + dst.name + ".fswap-tmp-" + System.nanoTime())
        val output = try {
            FileOutputStream(tmp)
        } catch (e: Exception) {
            if (isPermissionDenied(e)) {
                // 受限目录可能允许覆盖文件内容，却禁止新建目录项。
                // 只在创建临时文件明确遭遇 EACCES/EPERM 时才使用原位写入。
                return try {
                    copyStreams(opener, dst)
                    null
                } catch (direct: Exception) {
                    "临时文件创建被拒：${describe(e)}；直接覆盖失败：${describe(direct)}"
                }
            }
            return "无法创建临时文件：${describe(e)}"
        }

        return try {
            output.use { out -> copyStreams(opener, out) }
            // POSIX rename 原子替换已存在文件；失败时保留原目标，不先删目标重试。
            if (tmp.renameTo(dst)) null
            else "临时文件覆盖失败（${tmp.name} → ${dst.name}，${uidTag()}）"
        } catch (e: Exception) {
            "写临时文件失败：${describe(e)}"
        } finally {
            // 成功路径 rename 后 tmp 已不存在；失败路径清理残留
            runCatching { if (tmp.exists()) tmp.delete() }
        }
    }

    private fun isPermissionDenied(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty().lowercase()
            if (message.contains("eacces") || message.contains("eperm") ||
                message.contains("permission denied") || message.contains("operation not permitted")
            ) return true
            current = current.cause
        }
        return false
    }

    /** Fixed-buffer copy. fsync is best effort because some FUSE filesystems reject it. */
    private fun copyStreams(opener: () -> InputStream, dst: File) {
        FileOutputStream(dst).use { output -> copyStreams(opener, output) }
    }

    /** Copy into an already-open stream so temp-file creation errors are distinct from copy errors. */
    private fun copyStreams(opener: () -> InputStream, output: FileOutputStream) {
        opener().use { input ->
            val buf = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) output.write(buf, 0, n)
            }
            output.flush()
            runCatching { output.fd.sync() }
        }
    }

    /**
     * 只读打开文件并把 fd 交回应用进程。
     * 失败只能返回 null（AIDL 无法带原因），原因请用 [diagnoseRead] 单独取。
     */
    fun openRead(path: String): ParcelFileDescriptor? {
        return try {
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            null
        }
    }

    /** 以服务身份探测 path 的可读性，返回一行可读结果（含封锁边界 / gids / SELinux） */
    fun diagnoseRead(path: String): String {
        val f = File(path)
        val attempt = try {
            FileInputStream(f).use { }
            "open成功"
        } catch (e: Exception) {
            describe(e)
        }
        val parent = f.parentFile
        // 自下而上找最深的可访问祖先：一次定位封锁发生在路径的哪一层
        // （文件级？包目录级？Android/data 整体？——三种情况对策完全不同）
        var boundary = "/"
        var p: File? = parent
        while (p != null) {
            if (p.canRead() || p.canExecute()) {
                boundary = p.path
                break
            }
            p = p.parentFile
        }
        val gids = try {
            // /proc/self/groups：每行一个 gid，比 Os API 兼容性更好
            File("/proc/self/groups").readText().trim()
                .lines().filter { it.isNotBlank() }.joinToString(",") { g ->
                    val id = g.trim().toIntOrNull()
                    if (id == null) g.trim() else g.trim() + when (id) {
                        1015 -> "(sdcard_rw)"
                        1023 -> "(media_rw)"
                        1024 -> "(sdcard_r)"
                        1077 -> "(ext_obb_rw)"
                        1078 -> "(ext_data_rw)"
                        else -> ""
                    }
                }
        } catch (e: Exception) {
            "?"
        }
        val selinux = try {
            File("/proc/self/attr/current").readText().trim()
        } catch (e: Exception) {
            "?"
        }
        return "open=$attempt；exists=${f.exists()}，canRead=${f.canRead()}，" +
            "上级可读=${parent?.canRead()}，上级可遍历=${parent?.canExecute()}，" +
            "最深可访问目录=$boundary，gids=[$gids]，selinux=$selinux（${uidTag()}）"
    }

    fun listDir(path: String): Array<String>? {
        return try {
            // API 26+ 走 NIO：readAttributes 一次拿全 类型/大小/mtime，
            // 每个条目 1 次 stat；java.io.File 路径每个文件要 3 次
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                listDirNio(path)
            } else {
                listDirLegacy(path)
            }
        } catch (e: Exception) {
            null
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun listDirNio(path: String): Array<String>? {
        // 目录不存在 / 不是目录 / 无权访问时 newDirectoryStream 抛 IOException → 上层转 null
        // 注意 DirectoryStream 只能迭代一次，不能用 count() 预估容量
        Files.newDirectoryStream(Paths.get(path)).use { stream ->
            val out = ArrayList<String>()
            for (p in stream) {
                // 单个条目读属性失败（坏符号链接、无权 stat 等）只跳过该条目，
                // 不能让整个目录变成「无权读取」
                val attrs = try {
                    Files.readAttributes(p, BasicFileAttributes::class.java)
                } catch (e: Exception) {
                    continue
                }
                if (attrs.isDirectory) {
                    out.add("d|" + p.fileName.toString())
                } else {
                    out.add("f|" + p.fileName.toString() + "|" + attrs.size() + "|" + attrs.lastModifiedTime().toMillis())
                }
            }
            return out.toTypedArray()
        }
    }

    private fun listDirLegacy(path: String): Array<String>? {
        // listFiles 对「不是目录 / 不存在 / 无权读取」都返回 null，与 AIDL 约定一致
        val children = File(path).listFiles() ?: return null
        return children.map { c ->
            if (c.isDirectory) {
                "d|" + c.name
            } else {
                "f|" + c.name + "|" + c.length() + "|" + c.lastModified()
            }
        }.toTypedArray()
    }
}
