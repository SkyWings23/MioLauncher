package org.libsdl.app;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;

public class SDLSurface extends android.view.SurfaceView implements SurfaceHolder.Callback {
    public SDLSurface(Context ctx) { super(ctx); }
    public static void setNativeSurface(Surface surface) {}
    public void surfaceCreated(SurfaceHolder holder) {}
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    public void surfaceDestroyed(SurfaceHolder holder) {}
    public void nativeResize(int width, int height) {}
}
