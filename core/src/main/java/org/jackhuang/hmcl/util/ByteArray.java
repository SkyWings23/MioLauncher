/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * 说明：原实现用 java.lang.invoke.VarHandle 做字节序读写。D8 对 VarHandle 的
 * 签名多态方法（get/set + 基元 cast）去糖后生成无效字节码，在 Android 上触发
 * VerifyError（register v4 has type X but expected Object），导致下载/资产解析崩溃。
 * 这里改为纯 Java 位运算，无任何 invokedynamic/VarHandle，D8 可正确编译。
 */
package org.jackhuang.hmcl.util;

public final class ByteArray {
    private ByteArray() {
    }

    // Get

    public static byte getByte(byte[] array, int offset) {
        return array[offset];
    }

    public static int getUnsignedByte(byte[] array, int offset) {
        return Byte.toUnsignedInt(getByte(array, offset));
    }

    public static short getShortLE(byte[] array, int offset) {
        return (short) ((array[offset] & 0xff) | ((array[offset + 1] & 0xff) << 8));
    }

    public static int getUnsignedShortLE(byte[] array, int offset) {
        return Short.toUnsignedInt(getShortLE(array, offset));
    }

    public static short getShortBE(byte[] array, int offset) {
        return (short) (((array[offset] & 0xff) << 8) | (array[offset + 1] & 0xff));
    }

    public static int getUnsignedShortBE(byte[] array, int offset) {
        return Short.toUnsignedInt(getShortBE(array, offset));
    }

    public static int getIntLE(byte[] array, int offset) {
        return (array[offset] & 0xff)
                | ((array[offset + 1] & 0xff) << 8)
                | ((array[offset + 2] & 0xff) << 16)
                | ((array[offset + 3] & 0xff) << 24);
    }

    public static long getUnsignedIntLE(byte[] array, int offset) {
        return Integer.toUnsignedLong(getIntLE(array, offset));
    }

    public static int getIntBE(byte[] array, int offset) {
        return ((array[offset] & 0xff) << 24)
                | ((array[offset + 1] & 0xff) << 16)
                | ((array[offset + 2] & 0xff) << 8)
                | (array[offset + 3] & 0xff);
    }

    public static long getUnsignedIntBE(byte[] array, int offset) {
        return Integer.toUnsignedLong(getIntBE(array, offset));
    }

    public static long getLongLE(byte[] array, int offset) {
        return (getIntLE(array, offset) & 0xffff_ffffL)
                | ((getIntLE(array, offset + 4) & 0xffff_ffffL) << 32);
    }

    public static long getLongBE(byte[] array, int offset) {
        return (getIntBE(array, offset) & 0xffff_ffffL)
                | ((getIntBE(array, offset + 4) & 0xffff_ffffL) << 32);
    }

    // Set

    public static void setByte(byte[] array, int offset, byte value) {
        array[offset] = value;
    }

    public static void setUnsignedByte(byte[] array, int offset, int value) {
        array[offset] = (byte) (value & 0xff);
    }

    public static void setShortLE(byte[] array, int offset, short value) {
        array[offset] = (byte) (value & 0xff);
        array[offset + 1] = (byte) ((value >>> 8) & 0xff);
    }

    public static void setUnsignedShortLE(byte[] array, int offset, int value) {
        setShortLE(array, offset, (short) (value & 0xffff));
    }

    public static void setShortBE(byte[] array, int offset, short value) {
        array[offset] = (byte) ((value >>> 8) & 0xff);
        array[offset + 1] = (byte) (value & 0xff);
    }

    public static void setUnsignedShortBE(byte[] array, int offset, int value) {
        setShortBE(array, offset, (short) (value & 0xffff));
    }

    public static void setIntLE(byte[] array, int offset, int value) {
        array[offset] = (byte) (value & 0xff);
        array[offset + 1] = (byte) ((value >>> 8) & 0xff);
        array[offset + 2] = (byte) ((value >>> 16) & 0xff);
        array[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }

    public static void setUnsignedIntLE(byte[] array, int offset, long value) {
        setIntLE(array, offset, (int) (value & 0xffff_ffffL));
    }

    public static void setIntBE(byte[] array, int offset, int value) {
        array[offset] = (byte) ((value >>> 24) & 0xff);
        array[offset + 1] = (byte) ((value >>> 16) & 0xff);
        array[offset + 2] = (byte) ((value >>> 8) & 0xff);
        array[offset + 3] = (byte) (value & 0xff);
    }

    public static void setUnsignedIntBE(byte[] array, int offset, long value) {
        setIntBE(array, offset, (int) (value & 0xffff_ffffL));
    }

    public static void setLongLE(byte[] array, int offset, long value) {
        setIntLE(array, offset, (int) (value & 0xffff_ffffL));
        setIntLE(array, offset + 4, (int) ((value >>> 32) & 0xffff_ffffL));
    }

    public static void setLongBE(byte[] array, int offset, long value) {
        setIntBE(array, offset, (int) ((value >>> 32) & 0xffff_ffffL));
        setIntBE(array, offset + 4, (int) (value & 0xffff_ffffL));
    }
}
