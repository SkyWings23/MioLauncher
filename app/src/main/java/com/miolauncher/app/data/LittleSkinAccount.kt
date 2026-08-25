package com.miolauncher.app.data

import android.content.Context
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorProvider
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService

/**
 * LittleSkin 外置登录账户。
 * 使用 Yggdrasil 外置登录 API（authlib-injector 兼容），登录后保存会话用于启动。
 *
 * 遵循 Yggdrasil 规范：
 * - clientToken 在设备本地固定生成并复用（规范：每次随机会导致 refresh 异常）
 * - 只保存 accessToken/clientToken/角色信息，不保存密码
 * - token 生命周期：validate → 失败 refresh → 失败才重新登录
 */
object LittleSkinAccount {

    const val API_ROOT = "https://littleskin.cn/api/yggdrasil/"

    private const val PREF = "mio_littleskin"
    private const val KEY_EMAIL = "email"
    private const val KEY_CLIENT_TOKEN = "client_token"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_UUID = "uuid"
    private const val KEY_USERNAME = "username"
    private const val KEY_PROPERTIES = "user_properties"

    data class Session(
        val email: String,
        val clientToken: String,
        val accessToken: String,
        val uuid: String,
        val username: String,
        val userProperties: String,
    )

    private fun service(): YggdrasilService = YggdrasilService(AuthlibInjectorProvider(API_ROOT))

    private fun toSession(loginEmail: String, clientToken: String, s: org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession): Session {
        val profile = s.getSelectedProfile()
        val props = s.getUserProperties().let { map ->
            val obj = com.google.gson.JsonObject()
            map.forEach { (k, v) -> obj.addProperty(k, v) }
            obj.toString()
        }
        return Session(
            email = loginEmail,
            clientToken = clientToken,
            accessToken = s.getAccessToken(),
            uuid = profile?.getId()?.toString() ?: "",
            username = profile?.getName() ?: loginEmail,
            userProperties = props,
        )
    }

    /** 用 LittleSkin 用户名/密码登录，返回会话（含 accessToken/uuid/username）。失败抛异常。 */
    @Throws(Exception::class)
    fun login(email: String, password: String, context: Context): Session {
        val clientToken = getOrCreateClientToken(context)
        val session = service().authenticate(email, password, clientToken)
        return toSession(email, clientToken, session)
    }

    /**
     * 读取已保存会话并确保令牌有效（Yggdrasil 规范生命周期）：
     * 1. validate 校验；有效直接返回
     * 2. 失效 → refresh 续期，成功后更新保存
     * 3. refresh 也失败 → 返回 null（需重新登录）
     */
    fun loadValid(context: Context): Session? {
        val s = load(context) ?: return null
        return try {
            val svc = service()
            if (svc.validate(s.accessToken, s.clientToken)) {
                s
            } else {
                // 尝试刷新续期
                val profile = try {
                    GameProfile(java.util.UUID.fromString(s.uuid), s.username)
                } catch (_: Exception) {
                    null
                }
                val refreshed = svc.refresh(s.accessToken, s.clientToken, profile)
                val newSession = toSession(s.email, s.clientToken, refreshed)
                // 刷新成功才更新本地
                context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TOKEN, newSession.accessToken)
                    .putString(KEY_UUID, newSession.uuid)
                    .putString(KEY_USERNAME, newSession.username)
                    .putString(KEY_PROPERTIES, newSession.userProperties)
                    .apply()
                newSession
            }
        } catch (_: Exception) {
            // validate/refresh 网络异常也视为需要重新登录（保守）
            null
        }
    }

    /** 设备本地固定的 clientToken（Yggdrasil 规范：同一设备复用同一 UUID，避免 refresh 异常） */
    fun getOrCreateClientToken(context: Context): String {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.getString(KEY_CLIENT_TOKEN, "")?.takeIf { it.isNotBlank() }?.let { return it }
        val token = java.util.UUID.randomUUID().toString().replace("-", "")
        sp.edit().putString(KEY_CLIENT_TOKEN, token).apply()
        return token
    }

    /** 读取已保存的 LittleSkin 会话（未登录返回 null；不校验令牌有效性） */
    fun load(context: Context): Session? {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val token = p.getString(KEY_TOKEN, "") ?: ""
        val email = p.getString(KEY_EMAIL, "") ?: ""
        if (token.isBlank() || email.isBlank()) return null
        return Session(
            email = email,
            clientToken = p.getString(KEY_CLIENT_TOKEN, "") ?: "",
            accessToken = token,
            uuid = p.getString(KEY_UUID, "") ?: "",
            username = p.getString(KEY_USERNAME, "") ?: email,
            userProperties = p.getString(KEY_PROPERTIES, "{}") ?: "{}",
        )
    }

    fun save(context: Context, s: Session) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMAIL, s.email)
            .putString(KEY_CLIENT_TOKEN, s.clientToken)
            .putString(KEY_TOKEN, s.accessToken)
            .putString(KEY_UUID, s.uuid)
            .putString(KEY_USERNAME, s.username)
            .putString(KEY_PROPERTIES, s.userProperties)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
