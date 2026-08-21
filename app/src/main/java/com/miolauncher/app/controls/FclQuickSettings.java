package com.miolauncher.app.controls;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * FCL 风格局内快速设置悬浮窗（右侧滑出）：分辨率 / 鼠标速度 / 陀螺仪 / 手势 / 鼠标抓取。
 */
public class FclQuickSettings extends FrameLayout {

    public interface Listener {
        void onResolutionChanged();
    }

    private final Listener listener;
    private final LinearLayout panel;
    private boolean open = false;

    public FclQuickSettings(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setClipChildren(false);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(0xFF272727);
        bgd.setCornerRadii(new float[]{dp(12), dp(12), 0, 0, 0, 0, dp(12), dp(12)});
        panel.setBackground(bgd);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(250), ViewGroup.LayoutParams.MATCH_PARENT);
        pp.gravity = Gravity.END;
        panel.setTranslationX(dp(250));
        panel.setVisibility(GONE);
        addView(panel, pp);

        addTitle("快速设置");

        addSeekbar("分辨率缩放", 50, 200,
                Math.round(LauncherPreferences.PREF_SCALE_FACTOR),
                p -> {
                    LauncherPreferences.PREF_SCALE_FACTOR = p;
                    if (listener != null) listener.onResolutionChanged();
                },
                "%");

        addSeekbar("鼠标速度", 10, 300,
                (int) (LauncherPreferences.PREF_MOUSESPEED * 100f),
                p -> LauncherPreferences.PREF_MOUSESPEED = p / 100f,
                "%");

        addSwitch("鼠标抓取", LauncherPreferences.PREF_MOUSE_GRAB_FORCE,
                (b, on) -> LauncherPreferences.PREF_MOUSE_GRAB_FORCE = on);

        addSwitch("禁用手势", LauncherPreferences.PREF_DISABLE_GESTURES,
                (b, on) -> LauncherPreferences.PREF_DISABLE_GESTURES = on);

        addSeekbar("长按触发(ms)", 100, 900,
                LauncherPreferences.PREF_LONGPRESS_TRIGGER,
                p -> LauncherPreferences.PREF_LONGPRESS_TRIGGER = p,
                "ms");

        addSwitch("启用陀螺仪", LauncherPreferences.PREF_GYRO_SMOOTHING,
                (b, on) -> LauncherPreferences.PREF_GYRO_SMOOTHING = on);
        addSwitch("陀螺仪 X 反转", LauncherPreferences.PREF_GYRO_INVERT_X,
                (b, on) -> LauncherPreferences.PREF_GYRO_INVERT_X = on);
        addSwitch("陀螺仪 Y 反转", LauncherPreferences.PREF_GYRO_INVERT_Y,
                (b, on) -> LauncherPreferences.PREF_GYRO_INVERT_Y = on);

        addSeekbar("陀螺仪灵敏度", 10, 300,
                (int) (LauncherPreferences.PREF_GYRO_SENSITIVITY * 100f),
                p -> LauncherPreferences.PREF_GYRO_SENSITIVITY = p / 100f,
                "%");
    }

    private void addTitle(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(16), dp(12), dp(10));
        panel.addView(t);
    }

    private void addSeekbar(String label, int min, int max, int value,
                            java.util.function.IntConsumer onChanged, String suffix) {
        TextView lbl = new TextView(getContext());
        lbl.setText(label);
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(14f);
        lbl.setPadding(dp(18), dp(12), dp(18), 0);
        panel.addView(lbl);

        SeekBar sb = new SeekBar(getContext());
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setPadding(dp(14), 0, dp(14), 0);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) onChanged.accept(min + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(sb);
    }

    private void addSwitch(String label, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        Switch sw = new Switch(getContext());
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(14f);
        sw.setChecked(checked);
        sw.setPadding(dp(14), dp(4), dp(14), dp(4));
        sw.setOnCheckedChangeListener(l);
        panel.addView(sw);
    }

    public void toggle() {
        if (open) close(); else open();
    }

    public void open() {
        open = true;
        panel.setVisibility(VISIBLE);
        panel.animate().translationX(0).setDuration(180).start();
    }

    public void close() {
        open = false;
        panel.animate().translationX(dp(250)).setDuration(180).start();
        panel.postDelayed(() -> { if (!open) panel.setVisibility(GONE); }, 190);
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (open && event.getX() < getWidth() - dp(250)) {
            close();
            return true;
        }
        return false;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
