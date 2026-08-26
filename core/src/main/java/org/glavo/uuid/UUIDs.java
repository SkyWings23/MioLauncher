package org.glavo.uuid;

import java.util.UUID;

/**
 * 与 uuid-tools 0.2.0 同包同名的 UUIDs 替代实现。
 *
 * 原库使用 java.lang.invoke.VarHandle 做 byte[] 高效读写，在低版本 Android
 * （API 27-31）的 ART 验证器上抛 VerifyError（byte[] 被当作 Object[] 检查），
 * 导致账号/会话等使用 UUIDs 的路径崩溃。本实现改为纯移位位运算，
 * 行为与原库一致，且不依赖 Java 9+ 特性。
 *
 * 本启动器实际只用到：toCompactString / parse / generateV7 及少量常量。
 */
public final class UUIDs {

    public static final UUID NIL = new UUID(0L, 0L);
    public static final UUID MAX = new UUID(-1L, -1L);
    public static final UUID NAMESPACE_DNS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID NAMESPACE_OID = UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID NAMESPACE_X500 = UUID.fromString("6ba7b814-9dad-11d1-80b4-00c04fd430c8");

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private UUIDs() {
    }

    public static String toCompactString(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        char[] buf = new char[32];
        for (int i = 0; i < 16; i++) {
            buf[i] = HEX[(int) ((msb >>> (60 - i * 4)) & 0xF)];
            buf[16 + i] = HEX[(int) ((lsb >>> (60 - i * 4)) & 0xF)];
        }
        return new String(buf);
    }

    public static UUID parse(String str) {
        return UUID.fromString(str);
    }

    public static UUID generateV7() {
        long time = System.currentTimeMillis();
        long randA = (long) (Math.random() * 0x100000L);
        long msb = (time & 0x0000FFFFFFFFFFFFL) << 16;
        msb |= (0x7L << 12) | (randA & 0xFFFL);
        long randB = (long) (Math.random() * 0x100000000L) << 32;
        randB |= ((long) (Math.random() * 0x100000000L)) & 0xFFFFFFFFL;
        randB |= 0x8000000000000000L;
        return new UUID(msb, randB);
    }

    // ---- 常用辅助（行为与原库一致）----

    public static String toURNString(UUID uuid) {
        return "urn:uuid:" + uuid;
    }

    public static String toOIDString(UUID uuid) {
        String s = toCompactString(uuid);
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < s.length(); i++) {
            if (i == 8 || i == 12 || i == 16 || i == 20) sb.append('-');
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    public static boolean isNil(UUID uuid) {
        return uuid.equals(NIL);
    }

    public static boolean isMax(UUID uuid) {
        return uuid.equals(MAX);
    }

    public static int compare(UUID a, UUID b) {
        return a.compareTo(b);
    }

    public static byte[] toBytes(UUID uuid) {
        byte[] buf = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            buf[i] = (byte) (msb >>> (56 - i * 8));
            buf[8 + i] = (byte) (lsb >>> (56 - i * 8));
        }
        return buf;
    }

    public static void toBytes(UUID uuid, byte[] dest, int off) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            dest[off + i] = (byte) (msb >>> (56 - i * 8));
            dest[off + 8 + i] = (byte) (lsb >>> (56 - i * 8));
        }
    }

    public static UUID fromBytes(byte[] src) {
        return fromBytes(src, 0);
    }

    public static UUID fromBytes(byte[] src, int off) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (src[off + i] & 0xFFL);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (src[off + i] & 0xFFL);
        return new UUID(msb, lsb);
    }
}
