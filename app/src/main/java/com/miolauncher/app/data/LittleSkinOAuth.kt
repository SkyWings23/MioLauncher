package com.miolauncher.app.data

import org.jackhuang.hmcl.auth.OAuth
import org.jackhuang.hmcl.util.Lang.mapOf
import org.jackhuang.hmcl.util.Pair.pair
import org.jackhuang.hmcl.util.io.HttpRequest

/**
 * LittleSkin OAuth2（OpenID Connect）登录。
 *
 * LittleSkin 新版采用 OAuth 2 设备码流（Device Authorization Grant，RFC 8628），
 * 与微软登录同一套 [OAuth] 框架。流程：
 *  1. 请求设备码对（client_id + scope）→ 得 user_code / device_code
 *  2. 展示 user_code，引导用户到授权页输入
 *  3. 轮询 token 端点 → 得 OAuth access_token + id_token（含 selectedProfile）
 *  4. 调 LittleSkin `/api/yggdrasil/authserver/oauth` 换 Minecraft 令牌（Yggdrasil AccessToken）
 *
 * 注意：需要在 LittleSkin 后台（littleskin.cn/user/oauth/manage）创建应用并
 * 邮件申请「设备代码流白名单」后才能使用。CLIENT_ID 申请到后填入。
 */
object LittleSkinOAuth {

    /** LittleSkin 分配的客户端 ID（在 littleskin.cn/user/oauth/manage 创建应用后获取） */
    const val CLIENT_ID = "VcjizgD9lpctimJ3MSsN1JlwsAR8ilsQOka6lvRD"

    /** 授权端点 */
    const val AUTH_URL = "https://open.littleskin.cn/oauth/device_code"
    const val TOKEN_URL = "https://open.littleskin.cn/oauth/token"

    /** OAuth scope：openid(拿id_token/selectedProfile) + 选择角色 + 换 Minecraft 令牌 */
    const val SCOPE = "openid Yggdrasil.PlayerProfiles.Select Yggdrasil.MinecraftToken.Create"

    /** LittleSkin 换 Minecraft 令牌端点 */
    const val MINECRAFT_TOKEN_URL = "https://littleskin.cn/api/yggdrasil/authserver/oauth"

    /** Yggdrasil API 根（authlib-injector 兼容） */
    const val YGGDRASIL_ROOT = "https://littleskin.cn/api/yggdrasil/"

    /** OAuth 设备码回调：把设备码/验证 URL 展示给用户 */
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

