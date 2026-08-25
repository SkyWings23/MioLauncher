package com.miolauncher.app.data

import org.jackhuang.hmcl.auth.microsoft.MicrosoftService
import java.io.File

/**
 * 自定义皮肤上传。
 * 正版（微软）用户：上传到 Mojang 账号，游戏内即时生效。
 * 离线/外置用户：保存本地皮肤，供本地加载（通过 local_skins 机制）。
 */
object SkinUploader {

    /**
     * 上传皮肤到正版 Mojang 账号。
     * @param accessToken 微软登录的 accessToken
     * @param isSlim true=Alex 模型（slim），false=Steve 模型（classic）
     * @param skinFile PNG 皮肤文件
     */
    @Throws(Exception::class)
    fun uploadToMojang(accessToken: String, isSlim: Boolean, skinFile: File) {
        val service = MicrosoftService(object : org.jackhuang.hmcl.auth.OAuth.Callback {
            override fun startServer(): org.jackhuang.hmcl.auth.OAuth.Session? = null
            override fun grantDeviceCode(userCode: String, verificationURI: String) {}
            override fun loginCompletedDeviceCode() {}
            override fun openBrowser(grantFlow: org.jackhuang.hmcl.auth.OAuth.GrantFlow, url: String) {}
            override fun getClientId(): String = MsAccount.CLIENT_ID_PUBLIC
        })
        service.uploadSkin(accessToken, isSlim, skinFile.toPath())
    }

    /** 保存本地皮肤：复制到应用目录，供本地加载。 */
    fun saveLocalSkin(context: android.content.Context, source: java.io.InputStream, isSlim: Boolean): File {
        val dir = File(context.filesDir, "mio/skins").apply { mkdirs() }
        val model = if (isSlim) "slim" else "wide"
        val target = File(dir, "local_$model.png")
        source.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        return target
    }

    /** 读取本地皮肤文件（不存在返回 null）。 */
    fun loadLocalSkin(context: android.content.Context, isSlim: Boolean): File? {
        val model = if (isSlim) "slim" else "wide"
        val f = File(File(context.filesDir, "mio/skins"), "local_$model.png")
        return if (f.isFile) f else null
    }
}
