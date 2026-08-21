package com.miolauncher.app.controls;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.miolauncher.backend.NativeInput;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏控制覆盖层（FCL 风格）：
 * 虚拟摇杆（左）+ 动作按钮（右簇/顶栏）+ 底部悬浮抽屉（点按向两侧展开菜单）+ 视角触摸区。
 */
public class GameControlOverlay extends FrameLayout
        implements GameControlButton.Listener, GameJoystick.KeySender {

    private float renderW = 2316f;
    private float renderH = 1080f;

    private final GameJoystick joystick;
    private final List<GameControlButton> buttons = new ArrayList<>();
    private final LinearLayout menuPanel;

    private boolean controlsVisible = true;
    private boolean lookEnabled = true;

    // 悬浮抽屉
    private final GameControlButton drawerBtn;
    private final List<GameControlButton> drawerSubs = new ArrayList<>();
    private boolean drawerOpen = false;

    // look area touch state
    private float downX = 0f, downY = 0f;
    private boolean dragging = false;

    private static final float TAP_SLOP = 20f;

    public GameControlOverlay(Context context) {
        super(context);
        setClickable(true);
        setClipChildren(false);

        // ---- 摇杆（左下） ----
        joystick = new GameJoystick(context, this, dp(90));
        FrameLayout.LayoutParams jp = new FrameLayout.LayoutParams(dp(200), dp(200));
        jp.gravity = Gravity.BOTTOM | Gravity.START;
        jp.setMargins(dp(10), 0, 0, dp(10));
        addView(joystick, jp);

        // ---- 动作按钮 ----
        // 右簇（底部）
        addButton(MioKeycodes.MOUSE_LEFT, "L", dp(50), dp(30), false);
        addButton(MioKeycodes.MOUSE_RIGHT, "R", dp(120), dp(30), false);
        addButton(MioKeycodes.MOUSE_MIDDLE, "M", dp(50), dp(95), false);
        addButton(MioKeycodes.KEY_LEFT_SHIFT, "Sneak", dp(120), dp(95), true);
        addButton(MioKeycodes.KEY_SPACE, "Jump", dp(50), dp(160), true);
        addButton(MioKeycodes.KEY_LEFT_CTRL, "Sprint", dp(120), dp(160), true);
        addButton(MioKeycodes.KEY_E, "Inv", dp(50), dp(225), false);
        // 顶栏
        addButton(MioKeycodes.KEY_ESCAPE, "||", dp(10), dp(10), true); // Pause
        addButton(MioKeycodes.ACTION_TOGGLE_GUI, "Hide", dp(90), dp(10), true);
        addButton(MioKeycodes.ACTION_OPEN_MENU, "☰", dp(170), dp(10), true);

        // ---- 底部悬浮抽屉 ----
        drawerBtn = new GameControlButton(context, this, 0, "≡", false, false, dp(36));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(72), dp(72));
        dlp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        dlp.setMargins(0, 0, 0, dp(14));
        addView(drawerBtn, dlp);
        drawerBtn.setOnClickListener(v -> toggleDrawer());
        buttons.add(drawerBtn);

        // 抽屉左侧子按钮：视角切换 / 聊天 / 丢物品 / 玩家列表 / 调试
        addDrawerSub(-1, 0, "视角", () -> tapKey(MioKeycodes.KEY_F5));
        addDrawerSub(-1, 1, "聊天", () -> tapKey(MioKeycodes.KEY_T));
        addDrawerSub(-1, 2, "丢弃", () -> tapKey(MioKeycodes.KEY_Q));
        addDrawerSub(-1, 3, "玩家", () -> tapKey(MioKeycodes.KEY_TAB));
        addDrawerSub(-1, 4, "调试", () -> tapKey(MioKeycodes.KEY_F3));
        // 抽屉右侧子按钮：潜行 / 疾跑 / 副手 / 鼠标 / 隐藏控件
        addDrawerSub(1, 0, "潜行", () -> tapKey(MioKeycodes.KEY_LEFT_SHIFT));
        addDrawerSub(1, 1, "疾跑", () -> tapKey(MioKeycodes.KEY_LEFT_CTRL));
        addDrawerSub(1, 2, "副手", () -> tapKey(MioKeycodes.KEY_F));
        addDrawerSub(1, 3, "鼠标", this::onToggleMouse);
        addDrawerSub(1, 4, "控件", () -> setControlsVisible(!controlsVisible));

        // ---- 悬浮菜单面板（默认隐藏） ----
        menuPanel = new LinearLayout(context);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setBackground(makePanelBg());
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        mp.gravity = Gravity.TOP | Gravity.END;
        mp.setMargins(0, dp(50), dp(10), 0);
        menuPanel.setVisibility(GONE);
        addView(menuPanel, mp);

        addMenuAction("显示/隐藏控件", () -> setControlsVisible(!controlsVisible));
        addMenuAction("离开游戏", () -> System.exit(0));
    }

    public void setRenderSize(float w, float h) {
        renderW = w;
        renderH = h;
    }

    public void releaseAll() {
        joystick.release();
        for (GameControlButton b : buttons) b.release();
    }

    private void tapKey(int keycode) {
        sendKey(keycode, 1);
        sendKey(keycode, 0);
    }

    private void addButton(int keycode, String label, float rightOffset, float bottomOrTopOffset, boolean topAligned) {
        GameControlButton b = new GameControlButton(getContext(), this, keycode, label, true, true, dp(34));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(68), dp(68));
        if (topAligned) {
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.setMargins(0, (int) bottomOrTopOffset, (int) rightOffset, 0);
        } else {
            lp.gravity = Gravity.BOTTOM | Gravity.END;
            lp.setMargins(0, 0, (int) rightOffset, (int) bottomOrTopOffset);
        }
        addView(b, lp);
        buttons.add(b);
    }

    /** 抽屉子按钮：绝对定位在抽屉两侧（side<0 左，side>0 右）。 */
    private void addDrawerSub(int side, int index, String label, Runnable action) {
        GameControlButton b = new GameControlButton(getContext(), this, 0, label, false, false, dp(28));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(56), dp(56));
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        int slot = (index + 1) * dp(64);
        lp.setMargins(side * slot, 0, 0, dp(22));
        b.setVisibility(GONE);
        b.setOnClickListener(v -> {
            collapseDrawer();
            action.run();
        });
        addView(b, lp);
        drawerSubs.add(b);
    }

    private void toggleDrawer() {
        drawerOpen = !drawerOpen;
        for (GameControlButton b : drawerSubs) {
            b.setVisibility(drawerOpen ? VISIBLE : GONE);
        }
    }

    private void collapseDrawer() {
        drawerOpen = false;
        for (GameControlButton b : drawerSubs) b.setVisibility(GONE);
    }

    private void addMenuAction(String text, Runnable action) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14f);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setBackground(makeActionBg());
        t.setOnClickListener(v -> {
            action.run();
            menuPanel.setVisibility(GONE);
        });
        menuPanel.addView(t);
    }

    public void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        for (GameControlButton b : buttons) b.setVisibility(visible ? VISIBLE : GONE);
        joystick.setVisibility(visible ? VISIBLE : GONE);
        if (!visible) collapseDrawer();
    }

    private float gx(MotionEvent e) {
        return e.getX() * renderW / getWidth();
    }

    private float gy(MotionEvent e) {
        return e.getY() * renderH / getHeight();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (menuPanel.getVisibility() == VISIBLE) {
                    menuPanel.setVisibility(GONE);
                    return true;
                }
                float gx = gx(event), gy = gy(event);
                downX = gx;
                downY = gy;
                dragging = false;
                NativeInput.sendCursorPos(gx, gy);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float gx = gx(event), gy = gy(event);
                NativeInput.sendCursorPos(gx, gy);
                float dx = gx - downX, dy = gy - downY;
                if (!dragging && dx * dx + dy * dy > TAP_SLOP * TAP_SLOP) {
                    dragging = true;
                    if (lookEnabled) NativeInput.sendMouseButton(MioKeycodes.MOUSE_LEFT, 1, 0);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float gx = gx(event), gy = gy(event);
                NativeInput.sendCursorPos(gx, gy);
                if (dragging) {
                    if (lookEnabled) NativeInput.sendMouseButton(MioKeycodes.MOUSE_LEFT, 0, 0);
                } else {
                    if (lookEnabled) {
                        NativeInput.sendMouseButton(MioKeycodes.MOUSE_LEFT, 1, 0);
                        NativeInput.sendMouseButton(MioKeycodes.MOUSE_LEFT, 0, 0);
                    }
                }
                dragging = false;
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    // ---- GameControlButton.Listener / GameJoystick.KeySender ----
    @Override
    public void onKey(int keycode, int action) {
        sendKey(keycode, action);
    }

    @Override
    public void onMouse(int button, int action) {
        NativeInput.sendMouseButton(button, action, 0);
    }

    @Override
    public void onToggleGui() {
        tapKey(MioKeycodes.KEY_F1);
    }

    @Override
    public void onToggleMouse() {
        lookEnabled = !lookEnabled;
    }

    @Override
    public void onOpenMenu() {
        menuPanel.setVisibility(menuPanel.getVisibility() == VISIBLE ? GONE : VISIBLE);
    }

    private void sendKey(int keycode, int action) {
        NativeInput.sendKey(keycode, 0, action, 0);
    }

    private static android.graphics.drawable.Drawable makePanelBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC222222);
        g.setCornerRadius(12);
        return g;
    }

    private static android.graphics.drawable.Drawable makeActionBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        return g;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
