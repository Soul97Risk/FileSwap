package com.fileswap.app

import android.content.Context

/**
 * 文件选择器的记忆功能：
 * - 上次浏览目录：重新打开选择器时从上次离开的地方继续
 * - 最近导入：一键重新导入最近用过的替换源文件（mod 更新场景）
 * - 排序偏好：排序方式与方向跨会话保留（默认 时间降序，最新文件在最上）
 *
 * 路径按行分隔持久化。Linux 文件名理论上可含换行，实际游戏文件不会出现。
 */
object BrowserMemory {

    private const val PREFS = "browser_memory"
    private const val KEY_LAST_DIR = "last_dir"
    private const val KEY_RECENT = "recent_imports"
    private const val KEY_SORT_MODE = "sort_mode"
    private const val KEY_SORT_ASC = "sort_asc"
    private const val MAX_RECENT = 8

    fun lastDir(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_DIR, null)
    }

    fun setLastDir(context: Context, path: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_DIR, path).apply()
    }

    fun recentImports(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECENT, null) ?: return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    /** 记录一次成功导入：去重置顶，最多保留 [MAX_RECENT] 条 */
    fun pushRecentImport(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = (listOf(path) + recentImports(context)).distinct().take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT, updated.joinToString("\n")).apply()
    }

    fun sortMode(context: Context): SortBy {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SORT_MODE, null)
        return SortBy.entries.firstOrNull { it.name == name } ?: SortBy.DATE
    }

    fun sortAsc(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SORT_ASC, false)

    fun saveSort(context: Context, mode: SortBy, asc: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SORT_MODE, mode.name)
            .putBoolean(KEY_SORT_ASC, asc)
            .apply()
    }
}
