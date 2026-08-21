package com.miolauncher.backend;

/**
 * 自有输入桥：把触摸/按键直接推给游戏（libpojavexec 的 input_bridge）。
 * 不依赖 FCL 的 CallbackBridge sendData 协议。
 */
public final class NativeInput {
    private NativeInput() {}

    public static native void setInputReady(boolean ready);
    public static native void sendCursorPos(float x, float y);
    public static native void sendMouseButton(int button, int action, int mods);
    public static native void sendKey(int key, int scancode, int action, int mods);
    public static native void sendScroll(float x, float y);
    public static native void sendWindowSize(int width, int height);
    public static native int getSwapCount();
}
