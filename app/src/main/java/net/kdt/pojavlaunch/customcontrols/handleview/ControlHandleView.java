package net.kdt.pojavlaunch.customcontrols.handleview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;

/** Resize handle shown at the corner of the currently edited control. */
public class ControlHandleView extends View {

    public ControlHandleView(Context context) {
        super(context);
        init();
    }

    public ControlHandleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ControlInterface mView;
    private float mXOffset, mYOffset;
    private int mSize;

    private final ViewTreeObserver.OnPreDrawListener mPositionListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            if(mView == null || !mView.getControlView().isShown()){
                hide();
                return true;
            }

            setX(mView.getControlView().getX() + mView.getControlView().getWidth() - mSize/2f);
            setY(mView.getControlView().getY() + mView.getControlView().getHeight() - mSize/2f);
            return true;
        }
    };

    private void init(){
        mSize = Math.round(22f * getResources().getDisplayMetrics().density);
        setLayoutParams(new ViewGroup.LayoutParams(mSize, mSize));
        setTranslationZ(10.5F);
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth()/2f, cy = getHeight()/2f, r = Math.min(getWidth(), getHeight())/2f - dp(1.5f);
        mPaint.setColor(0xFFFFFFFF);
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, r, mPaint);
        mPaint.setColor(0xFF2C3E50);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(2));
        canvas.drawCircle(cx, cy, r - dp(1), mPaint);
    }

    public void setControlButton(ControlInterface controlInterface){
        if(mView != null) mView.getControlView().getViewTreeObserver().removeOnPreDrawListener(mPositionListener);

        setVisibility(VISIBLE);
        mView = controlInterface;
        if(mView != null) {
            mView.getControlView().getViewTreeObserver().addOnPreDrawListener(mPositionListener);
            setX(mView.getControlView().getX() + mView.getControlView().getWidth() - mSize/2f);
            setY(mView.getControlView().getY() + mView.getControlView().getHeight() - mSize/2f);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                mXOffset = event.getX();
                mYOffset = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                setX(getX() + event.getX() - mXOffset);
                setY(getY() + event.getY() - mYOffset);

                mView.getProperties().setWidth(getX() + mSize/2f - mView.getControlView().getX());
                mView.getProperties().setHeight(getY() + mSize/2f - mView.getControlView().getY());
                mView.regenerateDynamicCoordinates();
                break;
        }

        return true;
    }

    public void hide(){
        if(mView != null)
            mView.getControlView().getViewTreeObserver().removeOnPreDrawListener(mPositionListener);
        setVisibility(GONE);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
