package io.github.controlwear.virtual.joystick.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * MioLauncher 内置虚拟摇杆（替代 virtual-joystick-android 依赖，API 兼容）。
 */
public class JoystickView extends View {
    public interface OnMoveListener {
        void onMove(int angle, int strength);
        void onForwardLock(boolean isLocked);
    }

    private OnMoveListener mListener;
    private int mDeadzone = 0;
    private boolean mFixedCenter = false;
    private boolean mAutoReCenter = true;
    private int mForwardLockDistance = 0;
    private float mKnobX = 0, mKnobY = 0;
    private boolean mForwardLocked = false;
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public JoystickView(Context context) {
        super(context);
        mBgPaint.setColor(0x40000000);
        mKnobPaint.setColor(0x99000000);
    }

    public void setOnMoveListener(OnMoveListener l) { mListener = l; }
    public void setDeadzone(int d) { mDeadzone = d; }
    public void setFixedCenter(boolean fixed) { mFixedCenter = fixed; }
    public void setAutoReCenterButton(boolean auto) { mAutoReCenter = auto; }
    public void setForwardLockDistance(int d) { mForwardLockDistance = d; }
    public void setBorderWidth(int w) {}
    public void setBorderColor(int c) {}
    public void setBackgroundColor(int c) { mBgPaint.setColor(c); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) * 0.92f;
        canvas.drawCircle(cx, cy, r, mBgPaint);
        float kr = r * 0.45f;
        canvas.drawCircle(cx + mKnobX, cy + mKnobY, kr, mKnobPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) * 0.92f;
        android.util.Log.i("MioCtrl", "joystick touch " + event.getActionMasked());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - cx, dy = event.getY() - cy;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > r) { dx = dx / dist * r; dy = dy / dist * r; }
                mKnobX = dx; mKnobY = dy;
                int strength = (int) (Math.hypot(dx, dy) / r * 100);
                if (mFixedCenter && mForwardLockDistance > 0 && Math.abs(dx) < mForwardLockDistance) {
                    if (!mForwardLocked) { mForwardLocked = true; if (mListener != null) mListener.onForwardLock(true); }
                } else if (mForwardLocked) {
                    mForwardLocked = false; if (mListener != null) mListener.onForwardLock(false);
                }
                int angle = 0;
                if (strength > mDeadzone) angle = (int) (Math.toDegrees(Math.atan2(-dy, dx)) + 360) % 360;
                else { strength = 0; mKnobX = 0; mKnobY = 0; }
                android.util.Log.i("MioCtrl", "joystick move angle=" + angle + " strength=" + strength);
                if (mListener != null) mListener.onMove(angle, strength);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mKnobX = 0; mKnobY = 0;
                if (mForwardLocked) { mForwardLocked = false; if (mListener != null) mListener.onForwardLock(false); }
                if (mListener != null) mListener.onMove(0, 0);
                invalidate();
                return true;
            }
        }
        return true;
    }
}
