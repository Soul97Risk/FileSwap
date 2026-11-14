package com.fileswap.app

import android.content.Context
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * 快捷替换预设：把「目标文件 + 备份路径 + 替换源文件」绑成一个可一键触发的组合。
 * 替换源是导入到 App 私有目录的文件（见 MainViewModel.importFile）。
 * 时间戳不存这里——备份文件的 mtime 被设为原目标的 mtime（见 quickReplace），
 * 还原时从备份文件本身取回，跨设备导入备份也能正确还原。
 */
@Immutable
data class QuickPreset(
    val id: String,
    val name: String,
    val targetPath: String,
    val backupPath: String,
    val sourcePath: String,
    val sourceName: String
)

/** QuickPreset 的持久化：SharedPreferences + org.json（Android 平台自带，无新依赖）。 */
object QuickPresetStore {

    private const val PREFS = "quick_presets"
    private const val KEY = "presets"

    fun load(context: Context): List<QuickPreset> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                QuickPreset(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    targetPath = o.getString("target"),
                    backupPath = o.getString("backup"),
                    sourcePath = o.getString("source"),
                    sourceName = o.optString("sourceName")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, presets: List<QuickPreset>) {
        val arr = JSONArray()
        presets.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("target", p.targetPath)
                    .put("backup", p.backupPath)
                    .put("source", p.sourcePath)
                    .put("sourceName", p.sourceName)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
