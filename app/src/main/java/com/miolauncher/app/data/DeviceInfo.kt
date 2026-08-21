package com.miolauncher.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * 设备信息与兼容性工具：
 * 依据设备实际内存 / ABI / 型号，为不同机型提供安全默认值。
 */
object DeviceInfo {

    /** 当前设备总物理内存（MB） */
    fun totalMemoryMb(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            (mi.totalMem / 1024 / 1024).toInt().coerceAtLeast(1024)
        } catch (_: Exception) {
            4096
        }
    }

    /**
     * 游戏 JVM 安全内存上限（MB）。
     * 预留系统 / 其他进程空间，低配机型自动降档，防止被 LMK 杀死。
     */
    fun safeGameMemoryMb(context: Context): Int {
        val total = totalMemoryMb(context)
        return when {
            total < 2048 -> 768
            total < 4096 -> 1536
            total < 8192 -> 3072
            else -> 6144
        }
    }

    /** 建议的游戏内存档位（启动设置滑块上限） */
    fun recommendedMemoryRange(context: Context): IntRange = 512..safeGameMemoryMb(context)

    /**
     * 内存扩展后的上限（需用户显式开启并二次确认）。
     * 取物理内存的 60%（比安全值的约 40% 高），仍保留系统余量。
     */
    fun extendedMemoryLimit(context: Context): Int {
        val total = totalMemoryMb(context)
        return (total * 60 / 100).coerceAtLeast(safeGameMemoryMb(context))
    }

    /**
     * 游戏 JVM 默认内存（MB）。
     * 取安全上限的一半（保证充裕）但不超过 2048：堆过大反而引发系统内存压力被 LMK 误杀。
     * 玩家可在启动设置里手动调高。
     */
    fun defaultGameMemoryMb(context: Context): Int =
        (safeGameMemoryMb(context) / 2).coerceIn(512, 2048)

    /** 当前运行 ABI（如 arm64-v8a） */
    fun primaryAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: Build.CPU_ABI ?: "unknown"

    /** 设备型号（厂商 + 型号） */
    fun deviceModel(): String {
        val manu = Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "unknown"
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "unknown"
        return "$manu $model"
    }

    /** API 级别 */
    fun androidVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
