package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Android 兼容工具：替代 Android 上不可用的 Files.readString / Files.writeString。
 */
public final class AndroidFiles {

    private AndroidFiles() {
    }

    public static @NotNull String readString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    public static void writeString(Path path, CharSequence csq) throws IOException {
        Files.write(path, csq.toString().getBytes(StandardCharsets.UTF_8));
    }
}
