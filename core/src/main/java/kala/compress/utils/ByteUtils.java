package kala.compress.utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 与 kala-compress-base 1.27.1-3 同包同名的 ByteUtils 替代实现。
 *
 * 原库使用 java.lang.invoke.VarHandle 访问 byte[]，在低版本 Android（API 27-31）
 * 的 ART 验证器上会抛 VerifyError（byte[] 被当作 Object[] 检查），导致
 * 整合包安装 ZipArchiveReader.<clinit> 崩溃。本实现改为纯字节数组按位移位读写，
 * 行为与原库完全一致（大端/小端），且不依赖任何 Java 9+ 特性。
 */
public final class ByteUtils {

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private ByteUtils() {
    }

    private static void checkReadLength(int length) {
        if (length > 8) {
            throw new IllegalArgumentException("Can't read more than eight bytes into a long value");
        }
    }

    public static long fromLittleEndian(byte[] bytes) {
        return fromLittleEndian(bytes, 0, bytes.length);
    }

    public static long fromLittleEndian(byte[] bytes, int off, int length) {
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            l |= (bytes[off + i] & 0xffL) << (8 * i);
        }
        return l;
    }

    public static long fromLittleEndian(ByteSupplier src, int length) throws IOException {
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            int b = src.getAsByte();
            if (b == -1) throw new IOException("Premature end of data");
            l |= ((long) b) << (8 * i);
        }
        return l;
    }

    public static long fromLittleEndian(DataInput src, int length) throws IOException {
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            l |= ((long) src.readUnsignedByte()) << (8 * i);
        }
        return l;
    }

    public static long fromLittleEndian(InputStream src, int length) throws IOException {
        checkReadLength(length);
        long l = 0;
        for (int i = 0; i < length; i++) {
            int b = src.read();
            if (b == -1) throw new IOException("Premature end of data");
            l |= ((long) b) << (8 * i);
        }
        return l;
    }

    public static void toLittleEndian(byte[] b, long value, int off, int length) {
        for (int i = 0; i < length; i++) {
            b[off + i] = (byte) (value >>> (8 * i));
        }
    }

    public static void toLittleEndian(ByteConsumer dest, long value, int length) throws IOException {
        for (int i = 0; i < length; i++) {
            dest.accept((byte) (value >>> (8 * i)));
        }
    }

    public static void toLittleEndian(DataOutput dest, long value, int length) throws IOException {
        for (int i = 0; i < length; i++) {
            dest.writeByte((int) (value >>> (8 * i)));
        }
    }

    public static void toLittleEndian(OutputStream dest, long value, int length) throws IOException {
        for (int i = 0; i < length; i++) {
            dest.write((int) (value >>> (8 * i)));
        }
    }

    public static byte getByte(byte[] src, int index) {
        return src[index];
    }

    public static int getUnsignedByte(byte[] src, int index) {
        return src[index] & 0xFF;
    }

    public static short getShortLE(byte[] src, int index) {
        return (short) (getUnsignedShortLE(src, index));
    }

    public static int getUnsignedShortLE(byte[] src, int index) {
        return (src[index] & 0xFF)
            | ((src[index + 1] & 0xFF) << 8);
    }

    public static short getShortBE(byte[] src, int index) {
        return (short) getUnsignedShortBE(src, index);
    }

    public static int getUnsignedShortBE(byte[] src, int index) {
        return ((src[index] & 0xFF) << 8)
            | (src[index + 1] & 0xFF);
    }

    public static int getIntLE(byte[] src, int index) {
        return (int) fromLittleEndian(src, index, 4);
    }

    public static long getUnsignedIntLE(byte[] src, int index) {
        return fromLittleEndian(src, index, 4);
    }

    public static int getIntBE(byte[] src, int index) {
        return ((src[index] & 0xFF) << 24)
            | ((src[index + 1] & 0xFF) << 16)
            | ((src[index + 2] & 0xFF) << 8)
            | (src[index + 3] & 0xFF);
    }

    public static long getUnsignedIntBE(byte[] src, int index) {
        return ((long) getIntBE(src, index)) & 0xFFFFFFFFL;
    }

    public static long getLongLE(byte[] src, int index) {
        return fromLittleEndian(src, index, 8);
    }

    public static long getLongBE(byte[] src, int index) {
        return ((long) src[index] << 56)
            | (((long) src[index + 1] & 0xFF) << 48)
            | (((long) src[index + 2] & 0xFF) << 40)
            | (((long) src[index + 3] & 0xFF) << 32)
            | (((long) src[index + 4] & 0xFF) << 24)
            | (((long) src[index + 5] & 0xFF) << 16)
            | (((long) src[index + 6] & 0xFF) << 8)
            | ((long) src[index + 7] & 0xFF);
    }

    public static void setByte(byte[] src, int index, byte value) {
        src[index] = value;
    }

    public static void setUnsignedByte(byte[] src, int index, int value) {
        src[index] = (byte) value;
    }

    public static void setShortLE(byte[] src, int index, short value) {
        src[index] = (byte) value;
        src[index + 1] = (byte) (value >>> 8);
    }

    public static void setUnsignedShortLE(byte[] src, int index, int value) {
        src[index] = (byte) value;
        src[index + 1] = (byte) (value >>> 8);
    }

    public static void setShortBE(byte[] src, int index, short value) {
        src[index] = (byte) (value >>> 8);
        src[index + 1] = (byte) value;
    }

    public static void setUnsignedShortBE(byte[] src, int index, int value) {
        src[index] = (byte) (value >>> 8);
        src[index + 1] = (byte) value;
    }

    public static void setIntLE(byte[] src, int index, int value) {
        src[index] = (byte) value;
        src[index + 1] = (byte) (value >>> 8);
        src[index + 2] = (byte) (value >>> 16);
        src[index + 3] = (byte) (value >>> 24);
    }

    public static void setUnsignedIntLE(byte[] src, int index, long value) {
        src[index] = (byte) value;
        src[index + 1] = (byte) (value >>> 8);
        src[index + 2] = (byte) (value >>> 16);
        src[index + 3] = (byte) (value >>> 24);
    }

    public static void setIntBE(byte[] src, int index, int value) {
        src[index] = (byte) (value >>> 24);
        src[index + 1] = (byte) (value >>> 16);
        src[index + 2] = (byte) (value >>> 8);
        src[index + 3] = (byte) value;
    }

    public static void setUnsignedIntBE(byte[] src, int index, long value) {
        src[index] = (byte) (value >>> 24);
        src[index + 1] = (byte) (value >>> 16);
        src[index + 2] = (byte) (value >>> 8);
        src[index + 3] = (byte) value;
    }

    public static void setLongLE(byte[] src, int index, long value) {
        for (int i = 0; i < 8; i++) {
            src[index + i] = (byte) (value >>> (8 * i));
        }
    }

    public static void setLongBE(byte[] src, int index, long value) {
        for (int i = 0; i < 8; i++) {
            src[index + 7 - i] = (byte) (value >>> (8 * i));
        }
    }

    public interface ByteSupplier {
        int getAsByte() throws IOException;
    }

    public interface ByteConsumer {
        void accept(byte b) throws IOException;
    }

    public static class OutputStreamByteConsumer implements ByteConsumer {
        private final OutputStream out;

        public OutputStreamByteConsumer(OutputStream out) {
            this.out = out;
        }

        @Override
        public void accept(byte b) throws IOException {
            out.write(b);
        }
    }
}
