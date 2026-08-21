package com.miolauncher.app.controls;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * FCL 风格虚拟摇杆：拖动摇杆头输出方向，映射 W/A/S/D。
 */
public class GameJoystick extends View {

    public interface KeySender {
        void onKey(int keycode, int action);
    }

    private final KeySender keySender;
    private final float radius;
    private final float knobRadius;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float knobX = 0f;
    private float knobY = 0f;
    private int activeKey = 0;

    public GameJoystick(Context context, KeySender keySender, float radius) {
        super(context);
        this.keySender = keySender;
        this.radius = radius;
        this.knobRadius = radius * 0.45f;
        bgPaint.setColor(0x40FFFFFF);
        bgPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(0x99FFFFFF);
        knobPaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        // base circle
        canvas.drawCircle(cx, cy, radius, bgPaint);
        // knob
        canvas.drawCircle(cx + knobX, cy + knobY, knobRadius, knobPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - cx;
                float dy = event.getY() - cy;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > radius) {
                    dx = dx / dist * radius;
                    dy = dy / dist * radius;
                }
                knobX = dx;
                knobY = dy;
                updateKeys(dx, dy);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                knobX = 0f;
                knobY = 0f;
                updateKeys(0f, 0f);
                invalidate();
                return true;
            }
        }
        return true;
    }

    private void updateKeys(float dx, float dy) {
        float dead = radius * 0.15f;
        boolean up = dy < -dead;
        boolean down = dy > dead;
        boolean left = dx < -dead;
        boolean right = dx > dead;
        int keys = 0;
        if (up) keys |= 1 << 0;
        if (down) keys |= 1 << 1;
        if (left) keys |= 1 << 2;
        if (right) keys |= 1 << 3;

        int[] codes = {
                MioKeycodes.KEY_W, MioKeycodes.KEY_S,
                MioKeycodes.KEY_A, MioKeycodes.KEY_D};
        for (int i = 0; i < 4; i++) {
            boolean active = (keys & (1 << i)) != 0;
            boolean wasActive = (activeKey & (1 << i)) != 0;
            if (active && !wasActive) {
                if (keySender != null) keySender.onKey(codes[i], 1);
            } else if (!active && wasActive) {
                if (keySender != null) keySender.onKey(codes[i], 0);
            }
        }
        activeKey = keys;
    }

    public void release() {
        knobX = 0f;
        knobY = 0f;
        updateKeys(0f, 0f);
        invalidate();
    }
}
