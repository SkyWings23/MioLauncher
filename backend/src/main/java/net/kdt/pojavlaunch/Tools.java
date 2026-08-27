package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * 对应 libpojavexec.so 的 native 方法 + FCL 控件所需工具成员。
 */
public class Tools {
    private Tools() {}

    /** 返回当前进程的 ART JavaVM 指针。 */
    public static native long getJavaVMPointer();

    public static final String APP_NAME = "MioLauncher";
    public static final String APP_ID = "com.miolauncher.app";
    public static final String DIR_DATA = "/data/data/" + APP_ID;
    public static final String CTRLMAP_PATH = "/storage/emulated/0/MioLauncher/controlmap/";
    public static final String DIR_GAME_HOME = DIR_DATA + "/files/.minecraft";
    public static final String DIR_GAME_NEW = DIR_DATA + "/files/mio/game";
    /**
     * 当前渲染器名（即 AMETHYST_RENDERER 环境变量），启动游戏前由 JRE 写入。
     * 默认 opengles3。运行时读取以支持 Zink 等 Kopper 渲染器的分支逻辑。
     */
    public static String getLocalRenderer() {
        try {
            String r = System.getenv("AMETHYST_RENDERER");
            return (r == null || r.isEmpty()) ? "opengles3" : r;
        } catch (Throwable t) {
            return "opengles3";
        }
    }

    public static DisplayMetrics currentDisplayMetrics = new DisplayMetrics();

    public static final Gson GLOBAL_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public static DisplayMetrics getDisplayMetrics(Activity ctx) {
        return currentDisplayMetrics;
    }

    public static boolean isAndroid8OrHigher() {
        return android.os.Build.VERSION.SDK_INT >= 26;
    }

    public static boolean hasMods(String... mods) {
        return false;
    }

    public static int getDisplayFriendlyRes(int i, float f) {
        // MioLauncher: 分辨率缩放真正生效（f 为比例，如 1.0=100%, 0.5=50%）
        if (f <= 0.05f) return i;
        return Math.max(1, (int) (i * f));
    }

    public static void showError(Context ctx, Throwable e, boolean exit) {
        Log.e("MioTools", "error", e);
    }

    public static int pxToDp(float px) {
        return (int) (px / currentDisplayMetrics.density);
    }

    public static int dpToPx(float dp) {
        return (int) (dp * currentDisplayMetrics.density);
    }

    public static float dpToPxf(float dp) {
        return dp * currentDisplayMetrics.density;
    }

    public static String read(String path) throws java.io.IOException {
        File f = new File(path);
        if (!f.exists()) return null;
        byte[] b = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < b.length) {
                int r = in.read(b, off, b.length - off);
                if (r < 0) break;
                off += r;
            }
        }
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String read(InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    public static void write(String path, String content) throws java.io.IOException {
        File f = new File(path);
        f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public static boolean isValidString(String s) {
        return s != null && !s.isEmpty();
    }

    public static String jObjectToString(Object o) {
        if (o == null) return null;
        if (o instanceof String) return (String) o;
        if (o instanceof org.json.JSONObject) return ((org.json.JSONObject) o).toString();
        return o.toString();
    }

    public static <T> T getWeakReference(WeakReference<T> ref) {
        return ref == null ? null : ref.get();
    }

    public static void updateWindowSize(Activity ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);
        if (size.x == 0 || size.y == 0) {
            size.set(ctx.getResources().getDisplayMetrics().widthPixels,
                    ctx.getResources().getDisplayMetrics().heightPixels);
        }
        currentDisplayMetrics.widthPixels = size.x;
        currentDisplayMetrics.heightPixels = size.y;
        currentDisplayMetrics.density = ctx.getResources().getDisplayMetrics().density;
        currentDisplayMetrics.densityDpi = ctx.getResources().getDisplayMetrics().densityDpi;
        currentDisplayMetrics.scaledDensity = ctx.getResources().getDisplayMetrics().scaledDensity;
    }
}
