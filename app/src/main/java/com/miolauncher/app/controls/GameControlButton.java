package com.miolauncher.app.controls;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

/**
 * FCL 风格圆形控制按钮。
 * keycode>0 为 GLFW 键位；负值走 MioKeycodes 的特殊动作（鼠标/开关）。
 */
public class GameControlButton extends TextView {

    public interface Listener {
        void onKey(int keycode, int action);
        void onMouse(int button, int action);
        void onToggleGui();
        void onToggleMouse();
        void onOpenMenu();
    }

    private final Listener listener;
    private final int keycode;
    private final boolean isToggle;
    private final boolean isSwipeable;
    private final int radius;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean pressed = false;

    public GameControlButton(Context context, Listener listener, int keycode,
                             String label, boolean isToggle, boolean isSwipeable, int radius) {
        super(context);
        this.listener = listener;
        this.keycode = keycode;
        this.isToggle = isToggle;
        this.isSwipeable = isSwipeable;
        this.radius = radius;
        setText(label);
        setGravity(android.view.Gravity.CENTER);
        setTextColor(0xFFFFFFFF);
        setTextSize(12f);
        setIncludeFontPadding(false);
        setBackground(makeBackground(0x55FFFFFF, radius, 2f, 0xFFFFFFFF));
        bgPaint.setColor(0x00000000);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                pressed = true;
                setBackground(makeBackground(0x88FFAA00, radius, 2f, 0xFFFFFFFF));
                triggerDown();
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                pressed = false;
                setBackground(makeBackground(0x55FFFFFF, radius, 2f, 0xFFFFFFFF));
                triggerUp();
                performClick();
                return true;
            }
        }
        return true;
    }

    private void triggerDown() {
        dispatch(keycode, 1);
    }

    private void triggerUp() {
        dispatch(keycode, 0);
    }

    private void dispatch(int code, int action) {
        switch (code) {
            case MioKeycodes.ACTION_LEFT_CLICK:
                if (listener != null) listener.onMouse(MioKeycodes.MOUSE_LEFT, action);
                break;
            case MioKeycodes.ACTION_RIGHT_CLICK:
                if (listener != null) listener.onMouse(MioKeycodes.MOUSE_RIGHT, action);
                break;
            case MioKeycodes.ACTION_MIDDLE_CLICK:
                if (listener != null) listener.onMouse(MioKeycodes.MOUSE_MIDDLE, action);
                break;
            case MioKeycodes.ACTION_TOGGLE_GUI:
                if (listener != null) listener.onToggleGui();
                break;
            case MioKeycodes.ACTION_TOGGLE_MOUSE:
                if (listener != null) listener.onToggleMouse();
                break;
            case MioKeycodes.ACTION_OPEN_MENU:
                if (listener != null) listener.onOpenMenu();
                break;
            default:
                if (code > 0 && listener != null) listener.onKey(code, action);
                break;
        }
    }

    public void release() {
        if (pressed) {
            pressed = false;
            setBackground(makeBackground(0x55FFFFFF, radius, 2f, 0xFFFFFFFF));
            triggerUp();
        }
    }

    private static android.graphics.drawable.Drawable makeBackground(int fill, int radius, float strokeW, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setStroke((int) strokeW, strokeColor);
        return g;
    }
}
