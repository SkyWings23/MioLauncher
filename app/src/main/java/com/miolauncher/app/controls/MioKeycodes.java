package com.miolauncher.app.controls;

/** GLFW keycodes used by the control buttons (aligned with FCL default layout). */
public final class MioKeycodes {
    private MioKeycodes() {}

    public static final int KEY_SPACE = 32;
    public static final int KEY_E = 69;
    public static final int KEY_Q = 81;
    public static final int KEY_W = 87;
    public static final int KEY_A = 65;
    public static final int KEY_S = 83;
    public static final int KEY_D = 68;
    public static final int KEY_T = 84;
    public static final int KEY_F = 70;
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_TAB = 258;
    public static final int KEY_F1 = 290;
    public static final int KEY_F3 = 292;
    public static final int KEY_F5 = 294;
    public static final int KEY_LEFT_SHIFT = 340;
    public static final int KEY_LEFT_CTRL = 341;

    public static final int MOUSE_LEFT = 0;
    public static final int MOUSE_RIGHT = 1;
    public static final int MOUSE_MIDDLE = 2;

    /** -2 = toggle GUI, -3 = left click, -4 = right click, -5 = toggle mouse, -6 = middle click, -9 = open menu (FCL semantics). */
    public static final int ACTION_TOGGLE_GUI = -2;
    public static final int ACTION_LEFT_CLICK = -3;
    public static final int ACTION_RIGHT_CLICK = -4;
    public static final int ACTION_TOGGLE_MOUSE = -5;
    public static final int ACTION_MIDDLE_CLICK = -6;
    public static final int ACTION_OPEN_MENU = -9;
}
