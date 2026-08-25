package com.miolauncher.app.data

import android.content.Context
import android.net.Uri
import org.jackhuang.hmcl.auth.AuthInfo
import org.jackhuang.hmcl.auth.OAuth
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService
import org.jackhuang.hmcl.auth.microsoft.MicrosoftSession

/**
 * 正版（微软）登录。
 * 使用设备码流：显示设备码 → 打开浏览器 → 玩家在微软页面输入码授权 → 获取 Minecraft 会话。
 * Client ID 为 Mojang 官方登录器公开的 Azure 应用 ID（支持 device flow）。
 */
object MsAccount {

    private const val CLIENT_ID = "00000000402b5328"
    const val CLIENT_ID_PUBLIC = CLIENT_ID

    private const val PREF = "mio_ms"
    private const val KEY_TOKEN_TYPE = "token_type"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_NOT_AFTER = "not_after"
    private const val KEY_UUID = "uuid"
    private const val KEY_USERNAME = "username"

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val notAfter: Long,
        val uuid: String,
        val username: String,
    )

    /** 设备码回调：把设备码和验证 URL 展示给用户。 */
    class DeviceCallback(
        private val onDeviceCode: (userCode: String, verificationUri: String) -> Unit,
        private val onOpenBrowser: (String) -> Unit,
    ) : OAuth.Callback {
        override fun startServer(): OAuth.Session? = null

        override fun grantDeviceCode(userCode: String, verificationURI: String) {
            onDeviceCode(userCode, verificationURI)
        }

        override fun loginCompletedDeviceCode() {
        }

        override fun openBrowser(grantFlow: OAuth.GrantFlow, url: String) {
            onOpenBrowser(url)
        }

        override fun getClientId(): String = CLIENT_ID
    }

    /** 用微软账号登录（设备码流，阻塞直到完成或失败）。 */
    @Throws(Exception::class)
    fun login(
        onDeviceCode: (userCode: String, verificationUri: String) -> Unit,
        onOpenBrowser: (String) -> Unit,
    ): Session {
        val callback = DeviceCallback(onDeviceCode, onOpenBrowser)
        val service = MicrosoftService(callback)
        val session = service.authenticate(OAuth.GrantFlow.DEVICE)
        if (session.profile() == null) {
            throw Exception("该账号没有可用的 Minecraft 角色，请确认已购买正版 Minecraft")
        }
        return toSession(session)
    }

    private fun toSession(s: MicrosoftSession): Session = Session(
        accessToken = s.accessToken(),
        refreshToken = s.refreshToken(),
        notAfter = s.notAfter(),
        uuid = s.profile().id().toString(),
        username = s.profile().name(),
    )

    /** 读取已保存的微软会话（未登录返回 null） */
    fun load(context: Context): Session? {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val token = p.getString(KEY_ACCESS_TOKEN, "") ?: ""
        if (token.isBlank()) return null
        return Session(
            accessToken = token,
            refreshToken = p.getString(KEY_REFRESH_TOKEN, "") ?: "",
            notAfter = p.getLong(KEY_NOT_AFTER, 0),
            uuid = p.getString(KEY_UUID, "") ?: "",
            username = p.getString(KEY_USERNAME, "") ?: "",
        )
    }

    fun save(context: Context, s: Session) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCESS_TOKEN, s.accessToken)
            .putString(KEY_REFRESH_TOKEN, s.refreshToken)
            .putLong(KEY_NOT_AFTER, s.notAfter)
            .putString(KEY_UUID, s.uuid)
            .putString(KEY_USERNAME, s.username)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
