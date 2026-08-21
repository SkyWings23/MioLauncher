package com.miolauncher.app.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 简易 Minecraft RCON 客户端，用于向本地/远程服务器发送控制台命令（kick/op/deop/list 等）。
 * 协议：https://wiki.vg/RCON
 */
object RconClient {

    data class RconResult(val success: Boolean, val body: String)

    /**
     * 向指定服务器发送 RCON 命令。
     * @param host 服务器地址（IP 或域名）
     * @param port RCON 端口（默认 25575）
     * @param password RCON 密码
     * @param command 要执行的命令（如 "list", "kick PlayerName", "op PlayerName"）
     * @return RconResult(success, 响应内容)
     */
    fun execute(host: String, port: Int, password: String, command: String, timeoutMs: Int = 5000): RconResult {
        return try {
            Socket(host, port).use { socket ->
                socket.soTimeout = timeoutMs
                val out = DataOutputStream(socket.getOutputStream())
                val inp = DataInputStream(socket.getInputStream())

                // 1. 认证包
                val authId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
                val authPayload = buildPacket(0, 3, password, authId)
                out.write(authPayload)
                out.flush()

                // 读认证响应
                val authResp = readPacket(inp)
                if (authResp.requestId != authId) {
                    return@use RconResult(false, "认证失败：密码错误或服务器 RCON 未开启")
                }

                // 2. 命令包
                val cmdId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
                val cmdPayload = buildPacket(cmdId, 2, command, cmdId)
                out.write(cmdPayload)
                out.flush()

                // 读命令响应
                val cmdResp = readPacket(inp)
                RconResult(cmdResp.requestId == cmdId, cmdResp.body)
            }
        } catch (e: java.net.ConnectException) {
            RconResult(false, "无法连接 RCON（端口 $port）：${e.message}")
        } catch (e: java.net.SocketTimeoutException) {
            RconResult(false, "RCON 连接超时")
        } catch (e: Exception) {
            RconResult(false, "RCON 错误：${e.message}")
        }
    }

    /** 构造 RCON 数据包 */
    private fun buildPacket(requestId: Int, type: Int, body: String, authId: Int): ByteArray {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val packetSize = 4 + 4 + bodyBytes.size + 1 + 1 // requestId + type + body + \0 + \0
        return ByteBuffer.allocate(4 + packetSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(packetSize)    // 包大小
            .putInt(requestId)     // 请求 ID
            .putInt(type)          // 类型（3=认证, 2=命令）
            .put(bodyBytes)        // 内容
            .put(0.toByte())       // 空终止符
            .put(0.toByte())       // 空终止符（padding）
            .array()
    }

    /** 读取 RCON 响应包 */
    private fun readPacket(inp: DataInputStream): RconPacket {
        val size = inp.readInt()
        val requestId = inp.readInt()
        val type = inp.readInt()
        val bodyBytes = ByteArray(size - 4 - 4 - 1) // -requestId -type -trailing \0
        inp.readFully(bodyBytes)
        inp.readByte() // trailing \0
        return RconPacket(requestId, type, String(bodyBytes, Charsets.UTF_8).trimEnd('\u0000'))
    }

    private data class RconPacket(val requestId: Int, val type: Int, val body: String)

    /** 快捷命令：列出在线玩家 */
    fun listPlayers(host: String, port: Int, password: String): String {
        val result = execute(host, port, password, "list")
        return if (result.success) result.body else "获取失败：${result.body}"
    }

    /** 快捷命令：踢出玩家 */
    fun kickPlayer(host: String, port: Int, password: String, player: String, reason: String = "被管理员踢出"): RconResult {
        return execute(host, port, password, "kick $player $reason")
    }

    /** 快捷命令：设为管理员 */
    fun opPlayer(host: String, port: Int, password: String, player: String): RconResult {
        return execute(host, port, password, "op $player")
    }

    /** 快捷命令：取消管理员 */
    fun deopPlayer(host: String, port: Int, password: String, player: String): RconResult {
        return execute(host, port, password, "deop $player")
    }
}
