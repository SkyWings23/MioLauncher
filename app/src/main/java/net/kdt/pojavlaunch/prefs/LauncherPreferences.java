package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * MioLauncher 精简版 LauncherPreferences（对齐 FCL 控件所需静态偏好）。
 */
public class LauncherPreferences {
    public static SharedPreferences DEFAULT_PREF;

    public static float PREF_BUTTONSIZE = 100.0f;
    public static float PREF_SCALE_FACTOR = 1f;
    public static boolean PREF_MOUSE_GRAB_FORCE = false;
    public static boolean PREF_USE_ALTERNATE_SURFACE = false;
    public static boolean PREF_BUTTON_ALL_CAPS = false;
    public static boolean PREF_DISABLE_GESTURES = false;
    public static boolean PREF_ENABLE_MOUSE_CLICK = false;
    public static boolean PREF_DISABLE_SWAP_HAND = false;
    public static float PREF_MOUSESCALE = 1.0f;
    public static float PREF_MOUSESPEED = 1.0f;
    public static int PREF_LONGPRESS_TRIGGER = 400;
    public static float PREF_DEADZONE_SCALE = 1.0f;
    public static String PREF_DEFAULTCTRL_PATH = "default.json";

    public static boolean PREF_GYRO_INVERT_X = false;
    public static boolean PREF_GYRO_INVERT_Y = false;
    public static float PREF_GYRO_SENSITIVITY = 1.0f;
    public static boolean PREF_GYRO_SMOOTHING = false;
    public static int PREF_GYRO_SAMPLE_RATE = 100;

    public static String CTRLDEF_FILE = "default.json";

    public static void loadPreferences(Context ctx) {
        DEFAULT_PREF = ctx.getSharedPreferences("mio_pref", Context.MODE_PRIVATE);
        PREF_BUTTONSIZE = DEFAULT_PREF.getFloat("buttonsize", 100.0f);
        PREF_SCALE_FACTOR = DEFAULT_PREF.getInt("resolutionScale", 100) / 100f;
        PREF_MOUSE_GRAB_FORCE = DEFAULT_PREF.getBoolean("mouseForceGrab", false);
        PREF_USE_ALTERNATE_SURFACE = DEFAULT_PREF.getBoolean("useAltSurface", false);
        PREF_BUTTON_ALL_CAPS = DEFAULT_PREF.getBoolean("buttonAllCaps", false);
        PREF_DISABLE_GESTURES = DEFAULT_PREF.getBoolean("disableGestures", false);
        PREF_DISABLE_SWAP_HAND = DEFAULT_PREF.getBoolean("disableSwapHand", false);
        PREF_MOUSESCALE = DEFAULT_PREF.getFloat("mousescale", 1.0f);
        PREF_MOUSESPEED = DEFAULT_PREF.getFloat("mousespeed", 1.0f);
        PREF_LONGPRESS_TRIGGER = DEFAULT_PREF.getInt("longPressTrigger", 400);
        PREF_DEADZONE_SCALE = DEFAULT_PREF.getFloat("deadzoneScale", 1.0f);
        PREF_GYRO_INVERT_X = DEFAULT_PREF.getBoolean("gyroInvertX", false);
        PREF_GYRO_INVERT_Y = DEFAULT_PREF.getBoolean("gyroInvertY", false);
        PREF_GYRO_SENSITIVITY = DEFAULT_PREF.getFloat("gyroSensitivity", 1.0f);
        PREF_GYRO_SMOOTHING = DEFAULT_PREF.getBoolean("gyroSmoothing", false);
        PREF_GYRO_SAMPLE_RATE = DEFAULT_PREF.getInt("gyroSampleRate", 100);
    }

    public static long getTotalDeviceMemory() {
        return android.os.Build.VERSION.SDK_INT >= 16
                ? java.lang.Runtime.getRuntime().maxMemory() : 0;
    }

    public static void updateWindowSize(Context ctx) {
        net.kdt.pojavlaunch.Tools.updateWindowSize((android.app.Activity) ctx);
    }
}
