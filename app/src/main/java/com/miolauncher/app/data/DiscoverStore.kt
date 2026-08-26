package com.miolauncher.app.data

import android.content.Context

/**
 * 「发现好服」本地存储：设备 ID、每日推送设置、负反馈(不感兴趣)缓存、标签桶。
 */
object DiscoverStore {
    private const val PREF = "mio_discover"
    private const val KEY_DEVICE = "device_id"
    private const val KEY_PUSH = "allow_daily_push"
    private const val KEY_HOUR = "push_hour"
    private const val KEY_NEGATIVE = "negative"   // srvId:epoch 每行一条
    private const val KEY_TAGS = "tag_bucket"     // tag:count 每行一条
    private const val HIDE_DAYS = 30L

    private fun p(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 稳定的匿名设备 ID（服务端防刷用），首次生成后持久化 */
    fun deviceId(context: Context): String {
        p(context).getString(KEY_DEVICE, null)?.let { return it }
        val id = java.util.UUID.randomUUID().toString().replace("-", "")
        p(context).edit().putString(KEY_DEVICE, id).apply()
        return id
    }

    /** 每日推送总开关（默认开启） */
    fun allowDailyPush(context: Context): Boolean = p(context).getBoolean(KEY_PUSH, true)
    fun setAllowDailyPush(context: Context, v: Boolean) =
        p(context).edit().putBoolean(KEY_PUSH, v).apply()

    /** 推送时间（小时，默认 12:00，可 12 或 20） */
    fun pushHour(context: Context): Int = p(context).getInt(KEY_HOUR, 12)
    fun setPushHour(context: Context, h: Int) = p(context).edit().putInt(KEY_HOUR, h).apply()

    private fun negativeMap(context: Context): Map<String, Long> {
        val map = HashMap<String, Long>()
        (p(context).getString(KEY_NEGATIVE, "") ?: "").split("\n").forEach { line ->
            val i = line.lastIndexOf(':')
            if (i > 0) {
                val t = line.substring(i + 1).toLongOrNull()
                if (t != null) map[line.substring(0, i)] = t
            }
        }
        return map
    }

    /** 该服务器是否被用户"不感兴趣"（30 天内隐藏） */
    fun isHidden(context: Context, srvId: String): Boolean {
        val t = negativeMap(context)[srvId] ?: return false
        return System.currentTimeMillis() - t < HIDE_DAYS * 86400_000L
    }

    /** 标记不感兴趣（同时上报服务器，本地缓存 30 天） */
    fun hideServer(context: Context, srvId: String) {
        val keep = (p(context).getString(KEY_NEGATIVE, "") ?: "")
            .split("\n").filter { it.isNotBlank() && !it.startsWith("$srvId:") }
        p(context).edit().putString(KEY_NEGATIVE, (keep + "$srvId:${System.currentTimeMillis()}").joinToString("\n")).apply()
    }

    /** 记录点击过的服务器标签（本地标签桶，供每日推荐上报） */
    fun addTag(context: Context, tags: List<String>) {
        if (tags.isEmpty()) return
        val map = HashMap<String, Int>()
        (p(context).getString(KEY_TAGS, "") ?: "").split("\n").forEach { line ->
            val i = line.lastIndexOf(':')
            if (i > 0) line.substring(i + 1).toIntOrNull()?.let { map[line.substring(0, i)] = it }
        }
        tags.forEach { map[it] = (map[it] ?: 0) + 1 }
        val body = map.entries.joinToString("\n") { "${it.key}:${it.value}" }
        p(context).edit().putString(KEY_TAGS, body).apply()
    }

    /** 本地标签桶 Top N（最多 max 个） */
    fun topTags(context: Context, max: Int = 5): List<String> {
        val list = ArrayList<Pair<String, Int>>()
        (p(context).getString(KEY_TAGS, "") ?: "").split("\n").forEach { line ->
            val i = line.lastIndexOf(':')
            if (i > 0) line.substring(i + 1).toIntOrNull()?.let { list.add(line.substring(0, i) to it) }
        }
        return list.sortedByDescending { it.second }.take(max).map { it.first }
    }
}
