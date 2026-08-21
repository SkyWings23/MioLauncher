package com.miolauncher.app.controls;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.miolauncher.app.R;
import com.miolauncher.backend.NativeInput;

/**
 * FCL 风格悬浮菜单：顶部黑色半圆下拉标签 + 右侧滑出深色菜单。
 * 菜单项：强制关闭 / 日志输出 / 发送自定义键码 / 快速设置 / 显示或隐藏控件。
 */
public class FclFloatingMenu extends FrameLayout {

    public interface Listener {
        void onForceClose();
        void onViewLog();
        void onSendCustomKey();
        void onQuickSettings();
        void onToggleControls();
    }

    private final Listener listener;
    private final View pullTab;
    private final LinearLayout menuPanel;
    private boolean menuOpen = false;

    public FclFloatingMenu(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setClipChildren(false);

        // ---- 可拖动悬浮球（菜单入口，可见） ----
        pullTab = new TextView(context) {
            private float downX, downY;
            private boolean dragging;
            {
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(0xFF3A6EA5);
                d.setStroke(dp(3), 0xFFFFFFFF);
                setBackground(d);
                setText("☰");
                setTextColor(Color.WHITE);
                setTextSize(24f);
                setGravity(Gravity.CENTER);
                setElevation(dp(6));
            }
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (!dragging && dx * dx + dy * dy > dp(6) * dp(6)) dragging = true;
                        if (dragging) {
                            ViewGroup.LayoutParams lp = getLayoutParams();
                            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                            flp.leftMargin = (int) Math.max(0, Math.min(getParentWidth() - getWidth(), getLeft() + dx));
                            flp.topMargin = (int) Math.max(0, Math.min(getParentHeight() - getHeight(), getTop() + dy));
                            flp.gravity = Gravity.TOP | Gravity.START;
                            setLayoutParams(flp);
                            downX = event.getRawX();
                            downY = event.getRawY();
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!dragging) toggle();
                        return true;
                }
                return super.onTouchEvent(event);
            }
            private int getParentWidth() {
                return ((ViewGroup) getParent()).getWidth();
            }
            private int getParentHeight() {
                return ((ViewGroup) getParent()).getHeight();
            }
        };
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(56), dp(56));
        tp.gravity = Gravity.TOP | Gravity.START;
        tp.leftMargin = dp(12);
        tp.topMargin = dp(90);
        addView(pullTab, tp);

        // ---- 右侧滑出菜单面板（FCL 深色 #272727） ----
        menuPanel = new LinearLayout(context);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(0xFF272727);
        bgd.setCornerRadii(new float[]{dp(12), dp(12), 0, 0, 0, 0, dp(12), dp(12)});
        menuPanel.setBackground(bgd);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.MATCH_PARENT);
        mp.gravity = Gravity.END;
        menuPanel.setTranslationX(dp(220));
        menuPanel.setVisibility(GONE);
        addView(menuPanel, mp);

        addItem("强制关闭", () -> { close(); if (listener != null) listener.onForceClose(); });
        addItem("日志输出", () -> { close(); if (listener != null) listener.onViewLog(); });
        addItem("发送自定义键码", () -> { close(); if (listener != null) listener.onSendCustomKey(); });
        addItem("快速设置", () -> { close(); if (listener != null) listener.onQuickSettings(); });
        addItem("显示或隐藏控件", () -> { close(); if (listener != null) listener.onToggleControls(); });
    }

    private void addItem(String text, Runnable action) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15f);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(18), dp(16), dp(18), dp(16));
        t.setOnClickListener(v -> action.run());
        menuPanel.addView(t);
    }

    public void toggle() {
        if (menuOpen) close(); else open();
    }

    public void open() {
        menuOpen = true;
        menuPanel.setVisibility(VISIBLE);
        ObjectAnimator anim = ObjectAnimator.ofFloat(menuPanel, "translationX", dp(220), 0f);
        anim.setDuration(180);
        anim.start();
    }

    public void close() {
        menuOpen = false;
        ObjectAnimator anim = ObjectAnimator.ofFloat(menuPanel, "translationX", 0f, dp(220));
        anim.setDuration(180);
        anim.start();
        menuPanel.postDelayed(() -> { if (!menuOpen) menuPanel.setVisibility(GONE); }, 190);
    }

    public boolean isMenuOpen() {
        return menuOpen;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (menuOpen && event.getX() < getWidth() - dp(220)) {
            close();
            return true;
        }
        return false;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
