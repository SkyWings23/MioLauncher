package com.miolauncher.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

/**
 * 联机服务器管理：服务器列表（增删改查）+ 待连接服务器。
 * 服务器列表存于 files/mio/servers.json；待连接服务器用于"一键进服"。
 */
object ServerManager {

    const val PREF_NAME = "mio_online"
    const val KEY_PENDING_SERVER = "pending_server"

    data class MioServer(
        val name: String,
        val address: String,   // host 或 host:port
    )

    private val gson = Gson()

    private fun file(context: Context): File = File(context.filesDir, "mio/servers.json")

    fun list(context: Context): List<MioServer> {
        return try {
            val f = file(context)
            if (!f.isFile) return emptyList()
            val arr = com.google.gson.JsonParser.parseString(f.readText()).asJsonArray
            arr.mapNotNull { e ->
                val o = e.asJsonObject
                if (!o.has("name") || !o.has("address")) null
                else MioServer(o.get("name").asString, o.get("address").asString)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, name: String, address: String): Boolean {
        val nameT = name.trim()
        val addrT = address.trim()
        if (nameT.isEmpty() || addrT.isEmpty()) return false
        val list = list(context).toMutableList()
        list.removeAll { it.address == addrT }
        list.add(MioServer(nameT, addrT))
        save(context, list)
        return true
    }

    fun remove(context: Context, address: String) {
        save(context, list(context).filter { it.address != address })
    }

    fun rename(context: Context, oldAddress: String, newName: String) {
        if (newName.isBlank()) return
        save(context, list(context).map { if (it.address == oldAddress) it.copy(name = newName.trim()) else it })
    }

    private fun save(context: Context, servers: List<MioServer>) {
        try {
            val arr = JsonArray()
            servers.forEach { s ->
                val o = JsonObject()
                o.addProperty("name", s.name)
                o.addProperty("address", s.address)
                arr.add(o)
            }
            file(context).parentFile?.mkdirs()
            file(context).writeText(arr.toString())
        } catch (_: Exception) { }
    }

    // ---- 待连接服务器（一键进服） ----

    fun pendingServer(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_SERVER, null)?.takeIf { it.isNotBlank() }

    fun setPendingServer(context: Context, address: String?) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PENDING_SERVER, address).apply()
    }

    /** 取走待连接服务器（进服成功后清空） */
    fun takePendingServer(context: Context): String? {
        val p = pendingServer(context)
        if (p != null) setPendingServer(context, null)
        return p
    }
}
