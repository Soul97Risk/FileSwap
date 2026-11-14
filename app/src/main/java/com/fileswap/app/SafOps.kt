package com.fileswap.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File

/**
 * SAF 树授权通道：用应用自己的 uid 经 ContentResolver 读写其他应用 Android/data 下的文件。
 *
 * 为什么存在它：部分 Android 10 及以下设备/文档提供器允许用户为受限共享目录
 * 授予树访问权。Android 11+ 的系统选择器明确禁止为其他应用的 Android/data
 * 与 Android/obb 授权；这些目标应使用 Root 或 Shizuku。SAF 走应用自身身份，
 * 与 shell/UserService 通道解耦。
 *
 * 实测约定（研究结论）：FileSystemProvider 的 query 会丢弃 selection 参数，
 * 必须客户端逐行比对显示名；renameDocument 覆盖已存在目标是慢路径且易碎，
 * 覆盖写一律「先删旧文档再新建」。
 */
object SafOps {

    private const val PREFS = "saf_grants"

    /** 外置存储文档 provider 的 authority（系统 ExternalStorageProvider） */
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * 由文件系统路径推出「选择器初始位置」的文档 URI：
     * `/storage/emulated/0/a/b` → documentId `primary:a/b`。
     * 让系统目录选择器直接打开到目标目录附近，用户少点几下。
     * 路径不在主存储下时返回 null（选择器从默认位置打开）。
     */
    fun initialTreeUri(dirPath: String): Uri? {
        val docId = primaryDocId(dirPath) ?: return null
        return runCatching {
            DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, docId)
        }.getOrNull()
    }

    /**
     * Directly constructs an ExternalStorageProvider tree URI as an opportunistic probe.
     * This is not a user-granted SAF permission and is expected to be rejected on many
     * Android versions and devices; callers must verify access and fall back safely.
     */
    fun fabricatedTreeUri(dirPath: String): Uri? {
        val docId = primaryDocId(dirPath) ?: return null
        return runCatching {
            DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE_AUTHORITY, docId)
        }.getOrNull()
    }

    /** `/storage/emulated/0/a/b` → `primary:a/b`；不在主存储下返回 null */
    private fun primaryDocId(dirPath: String): String? {
        val prefix = "/storage/emulated/0/"
        val normalized = runCatching { File(dirPath).canonicalPath }.getOrDefault(dirPath)
        if (!normalized.startsWith(prefix)) return null
        val rel = normalized.removePrefix(prefix).trim('/')
        if (rel.isEmpty()) return null
        return "primary:$rel"
    }

    /** A SAF tree is usable only when it is the exact directory represented by dirPath. */
    fun isTreeForDirectory(treeUri: Uri, dirPath: String): Boolean {
        val expectedId = primaryDocId(dirPath) ?: return false
        return runCatching {
            DocumentsContract.isTreeUri(treeUri) &&
                treeUri.authority == EXTERNAL_STORAGE_AUTHORITY &&
                DocumentsContract.getTreeDocumentId(treeUri) == expectedId
        }.getOrDefault(false)
    }

    /** 持久化目录授权：dirPath（文件系统路径）→ treeUri */
    fun saveGrant(context: Context, dirPath: String, treeUri: Uri) {
        require(isTreeForDirectory(treeUri, dirPath)) {
            "所选 SAF 目录与目标文件所在目录不一致"
        }
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(dirPath, treeUri.toString()).apply()
    }

    fun grantFor(context: Context, dirPath: String): Uri? {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(dirPath, null) ?: return null
        val uri = runCatching { Uri.parse(s) }.getOrNull() ?: return null
        return uri.takeIf { isTreeForDirectory(it, dirPath) }
    }

    /** 全部已持久化授权（目录路径 → treeUri） */
    fun grants(context: Context): Map<String, Uri> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.all.mapNotNull { (k, v) ->
            (v as? String)?.let { k to (runCatching { Uri.parse(it) }.getOrNull() ?: return@mapNotNull null) }
        }.toMap()
    }

    /** 在树里按显示名找文档；FileSystemProvider 丢弃 selection 参数，必须客户端比对 */
    fun findDoc(context: Context, treeUri: Uri, name: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            val idIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (idIndex < 0 || nameIndex < 0) {
                throw IllegalStateException("SAF provider 返回了不完整的目录信息")
            }
            while (c.moveToNext()) {
                if (c.getString(nameIndex) == name) {
                    val documentId = c.getString(idIndex)
                        ?: throw IllegalStateException("SAF provider 未返回文档 ID")
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                }
            }
        } ?: throw IllegalStateException("SAF provider 未返回目录内容")
        return null
    }

    fun hasFile(context: Context, treeUri: Uri, name: String): Boolean =
        findDoc(context, treeUri, name) != null

    /**
     * 覆盖写：先删旧文档再新建（研究实证的可靠路径），返回写入字节数。
     * 目标必须已通过 [saveGrant] 持久化授权，treeUri 即授权的那棵树。
     */
    fun writeFile(context: Context, treeUri: Uri, fileName: String, src: File): Long {
        val resolver = context.contentResolver
        val old = findDoc(context, treeUri, fileName)
        val oldCopy = old?.let { existing ->
            val staged = File.createTempFile("fswap-saf-old-", ".tmp", context.cacheDir)
            try {
                resolver.openInputStream(existing)?.use { input ->
                    staged.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                } ?: throw IllegalStateException("无法暂存旧文档：$fileName")
                staged
            } catch (e: Exception) {
                runCatching { staged.delete() }
                throw IllegalStateException("无法暂存旧文档，未修改目标：$fileName。${e.message}", e)
            }
        }
        var oldDeleted = false
        try {
            if (old != null) {
                if (!DocumentsContract.deleteDocument(resolver, old)) {
                    throw IllegalStateException("SAF 删除旧文档失败：$fileName")
                }
                oldDeleted = true
            }
            return createAndWrite(context, treeUri, fileName, src)
        } catch (e: Exception) {
            if (oldDeleted && oldCopy != null) {
                val recovery = runCatching { createAndWrite(context, treeUri, fileName, oldCopy) }
                if (recovery.isFailure) {
                    e.addSuppressed(recovery.exceptionOrNull()!!)
                    throw IllegalStateException(
                        "SAF 写入失败且旧文档自动恢复失败：${e.message}", e
                    )
                }
            }
            throw IllegalStateException("SAF 写入失败，旧文档已尽力保留：${e.message}", e)
        } finally {
            runCatching { if (oldCopy?.exists() == true) oldCopy.delete() }
        }
    }

    private fun createAndWrite(context: Context, treeUri: Uri, fileName: String, src: File): Long {
        val resolver = context.contentResolver
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val doc = DocumentsContract.createDocument(resolver, treeUri, mime, fileName)
            ?: throw IllegalStateException("SAF 创建文档失败：$fileName")
        try {
            var copied = 0L
            resolver.openOutputStream(doc, "w")?.use { outs ->
                src.inputStream().use { ins ->
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
            } ?: throw IllegalStateException("SAF 打开输出流失败")
            if (copied != src.length()) {
                throw IllegalStateException("SAF 写入长度不匹配：预期 ${src.length()}，实际 $copied")
            }
            return copied
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw IllegalStateException("SAF 未完成文档已清理：${e.message}", e)
        }
    }

    /** 读文档到本地文件，返回字节数（备份用：SAF 读目标 → 本地临时 → 写备份） */
    fun readFile(context: Context, treeUri: Uri, fileName: String, dst: File): Long {
        val doc = findDoc(context, treeUri, fileName)
            ?: throw IllegalStateException("SAF 未找到文档：$fileName")
        var copied = 0L
        context.contentResolver.openInputStream(doc)?.use { ins ->
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
        } ?: throw IllegalStateException("SAF 打开输入流失败")
        return copied
    }
}
