package com.fileswap.app

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.File
import java.io.FileInputStream

/**
 * Shizuku「服务端 shell」通道：经 [IShizukuService.newProcess] 在 **Shizuku 服务端**
 * （adb shell 上下文）执行命令，而不是在我们的 UserService 子进程里。
 *
 * 为什么存在它：vivo（安卓 11）上 UserService 子进程访问其他应用的
 * Android/data 会 EACCES（连目录遍历都被拒），而同为 Shizuku 驱动的
 * MT 管理器等服务端通道可以。两条通道身份同为 shell(2000)，但进程上下文
 * 不同——UserService 路径 EACCES 时自动落到这里，即「MT 同款机制」。
 *
 * 安全：所有路径必须经 [quote] 单引号转义后再拼进命令，杜绝命令注入。
 */
object ShizukuShell {

    private fun service(): IShizukuService {
        val binder = Shizuku.getBinder()
        if (binder == null || !binder.pingBinder()) {
            throw IllegalStateException("Shizuku 未运行")
        }
        return IShizukuService.Stub.asInterface(ShizukuBinderWrapper(binder))
    }

    /** Shizuku 服务端版本（诊断 Shizuku 13.6.x 的 Android/data 回归用，issue #1278） */
    fun serverVersion(): Int = runCatching { service().version }.getOrDefault(-1)

    /** 单引号转义：' → '\''（POSIX shell 中唯一可靠的字面量方式） */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * 在服务端执行 `sh -c [script]`，返回 (exitCode, stdout+stderr)。
     * 两个流都必须读：stat/cp 的报错走 stderr，只读 stdout 会把失败原因吞成空串
     * （第一版实测教训）。顺序读 stdout→stderr：本通道命令输出量小（stat/cp/touch），
     * 不会撑爆 64KB 管道缓冲，无死锁风险。
     */
    fun exec(script: String): Pair<Int, String> {
        val rp = service().newProcess(arrayOf("/system/bin/sh", "-c", script), null, "/")
        val out = StringBuilder()
        val err = StringBuilder()
        ParcelFileDescriptor.AutoCloseInputStream(rp.inputStream).use { ins ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                out.append(String(buf, 0, n))
            }
        }
        ParcelFileDescriptor.AutoCloseInputStream(rp.errorStream).use { ins ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                err.append(String(buf, 0, n))
            }
        }
        val code = rp.waitFor()
        var merged = out.toString().trim()
        if (err.isNotBlank()) {
            if (merged.isNotEmpty()) merged += "\n"
            merged += "[stderr] " + err.toString().trim()
        }
        return code to merged
    }

    /**
     * 服务端列目录，返回与 [FileOpsImpl.listDir] 相同的编码（d|名称 / f|名称|大小|mtime），
     * 供 [MainViewModel.parseDirEntry] 直接复用。进入不了目录返回 null（exit 9）。
     * 用 ./ 前缀 stat 规避文件名以 - 开头被当成选项的问题。
     */
    fun listDir(path: String): Array<String>? {
        // 注意：脚本是 shell 语法，$f 等变量在 Kotlin 里必须用 \$ 转义或拼接，
        // 否则会被 Kotlin 字符串模板吃掉（实测编译报 Unresolved 'f'）
        val script = "cd ${quote(path)} 2>/dev/null || exit 9\n" +
            "for f in * .[!.]* ..?*; do\n" +
            "  [ -e \"./\$f\" ] || continue\n" +
            "  if [ -d \"./\$f\" ]; then\n" +
            "    printf 'd|%s\n' \"\$f\"\n" +
            "  else\n" +
            "    printf 'f|%s|%s|%s\n' \"\$f\" \\\n" +
            "      \"\$(stat -c %s \"./\$f\" 2>/dev/null)\" \"\$(stat -c %Y \"./\$f\" 2>/dev/null)\"\n" +
            "  fi\n" +
            "done\n"
        return try {
            val (code, out) = exec(script)
            if (code != 0) null else out.lines().filter { it.isNotEmpty() }.toTypedArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 把本地文件 [src] 的内容经 stdin 泵入服务端命令的写入端。
     * 配合 `cat > '目标'` 使用：替换源在应用私有目录（shell 读不了），
     * 但目标在服务端可写——内容方向恰好是「应用 → 服务端」。
     * 返回写入字节数；命令退出码非 0 时抛异常。
     */
    fun pumpWrite(targetPath: String, src: File): Long {
        val rp = service().newProcess(
            arrayOf("/system/bin/sh", "-c", "cat > ${quote(targetPath)}"),
            null, "/"
        )
        var copied = 0L
        try {
            FileInputStream(src).use { ins ->
                ParcelFileDescriptor.AutoCloseOutputStream(rp.outputStream).use { outs ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        if (n > 0) {
                            outs.write(buf, 0, n)
                            copied += n
                        }
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { rp.destroy() }
            throw IllegalStateException("向服务端传输内容失败：${e.message}")
        }
        val code = rp.waitFor()
        if (code != 0) {
            throw IllegalStateException("服务端写入命令退出码 $code")
        }
        return copied
    }

    /**
     * 反向泵：服务端 `cat '远程文件'`，stdout 流式落到本地 [dst]。
     * 用于导入（源在其他应用的 Android/data 下时 UserService 通道读不了）。
     * 返回字节数；命令退出码非 0 时抛异常（调用方负责清理临时文件）。
     */
    fun pumpFromRemote(remotePath: String, dst: File): Long {
        val rp = service().newProcess(
            arrayOf("/system/bin/sh", "-c", "cat ${quote(remotePath)}"),
            null, "/"
        )
        var copied = 0L
        try {
            ParcelFileDescriptor.AutoCloseInputStream(rp.inputStream).use { ins ->
                dst.outputStream().use { outs ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        if (n > 0) {
                            outs.write(buf, 0, n)
                            copied += n
                        }
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { rp.destroy() }
            throw IllegalStateException("从服务端读取内容失败：${e.message}")
        }
        val code = rp.waitFor()
        if (code != 0) {
            throw IllegalStateException("服务端读取命令退出码 $code")
        }
        return copied
    }
}
