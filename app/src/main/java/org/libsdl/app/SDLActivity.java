package org.libsdl.app;

import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;

public class SDLActivity extends Activity {
    public static SDLGenericMotionListener_API14 getMotionListener() {
        return (v, event) -> false;
    }
    public static void externalInitialize(SDLSurface surface, ViewGroup layout, Surface nativeSurface) {}
    public static SDLSurface getSDLSurface() { return null; }
    public static void handleKeyEvent(View view, int keyCode, KeyEvent event, Object unused) {}
    public static void handleKeyEvent(View view, int keyCode, KeyEvent event) {}
    public static void onNativeMouse(int state, int action, float x, float y, boolean relative) {}
    public static void onNativeKeyDown(int keyCode) {}
    public static void onNativeKeyUp(int keyCode) {}
    public static Context getContext() { return null; }
}
