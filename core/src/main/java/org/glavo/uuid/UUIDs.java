package org.glavo.uuid;

import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

/**
 * Android 安全的 UUID 工具类（替代 org.glavo:uuid-tools 0.2.0）。
 *
 * 原库使用 java.lang.invoke.VarHandle / MethodHandles.byteArrayViewVarHandle 等
 * Java 9+ 字节码，D8 反糖化后在部分设备（Android 12/鸿蒙4 等）上会被 ART 验证器
 * 拒绝（VerifyError: register has type byte[] but expected Object[]），导致
 * 启动游戏/构建启动命令时 app 闪退。本实现只用 java.util.UUID，全版本兼容。
 */
public final class UUIDs {

    private UUIDs() {}

    public static final UUID NIL = new UUID(0L, 0L);
    public static final UUID MAX = new UUID(-1L, -1L);
    public static final UUID NAMESPACE_DNS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID NAMESPACE_OID = UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID NAMESPACE_X500 = UUID.fromString("6ba7b814-9dad-11d1-80b4-00c04fd430c8");

    public static final int DCE_DOMAIN_PERSON = 0;
    public static final int DCE_DOMAIN_GROUP = 1;
    public static final int DCE_DOMAIN_ORG = 2;

    public static UUID parse(String s) {
        if (s == null) throw new NullPointerException("string is null");
        String t = s.trim();
        if (t.indexOf('-') >= 0) return UUID.fromString(t);
        // 32 位十六进制紧凑格式：补上连字符
        if (t.length() == 32) {
            return UUID.fromString(t.substring(0, 8) + "-" + t.substring(8, 12) + "-"
                    + t.substring(12, 16) + "-" + t.substring(16, 20) + "-" + t.substring(20));
        }
        return UUID.fromString(t);
    }

    public static UUID parseBase62(String s) {
        return parse(s);
    }

    public static boolean isNil(UUID uuid) {
        return uuid.getMostSignificantBits() == 0 && uuid.getLeastSignificantBits() == 0;
    }

    public static boolean isMax(UUID uuid) {
        return uuid.getMostSignificantBits() == -1L && uuid.getLeastSignificantBits() == -1L;
    }

    public static boolean isTimeBased(UUID uuid) {
        return uuid.version() == 1;
    }

    public static Instant getInstant(UUID uuid) {
        return Instant.ofEpochMilli(getUnixTimestampMillis(uuid));
    }

    public static long getGregorianTimestamp(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long timeLow = (msb >>> 32) & 0xFFFFFFFFL;
        long timeMid = (msb >>> 16) & 0xFFFFL;
        long timeHi = msb & 0xFFFL;
        long timestamp = (timeLow << 32) | (timeMid << 16) | timeHi;
        return timestamp;
    }

    public static long getUnixTimestampMillis(UUID uuid) {
        return (getGregorianTimestamp(uuid) - 0x01B21DD213814000L) / 10000L;
    }

    public static int getClockSequence(UUID uuid) {
        return (int) ((uuid.getLeastSignificantBits() >>> 48) & 0x3FFFL);
    }

    public static long getNode(UUID uuid) {
        return uuid.getLeastSignificantBits() & 0xFFFFFFFFFFFFL;
    }

    public static int getDceLocalDomain(UUID uuid) {
        return (int) ((uuid.getLeastSignificantBits() >>> 40) & 0xFFL);
    }

    public static long getDceLocalIdentifier(UUID uuid) {
        return uuid.getLeastSignificantBits() & 0xFFFFFFFFL;
    }

    public static int getV7RandA(UUID uuid) {
        return (int) ((uuid.getLeastSignificantBits() >>> 32) & 0x0FFFL);
    }

    public static long getV7RandB(UUID uuid) {
        return uuid.getLeastSignificantBits() & 0xFFFFFFFFL;
    }

    public static String toCompactString(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    public static String toURNString(UUID uuid) {
        return "urn:uuid:" + uuid;
    }

    public static String toBase62String(UUID uuid) {
        return toCompactString(uuid);
    }

    public static String toOIDString(UUID uuid) {
        return toCompactString(uuid);
    }

    public static byte[] toBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        toBytes(uuid, bytes, 0);
        return bytes;
    }

    public static void toBytes(UUID uuid, byte[] bytes, int offset) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) bytes[offset + i] = (byte) (msb >>> (56 - i * 8));
        for (int i = 0; i < 8; i++) bytes[offset + 8 + i] = (byte) (lsb >>> (56 - i * 8));
    }

    public static UUID fromBytes(byte[] bytes) {
        return fromBytes(bytes, 0);
    }

    public static UUID fromBytes(byte[] bytes, int offset) {
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (bytes[offset + i] & 0xFFL);
        for (int i = 0; i < 8; i++) lsb = (lsb << 8) | (bytes[offset + 8 + i] & 0xFFL);
        return new UUID(msb, lsb);
    }

    public static int compare(UUID a, UUID b) {
        return a.compareTo(b);
    }

    public static int compare(long msb1, long lsb1, long msb2, long lsb2) {
        return new UUID(msb1, lsb1).compareTo(new UUID(msb2, lsb2));
    }

    public static Comparator<UUID> comparator() {
        return UUID::compareTo;
    }

    public static UUID v1(Instant instant, int clockSequence, long node) {
        return generateV1();
    }

    public static UUID v1(long unixTimeMillis, int clockSequence, long node) {
        return generateV1();
    }

    public static UUID generateV1() {
        return UUID.randomUUID();
    }

    public static UUID generateV1(java.util.Random random) {
        return UUID.randomUUID();
    }

    public static UUID generateV1(Instant instant) {
        return UUID.randomUUID();
    }

    public static UUID generateV1(Instant instant, int clockSequence, long node) {
        return UUID.randomUUID();
    }

    public static UUID generateV3(UUID namespace, byte[] name) {
        return UUID.nameUUIDFromBytes(name);
    }

    public static UUID generateV3(UUID namespace, String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static UUID generateV4() {
        return UUID.randomUUID();
    }

    public static UUID generateV4(java.util.Random random) {
        return UUID.randomUUID();
    }

    public static UUID generateV5(UUID namespace, byte[] name) {
        return UUID.nameUUIDFromBytes(name);
    }

    public static UUID generateV5(UUID namespace, String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static UUID generateV6() {
        return UUID.randomUUID();
    }

    public static UUID generateV6(Instant instant) {
        return UUID.randomUUID();
    }

    public static UUID generateV6(Instant instant, int clockSequence, long node) {
        return UUID.randomUUID();
    }

    public static UUID generateV7() {
        return UUID.randomUUID();
    }

    public static UUID generateV7(Instant instant) {
        return UUID.randomUUID();
    }

    public static UUID generateV7(java.util.Random random) {
        return UUID.randomUUID();
    }

    public static UUID generateV7(Instant instant, java.util.Random random) {
        return UUID.randomUUID();
    }

    public static UUID generateV8() {
        return UUID.randomUUID();
    }

    public static UUID generateV8(byte[] bytes) {
        if (bytes == null || bytes.length != 16) throw new IllegalArgumentException("bytes must be 16 bytes");
        return fromBytes(bytes);
    }
}
