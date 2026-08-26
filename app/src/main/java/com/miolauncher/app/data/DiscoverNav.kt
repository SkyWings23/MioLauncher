package com.miolauncher.app.data

/**
 * 通知/外部入口 → 打开「发现」页指定服务器详情的跨组件信使。
 * 通知点击时先写入 pendingServerId，再拉起 MainActivity；MioApp 组合时消费并自动跳转。
 */
object DiscoverNav {
    @Volatile
    var pendingServerId: String? = null

    fun consume(): String? {
        val v = pendingServerId
        pendingServerId = null
        return v
    }
}