    /**
     * 设备码流登录：阻塞直到用户完成授权或失败。
     * @return OAuth 授权结果（含访问令牌 + 授权时选择的角色）
     */
    @Throws(Exception::class)
    fun loginDeviceCode(
        onDeviceCode: (userCode: String, verificationUri: String) -> Unit,
        onOpenBrowser: (String) -> Unit,
    ): OAuthResult {
        // OAuth.java 的设备码流会 POST device_code URL（response_type=device_code 是微软特有，
        // 这里 LittleSkin 需要自定义 scope，直接用 HttpRequest 实现，避免与微软 endpoint 耦合）
        val deviceCodeResponse = HttpRequest.POST(AUTH_URL)
            .form(
                pair("client_id", CLIENT_ID),
                pair("scope", SCOPE),
            )
            .ignoreHttpCode()
            .getJson(DeviceCodeResponse::class.java)
        if (!deviceCodeResponse.deviceCode.isNullOrBlank() && deviceCodeResponse.userCode != null) {
            val userCode = deviceCodeResponse.userCode!!
            val deviceCode = deviceCodeResponse.deviceCode!!
            val verificationUri = deviceCodeResponse.verificationUriComplete
                ?: deviceCodeResponse.verificationUri ?: "https://open.littleskin.cn/oauth/link"
            onDeviceCode(userCode, verificationUri)
            onOpenBrowser(verificationUri)

            // 轮询授权结果
            val start = System.nanoTime()
            var interval = (deviceCodeResponse.interval ?: 5L).coerceAtLeast(1)
            while (true) {
                Thread.sleep(interval * 1000)
                val elapsedSec = (System.nanoTime() - start) / 1_000_000_000
                if (elapsedSec >= (deviceCodeResponse.expiresIn ?: 300).coerceAtLeast(60)) {
                    throw Exception("授权已超时，请重试")
                }
                val tokenResponse = HttpRequest.POST(TOKEN_URL)
                    .form(
                        pair("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                        pair("client_id", CLIENT_ID),
                        pair("device_code", deviceCode),
                    )
                    .ignoreHttpCode()
                    .getJson(TokenResponse::class.java)
                when {
                    tokenResponse.error == "authorization_pending" -> continue
                    tokenResponse.error == "slow_down" -> { interval += 5; continue }
                    tokenResponse.error == "expired_token" -> throw Exception("授权已过期，请重试")
                    tokenResponse.error != null -> throw Exception(
                        "LittleSkin 授权失败：${tokenResponse.error} ${tokenResponse.errorDescription ?: ""}".trim()
                    )
                }
                val accessToken = tokenResponse.accessToken
                if (!accessToken.isNullOrBlank()) {
                    return OAuthResult(
                        accessToken = accessToken,
                        refreshToken = tokenResponse.refreshToken,
                        selectedProfile = parseSelectedProfile(tokenResponse.idToken),
                    )
                }
                throw Exception("LittleSkin 授权失败：未返回访问令牌")
            }
        }
        throw Exception("LittleSkin 设备码请求失败：${deviceCodeResponse.errorDescription ?: "未知错误"}")
    }

    /** 用 OAuth access_token + 角色 uuid 换取 Minecraft 令牌（Yggdrasil AccessToken） */
    @Throws(Exception::class)
    fun minecraftToken(oauthAccessToken: String, uuid: String): MinecraftTokenResult {
        val resp = HttpRequest.POST(MINECRAFT_TOKEN_URL)
            .json(mapOf(pair("uuid", uuid)))
            .header("Authorization", "Bearer $oauthAccessToken")
            .getJson(MinecraftTokenResult::class.java)
        if (resp.accessToken.isNullOrBlank()) {
            throw Exception("获取 Minecraft 令牌失败")
        }
        return resp
    }

    data class MinecraftTokenResult(
        @com.google.gson.annotations.SerializedName("accessToken") val accessToken: String?,
        @com.google.gson.annotations.SerializedName("clientToken") val clientToken: String?,
        @com.google.gson.annotations.SerializedName("selectedProfile") val selectedProfile: Profile?,
        @com.google.gson.annotations.SerializedName("availableProfiles") val availableProfiles: List<Profile>?,
    )

    data class Profile(
        @com.google.gson.annotations.SerializedName("id") val id: String?,
        @com.google.gson.annotations.SerializedName("name") val name: String?,
    )

    data class OAuthResult(
        val accessToken: String,
        val refreshToken: String?,
        /** 授权时用户选择的角色（来自 id_token.selectedProfile，可能为 null） */
        val selectedProfile: Profile?,
    )

    /** 解析 id_token（JWT）的 payload，提取 selectedProfile。不验签（仅取 uuid/name）。 */
    private fun parseSelectedProfile(idToken: String?): Profile? {
        if (idToken.isNullOrBlank()) return null
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),
                Charsets.UTF_8,
            )
            val obj = com.google.gson.JsonParser.parseString(payload).asJsonObject
            val sp = obj.getAsJsonObject("selectedProfile") ?: return null
            Profile(
                id = sp.get("id")?.asString,
                name = sp.get("name")?.asString,
            )
        } catch (_: Exception) {
            null
        }
    }

    private class DeviceCodeResponse {
        @com.google.gson.annotations.SerializedName("user_code")
        var userCode: String? = null

        @com.google.gson.annotations.SerializedName("device_code")
        var deviceCode: String? = null

        @com.google.gson.annotations.SerializedName("verification_uri")
        var verificationUri: String? = null

        @com.google.gson.annotations.SerializedName("verification_uri_complete")
        var verificationUriComplete: String? = null

        @com.google.gson.annotations.SerializedName("expires_in")
        var expiresIn: Long? = null

        @com.google.gson.annotations.SerializedName("interval")
        var interval: Long? = null

        @com.google.gson.annotations.SerializedName("error")
        var error: String? = null

        @com.google.gson.annotations.SerializedName("error_description")
        var errorDescription: String? = null
    }

    private class TokenResponse {
        @com.google.gson.annotations.SerializedName("token_type")
        var tokenType: String? = null

        @com.google.gson.annotations.SerializedName("expires_in")
        var expiresIn: Long? = null

        @com.google.gson.annotations.SerializedName("access_token")
        var accessToken: String? = null

        @com.google.gson.annotations.SerializedName("refresh_token")
        var refreshToken: String? = null

        @com.google.gson.annotations.SerializedName("id_token")
        var idToken: String? = null

        @com.google.gson.annotations.SerializedName("error")
        var error: String? = null

        @com.google.gson.annotations.SerializedName("error_description")
        var errorDescription: String? = null
    }
}
