package com.miolauncher.app.controls;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * 统合设置面板（FCL 风格）：
 * 可拖动悬浮球入口 + 右侧滑出设置面板（功能/鼠标/手柄/调试 4 分类）
 * 开：全屏遮罩压暗 + 面板亮 + 点外部关闭；关：完全穿透不拦截触摸。
 */
public class FclSettingsPanel extends FrameLayout {

    public interface Listener {
        void onForceClose();
        void onSendCustomKey();
        void onOpenQuickInput();
        void onOpenKeyBinding();
        void onOpenCascadeMenu();
        void onOpenMultiplayer();
        void onOpenTerracotta();
        void onViewLog();
        void onResolutionChanged();
        void onToggleControls();
        void onCustomControls();
    }

    private final Listener listener;
    private final View dim;
    private final LinearLayout panel;
    private final LinearLayout content;
    private final View ball;
    private boolean open = false;
    public final SharedPrefs prefs;

    public FclSettingsPanel(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        this.prefs = new SharedPrefs(context);
        setClipChildren(false);
        // 全局最高层：不被任何控件/键盘键位遮挡
        setTranslationZ(99999f);

        // ---- 遮罩（开时覆盖全屏压暗） ----
        dim = new View(context);
        dim.setBackgroundColor(0x99000000);
        dim.setVisibility(GONE);
        dim.setOnClickListener(v -> close());
        addView(dim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ---- 右侧设置面板 ----
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(0xFF1E1E1E);
        bgd.setCornerRadii(new float[]{dp(16), dp(16), 0, 0, 0, 0, dp(16), dp(16)});
        panel.setBackground(bgd);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(290), ViewGroup.LayoutParams.MATCH_PARENT);
        pp.gravity = Gravity.END;
        panel.setTranslationX(dp(290));
        panel.setVisibility(GONE);
        addView(panel, pp);

        // 面板头部：标题 + 返回
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(10), dp(10));
        TextView title = new TextView(context);
        title.setText("设置");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18f);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
        header.addView(title);

        TextView back = new TextView(context);
        back.setText("✕");
        back.setTextColor(Color.WHITE);
        back.setTextSize(20f);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(10), dp(6), dp(10), dp(6));
        back.setBackground(makeRound(0x33333333, dp(8)));
        back.setOnClickListener(v -> close());
        header.addView(back);
        panel.addView(header);

        // 内容滚动区
        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(20));
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildContent();

        // ---- 可拖动悬浮球（入口） ----
        ball = new TextView(context) {
            private float downX, downY;
            private boolean dragging;
            {
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(0xFF3A6EA5);
                d.setStroke(dp(2), 0xFFFFFFFF);
                setBackground(d);
                setText("☰");
                setTextColor(Color.WHITE);
                setTextSize(22f);
                setGravity(Gravity.CENTER);
                setElevation(dp(8));
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
            private int getParentWidth() { return ((ViewGroup) getParent()).getWidth(); }
            private int getParentHeight() { return ((ViewGroup) getParent()).getHeight(); }
        };
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(52), dp(52));
        bp.gravity = Gravity.TOP | Gravity.START;
        bp.leftMargin = dp(12);
        bp.topMargin = dp(90);
        addView(ball, bp);
        updateBallVisibility();
    }

    // ============ 内容构建 ============
    private void buildContent() {
        // 自定义控制（编辑模式）置顶，始终可见
        LinearLayout topRow = (LinearLayout) makeRow();
        TextView topLabel = makeLabel("自定义控制");
        topRow.addView(topLabel);
        TextView topBtn = new TextView(getContext());
        topBtn.setText("编辑");
        topBtn.setTextColor(Color.WHITE);
        topBtn.setTextSize(14f);
        topBtn.setGravity(Gravity.CENTER);
        topBtn.setPadding(dp(20), dp(8), dp(20), dp(8));
        topBtn.setBackground(makeRound(0xFF3A6EA5, dp(8)));
        topBtn.setOnClickListener(v -> { close(); if (listener != null) listener.onCustomControls(); });
        topRow.addView(topBtn);

        // ---------- 功能 ----------
        addSection("功能");
        addSwitch("锁定菜单键", prefs.lockMenuKey, v -> prefs.lockMenuKey = v);
        addSwitch("隐藏菜单键", prefs.hideMenuKey, v -> { prefs.hideMenuKey = v; updateBallVisibility(); });
        addSwitch("显示FPS", prefs.showFps, v -> { prefs.showFps = v; prefs.sp.edit().putBoolean("showFps", v).apply(); });
        addButton("联机菜单", "打开", () -> { close(); if (listener != null) listener.onOpenMultiplayer(); });
        addButton("陶瓦联机", "打开", () -> { close(); if (listener != null) listener.onOpenTerracotta(); });
        addButton("快捷输入", "管理", () -> { close(); if (listener != null) listener.onOpenQuickInput(); });
        addButton("发送键值", "发送", () -> { close(); if (listener != null) listener.onSendCustomKey(); });
        addSwitch("禁用软键盘自适应", prefs.disableSoftKeyboard, v -> prefs.disableSoftKeyboard = v);
        addSwitch("局内键盘按钮", prefs.showKeyboardButton, v -> { prefs.showKeyboardButton = v; prefs.sp.edit().putBoolean("showKeyboardButton", v).apply(); });
        addSlider("物品栏缩放", 0, 200, prefs.hotbarScale, v -> prefs.hotbarScale = v, "%");
        addSlider("窗口分辨率", 25, 200, prefs.windowResolution, v -> {
            prefs.windowResolution = v;
            LauncherPreferences.PREF_SCALE_FACTOR = Math.max(0.25f, v / 100f);
            if (listener != null) listener.onResolutionChanged();
        }, "%");
        addSlider("鼠标指针偏移", 0, 100, prefs.pointerOffset, v -> prefs.pointerOffset = v, "px");
        addSwitch("禁用手势", prefs.disableGestures, v -> { prefs.disableGestures = v; LauncherPreferences.PREF_DISABLE_GESTURES = v; });
        addSwitch("禁用仿基岩版手势", prefs.disableBedrockGestures, v -> prefs.disableBedrockGestures = v);
        addChoice("触控模式", new String[]{"建筑模式", "鼠标模式"}, prefs.touchMode, v -> prefs.touchMode = v);
        addSwitch("禁用左半屏触控", prefs.disableLeftTouch, v -> prefs.disableLeftTouch = v);

        // ---------- 鼠标 ----------
        addSection("鼠标");
        addSwitch("允许鼠标操纵", prefs.mouseClick, v -> { prefs.mouseClick = v; LauncherPreferences.PREF_ENABLE_MOUSE_CLICK = v; });
        addSlider("鼠标灵敏度", 25, 300, prefs.mouseSpeed, v -> { prefs.mouseSpeed = v; LauncherPreferences.PREF_MOUSESPEED = v / 100f; }, "%");
        addSlider("鼠标灵敏度(指针可见)", 25, 300, prefs.mouseSpeedPointer, v -> prefs.mouseSpeedPointer = v, "%");
        addSlider("鼠标尺寸", 5, 60, prefs.mouseSize, v -> { prefs.mouseSize = v; LauncherPreferences.PREF_MOUSESCALE = Math.max(0.5f, v / 15f); }, "dp");
        addSwitch("实体鼠标控制", prefs.physicalMouse, v -> prefs.physicalMouse = v);

        // ---------- 手柄 ----------
        addSection("手柄");
        addSwitch("禁用手柄映射", prefs.disableGamepadMap, v -> prefs.disableGamepadMap = v);
        addButton("重置映射", "重置", () -> prefs.resetGamepadMap());
        addButton("按键绑定", "打开", () -> { close(); if (listener != null) listener.onOpenKeyBinding(); });
        addSlider("调整死区", 50, 200, prefs.deadzone, v -> { prefs.deadzone = v; LauncherPreferences.PREF_DEADZONE_SCALE = v / 100f; }, "%");
        addSlider("陀螺仪灵敏度", 5, 300, prefs.gyroSensitivity, v -> { prefs.gyroSensitivity = v; LauncherPreferences.PREF_GYRO_SENSITIVITY = v / 100f; }, "");

        // ---------- 调试 ----------
        addSection("调试");
        addSwitch("显示内存", prefs.showMemory, v -> { prefs.showMemory = v; prefs.sp.edit().putBoolean("showMemory", v).apply(); });
        addSwitch("持续性性能模式", prefs.sustainedPerformance, v -> prefs.sustainedPerformance = v);
        addButton("显示日志", "查看", () -> { close(); if (listener != null) listener.onViewLog(); });
        addSwitch("自动显示日志", prefs.autoShowLog, v -> prefs.autoShowLog = v);
        addButton("强制退出", "退出", () -> { close(); if (listener != null) listener.onForceClose(); });
    }

    private void addSection(String name) {
        TextView t = new TextView(getContext());
        t.setText(name);
        t.setTextColor(0xFF7EA6FF);
        t.setTextSize(13f);
        t.setPadding(dp(18), dp(14), dp(18), dp(4));
        content.addView(t);
    }

    private View makeRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(6), dp(12), dp(6));
        content.addView(row);
        return row;
    }

    private TextView makeLabel(String text) {
        TextView l = new TextView(getContext());
        l.setText(text);
        l.setTextColor(Color.WHITE);
        l.setTextSize(14f);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1f));
        return l;
    }

    private void addSwitch(String label, boolean checked, java.util.function.Consumer<Boolean> on) {
        LinearLayout row = (LinearLayout) makeRow();
        row.addView(makeLabel(label));
        Switch sw = new Switch(getContext());
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, v) -> on.accept(v));
        row.addView(sw);
    }

    private void addSlider(String label, int min, int max, int value, java.util.function.IntConsumer on, String suffix) {
        LinearLayout row = (LinearLayout) makeRow();
        TextView labelTv = makeLabel(label);
        row.addView(labelTv);
        TextView valTv = new TextView(getContext());
        valTv.setTextColor(0xFFAAAAAA);
        valTv.setTextSize(13f);
        valTv.setText(" " + value + suffix);
        row.addView(valTv);

        SeekBar sb = new SeekBar(getContext());
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setPadding(0, 0, 0, 0);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) { on.accept(min + p); valTv.setText(" " + (min + p) + suffix); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        slp.setMargins(dp(18), 0, dp(12), 0);
        content.addView(sb, slp);
    }

    private void addButton(String label, String btnText, Runnable action) {
        LinearLayout row = (LinearLayout) makeRow();
        row.addView(makeLabel(label));
        TextView b = new TextView(getContext());
        b.setText(btnText);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(6), dp(16), dp(6));
        b.setBackground(makeRound(0xFF3A6EA5, dp(8)));
        b.setOnClickListener(v -> action.run());
        row.addView(b);
    }

    private void addChoice(String label, String[] options, int index, java.util.function.IntConsumer on) {
        LinearLayout row = (LinearLayout) makeRow();
        row.addView(makeLabel(label));
        TextView b = new TextView(getContext());
        b.setText(options[index]);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(6), dp(16), dp(6));
        b.setBackground(makeRound(0xFF3A6EA5, dp(8)));
        b.setOnClickListener(v -> {
            int next = (index + 1) % options.length;
            b.setText(options[next]);
            on.accept(next);
        });
        row.addView(b);
    }

    // ============ 打开/关闭 ============
    public void toggle() {
        if (open) close(); else open();
    }

    public void open() {
        open = true;
        dim.setVisibility(VISIBLE);
        panel.setVisibility(VISIBLE);
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(200).start();
        panel.animate().translationX(0).setDuration(220).start();
    }

    public void close() {
        open = false;
        dim.animate().alpha(0f).setDuration(200).withEndAction(() -> dim.setVisibility(GONE)).start();
        panel.animate().translationX(dp(290)).setDuration(220)
                .withEndAction(() -> { if (!open) panel.setVisibility(GONE); }).start();
    }

    public boolean isOpen() { return open; }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 关闭时完全穿透，不拦截任何触摸
        if (!open) return false;
        // 打开时：点面板外(左侧暗区)关闭
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && event.getX() < getWidth() - dp(290)) {
            close();
            return true;
        }
        return false;
    }

    public void updateBallVisibility() {
        ball.setVisibility(prefs.hideMenuKey ? GONE : VISIBLE);
        if (prefs.lockMenuKey) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ball.getLayoutParams();
            lp.topMargin = dp(90);
            lp.leftMargin = dp(12);
            ball.setLayoutParams(lp);
        }
    }

    private static GradientDrawable makeRound(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 偏好存储 */
    public static class SharedPrefs {
        private final android.content.SharedPreferences sp;
        public boolean lockMenuKey, hideMenuKey, showFps, disableSoftKeyboard, disableGestures,
                disableBedrockGestures, disableLeftTouch, physicalMouse, disableGamepadMap,
                sustainedPerformance, autoShowLog, showMemory, mouseClick, showKeyboardButton;
        public int hotbarScale, windowResolution, pointerOffset, touchMode, mouseControlMode,
                mouseSpeed, mouseSpeedPointer, mouseSize, deadzone, gyroSensitivity;

        public SharedPrefs(Context ctx) {
            sp = ctx.getSharedPreferences("mio_settings", Context.MODE_PRIVATE);
            lockMenuKey = sp.getBoolean("lockMenuKey", false);
            hideMenuKey = sp.getBoolean("hideMenuKey", false);
            showFps = sp.getBoolean("showFps", false);
            disableSoftKeyboard = sp.getBoolean("disableSoftKeyboard", false);
            disableGestures = sp.getBoolean("disableGestures", false);
            disableBedrockGestures = sp.getBoolean("disableBedrockGestures", false);
            disableLeftTouch = sp.getBoolean("disableLeftTouch", false);
            physicalMouse = sp.getBoolean("physicalMouse", false);
            disableGamepadMap = sp.getBoolean("disableGamepadMap", false);
            sustainedPerformance = sp.getBoolean("sustainedPerformance", false);
            autoShowLog = sp.getBoolean("autoShowLog", false);
            showMemory = sp.getBoolean("showMemory", false);
            mouseClick = sp.getBoolean("mouseClick", false);
            showKeyboardButton = sp.getBoolean("showKeyboardButton", false);
            LauncherPreferences.PREF_ENABLE_MOUSE_CLICK = mouseClick;
            hotbarScale = sp.getInt("hotbarScale", 0);
            windowResolution = sp.getInt("windowResolution", 100);
            pointerOffset = sp.getInt("pointerOffset", 0);
            touchMode = sp.getInt("touchMode", 0);
            mouseControlMode = sp.getInt("mouseControlMode", 0);
            mouseSpeed = sp.getInt("mouseSpeed", 100);
            mouseSpeedPointer = sp.getInt("mouseSpeedPointer", 200);
            mouseSize = sp.getInt("mouseSize", 15);
            deadzone = sp.getInt("deadzone", 100);
            gyroSensitivity = sp.getInt("gyroSensitivity", 10);
            LauncherPreferences.PREF_DISABLE_GESTURES = disableGestures;
            LauncherPreferences.PREF_MOUSESPEED = mouseSpeed / 100f;
            LauncherPreferences.PREF_MOUSESCALE = Math.max(0.5f, mouseSize / 15f);
            LauncherPreferences.PREF_DEADZONE_SCALE = deadzone / 100f;
            LauncherPreferences.PREF_GYRO_SENSITIVITY = gyroSensitivity / 100f;
            LauncherPreferences.PREF_SCALE_FACTOR = Math.max(0.25f, windowResolution / 100f);
        }

        public void setLockMenuKey(boolean v) { lockMenuKey = v; sp.edit().putBoolean("lockMenuKey", v).apply(); }
        public void setHideMenuKey(boolean v) { hideMenuKey = v; sp.edit().putBoolean("hideMenuKey", v).apply(); }
        public void resetGamepadMap() {
            sp.edit().remove("gamepadMap").apply();
        }
    }
}
