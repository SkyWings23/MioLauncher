package com.miolauncher.app.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * bsdiff 补丁应用（bspatch）纯 Java 实现，用于 APK 增量更新。
 *
 * 原理：bsdiff 补丁由 控制块(ctrl) + 数据块(diff) + 额外块(extra) 三段（bzip2 压缩）组成。
 * 控制块为 (x, y, z) 三元组：从旧文件读 x 字节与 diff 数据相加写新文件，
 * 再从 extra 数据块读 y 字节直接写新文件，z 表示旧文件指针的额外跳转。
 * 参考：https://github.com/mendsley/bsdiff 的 bspatch 逻辑。
 */
object Bspatch {

    /**
     * 应用补丁：oldFile + patchFile → newFile。
     * @throws Exception 解析/解压/写入失败
     */
    fun apply(oldFile: File, newFile: File, patchFile: File) {
        if (!oldFile.isFile) throw IllegalStateException("旧版本文件不存在")
        val patch = patchFile.readBytes()

        // 校验 BSDIFF40 魔数
        if (patch.size < 32 ||
            patch[0] != 'B'.code.toByte() || patch[1] != 'S'.code.toByte() ||
            patch[2] != 'D'.code.toByte() || patch[3] != 'I'.code.toByte() ||
            patch[4] != 'F'.code.toByte() || patch[5] != 'F'.code.toByte() ||
            patch[6] != '4'.code.toByte() || patch[7] != '0'.code.toByte()) {
            throw IllegalStateException("无效的增量补丁")
        }

        // 头部三段长度（offset 8, 16, 24）
        val ctrlLen = off64(patch, 8).toInt()
        val diffLen = off64(patch, 16).toInt()
        val newSize = off64(patch, 24).toInt()

        if (ctrlLen < 0 || diffLen < 0 || newSize < 0) {
            throw IllegalStateException("补丁头部异常")
        }

        // 补丁数据三段从 32 字节处开始，各自 bzip2 压缩
        var pos = 32
        val ctrlIn = bz2Stream(patch, pos, ctrlLen); pos += ctrlLen
        val diffIn = bz2Stream(patch, pos, diffLen); pos += diffLen
        val extraIn = bz2Stream(patch, pos, patch.size - pos)

        try {
            val oldData = oldFile.readBytes()
            val newData = ByteArrayOutputStream(newSize)

            var oldPos = 0L
            var newPos = 0L
            val ctrlBuf = ByteArray(8)

            while (newPos < newSize) {
                // 读控制块三元组 x, y, z
                readFully(ctrlIn, ctrlBuf)
                var x = off64(ctrlBuf, 0)
                readFully(ctrlIn, ctrlBuf)
                var y = off64(ctrlBuf, 0)
                readFully(ctrlIn, ctrlBuf)
                val z = off64(ctrlBuf, 0)

                if (newPos + x > newSize) throw IllegalStateException("补丁数据越界")

                // diff 段：new[i] = old[oldPos+i] + diff[i]（diff 字节直接加，字节运算）
                var i = 0L
                while (i < x) {
                    val b = diffIn.read()
                    if (b < 0) throw IllegalStateException("diff 数据提前结束")
                    val oldByte = if (oldPos + i < oldData.size) oldData[(oldPos + i).toInt()].toInt() and 0xFF else 0
                    newData.write(((oldByte + b) and 0xFF))
                    i++
                }

                // extra 段：直接拷贝
                var j = 0L
                while (j < y) {
                    val b = extraIn.read()
                    if (b < 0) throw IllegalStateException("extra 数据提前结束")
                    newData.write(b)
                    j++
                }

                oldPos += x
                oldPos += z  // z 带符号（可能为负，表示向前跳转）
                newPos += x + y
            }

            // 写入新文件
            newFile.parentFile?.mkdirs()
            newFile.writeBytes(newData.toByteArray())
        } finally {
            runCatching { ctrlIn.close() }
            runCatching { diffIn.close() }
            runCatching { extraIn.close() }
        }
    }

    /** 读满 buffer（不足抛异常）。 */
    private fun readFully(inp: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = inp.read(buf, off, buf.size - off)
            if (n < 0) throw IllegalStateException("补丁数据不完整")
            off += n
        }
    }

    /**
     * 读取 bsdiff 头长度（offtin 编码）：
     * 低 7 字节小端，最高字节 bit7 为符号位（bit6..0 是最高 7 位值）。
     */
    private fun off64(data: ByteArray, offset: Int): Long {
        var y = (data[offset + 7].toLong() and 0x7F)
        for (i in 6 downTo 0) {
            y = (y shl 8) or (data[offset + i].toLong() and 0xFF)
        }
        if (data[offset + 7].toInt() and 0x80 != 0) y = -y
        return y
    }

    /** 从补丁的指定区间解出 bzip2 流。 */
    private fun bz2Stream(data: ByteArray, offset: Int, length: Int): InputStream {
        if (length <= 0) throw IllegalStateException("补丁段长度异常")
        return org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(
            java.io.ByteArrayInputStream(data, offset, length)
        )
    }
}
