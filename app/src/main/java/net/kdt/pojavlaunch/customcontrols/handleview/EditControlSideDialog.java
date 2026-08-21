package net.kdt.pojavlaunch.customcontrols.handleview;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;
import net.kdt.pojavlaunch.utils.interfaces.SimpleItemSelectedListener;
import net.kdt.pojavlaunch.utils.interfaces.SimpleSeekBarListener;
import net.kdt.pojavlaunch.utils.interfaces.SimpleTextWatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 控件编辑面板（左侧滑出）：名称/尺寸/样式预设/虚拟键盘键位/开关/描边/圆角/透明度/颜色/可见性。
 */
public class EditControlSideDialog extends FrameLayout {

    private final ControlLayout mLayout;
    public boolean internalChanges = false;
    private ControlInterface mCurrentlyEditedButton;

    private final LinearLayout mPanel;
    private final ScrollView mScroll;
    private final LinearLayout mContent;
    private View mBtnBar;
    private View mScrim;

    private EditText mNameEditText, mWidthEditText, mHeightEditText;
    private Switch mToggleSwitch, mPassthroughSwitch, mSwipeableSwitch, mForwardLockSwitch, mAbsoluteTrackingSwitch;
    private Switch mOpenKeyboardSwitch;
    private Spinner mOrientationSpinner;
    private final TextView[] mKeycodeTextviews = new TextView[4];
    private final LinearLayout[] mKeyRows = new LinearLayout[4];
    private final int[] mActiveSlots = new int[4];
    private int mEditingSlot = 0;
    private SeekBar mStrokeWidthSeekbar, mCornerRadiusSeekbar, mAlphaSeekbar;
    private TextView mStrokePercentTextView, mCornerRadiusPercentTextView, mAlphaPercentTextView;
    private TextView mSelectBackgroundColor, mSelectStrokeColor;
    private CheckBox mDisplayInGameCheckbox, mDisplayInMenuCheckbox;
    private List<String> mSpecialArray;

    // 装饰性标签
    private TextView mOrientationTextView, mMappingTextView, mNameTextView,
            mCornerRadiusTextView, mVisibilityTextView, mSizeTextview, mSizeXTextView;

    // 样式预设
    private LinearLayout mPresetChips;

    // 虚拟键盘
    private View mKeyboardScrim;
    private LinearLayout mKeyboardPanel;
    private TextView mKeyboardTitle;
    private LinearLayout mKeyGrid;

    // 颜色选择条
    private LinearLayout mColorStrip;
    private boolean mEditingBackground = true;

    public EditControlSideDialog(Context context, ControlLayout layout) {
        super(context);
        mLayout = layout;
        setClipChildren(false);
        setTranslationZ(99998f);

        // 遮罩
        mScrim = new View(context);
        mScrim.setBackgroundColor(0x99000000);
        mScrim.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mScrim.setOnClickListener(v -> mLayout.removeEditWindow());
        addView(mScrim);

        // 左侧面板
        mPanel = new LinearLayout(context);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21E1E24);
        bg.setCornerRadii(new float[]{0, 0, dp(14), dp(14), dp(14), dp(14), 0, 0});
        mPanel.setBackground(bg);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.82f),
                ViewGroup.LayoutParams.MATCH_PARENT);
        pp.gravity = Gravity.START;
        mPanel.setLayoutParams(pp);
        mPanel.setTranslationX(-Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.82f) - dp(20));
        addView(mPanel);

        buildHeader();
        mScroll = new ScrollView(context);
        mScroll.setFillViewport(true);
        mContent = new LinearLayout(context);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mContent.setPadding(0, 0, 0, dp(8));
        mScroll.addView(mContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mPanel.addView(mScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildContent();
        buildButtonBar();
        buildColorStrip();
        buildKeyboard();
    }

    public EditControlSideDialog(Context context, ControlLayout layout, ControlInterface button) {
        this(context, layout);
        setCurrentlyEditedButton(button);
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(10), dp(10));
        TextView title = new TextView(getContext());
        title.setText("编辑控件");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16f);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        TextView close = new TextView(getContext());
        close.setText("✕");
        close.setTextColor(0xFFCCCCCC);
        close.setTextSize(18f);
        close.setPadding(dp(12), dp(4), dp(12), dp(4));
        close.setOnClickListener(v -> disappear(true));
        header.addView(close);
        mPanel.addView(header);
    }

    private void buildContent() {
        mNameTextView = new TextView(getContext());
        mNameTextView.setText("名称");
        mContent.addView(makeFieldLabel(mNameTextView));
        mNameEditText = makeEditText();
        makeFieldWrap(mNameEditText);

        mSizeTextview = new TextView(getContext());
        mSizeTextview.setText("尺寸 (px)");
        mContent.addView(makeFieldLabel(mSizeTextview));
        LinearLayout sizeRow = new LinearLayout(getContext());
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        mSizeXTextView = new TextView(getContext());
        mSizeXTextView.setText("宽");
        mSizeXTextView.setTextColor(0xFFCCCCCC);
        mSizeXTextView.setTextSize(13f);
        mWidthEditText = makeEditText();
        mWidthEditText.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
        TextView x = new TextView(getContext());
        x.setText("  ×  ");
        x.setTextColor(0xFFCCCCCC);
        TextView yl = new TextView(getContext());
        yl.setText("高");
        yl.setTextColor(0xFFCCCCCC);
        yl.setTextSize(13f);
        mHeightEditText = makeEditText();
        mHeightEditText.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
        sizeRow.addView(mSizeXTextView);
        sizeRow.addView(mWidthEditText);
        sizeRow.addView(x);
        sizeRow.addView(yl);
        sizeRow.addView(mHeightEditText);
        makeFieldWrap(sizeRow);

        // 样式预设
        TextView presetLabel = new TextView(getContext());
        presetLabel.setText("样式预设");
        mContent.addView(makeFieldLabel(presetLabel));
        HorizontalScrollView psv = new HorizontalScrollView(getContext());
        psv.setHorizontalScrollBarEnabled(false);
        mPresetChips = new LinearLayout(getContext());
        mPresetChips.setOrientation(LinearLayout.HORIZONTAL);
        mPresetChips.setPadding(dp(12), dp(2), dp(12), dp(2));
        psv.addView(mPresetChips, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mContent.addView(psv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildPresetChips();

        // 键位映射（虚拟键盘）
        mMappingTextView = new TextView(getContext());
        mMappingTextView.setText("键位映射（点键位打开虚拟键盘）");
        mContent.addView(makeFieldLabel(mMappingTextView));
        for (int i = 0; i < 4; i++) {
            mActiveSlots[i] = 0;
            LinearLayout inner = new LinearLayout(getContext());
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(getContext());
            label.setText("键" + (i + 1));
            label.setTextColor(0xFFCCCCCC);
            label.setTextSize(13f);
            label.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(36)));
            inner.addView(label);
            mKeycodeTextviews[i] = new TextView(getContext());
            mKeycodeTextviews[i].setTextColor(Color.WHITE);
            mKeycodeTextviews[i].setTextSize(13f);
            mKeycodeTextviews[i].setPadding(dp(6), 0, 0, 0);
            mKeycodeTextviews[i].setLayoutParams(new LinearLayout.LayoutParams(0, dp(36), 1f));
            int slot = i;
            mKeycodeTextviews[i].setOnClickListener(v -> showKeyboard(slot));
            inner.addView(mKeycodeTextviews[i]);
            TextView clear = new TextView(getContext());
            clear.setText("✕");
            clear.setTextColor(0xFFE57373);
            clear.setTextSize(14f);
            clear.setGravity(Gravity.CENTER);
            clear.setPadding(dp(10), 0, dp(4), 0);
            clear.setOnClickListener(v -> {
                mActiveSlots[slot] = 0;
                refreshKeyRow(slot);
            });
            inner.addView(clear);
            mKeyRows[i] = makeFieldWrap(inner);
        }

        // 开关
        mToggleSwitch = makeSwitch("切换开关");
        mPassthroughSwitch = makeSwitch("鼠标穿透");
        mSwipeableSwitch = makeSwitch("可滑动");
        mForwardLockSwitch = makeSwitch("摇杆固定");
        mAbsoluteTrackingSwitch = makeSwitch("摇杆绝对追踪");
        mOpenKeyboardSwitch = makeSwitch("呼出输入法（点击时打开键盘）");
        mContent.addView(mToggleSwitch);
        mContent.addView(mPassthroughSwitch);
        mContent.addView(mSwipeableSwitch);
        mContent.addView(mForwardLockSwitch);
        mContent.addView(mAbsoluteTrackingSwitch);
        mContent.addView(mOpenKeyboardSwitch);

        mOrientationTextView = new TextView(getContext());
        mOrientationTextView.setText("抽屉方向");
        mContent.addView(makeFieldLabel(mOrientationTextView));
        mOrientationSpinner = new Spinner(getContext());
        mOrientationSpinner.setBackgroundColor(0xFF444444);
        mOrientationSpinner.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        makeFieldWrap(mOrientationSpinner);
        loadOrientationAdapter();

        addSeekbarRow("描边宽度", 40);
        mStrokeWidthSeekbar = mLastSeekbar;
        mStrokePercentTextView = mLastPercent;
        addSeekbarRow("圆角", 100);
        mCornerRadiusSeekbar = mLastSeekbar;
        mCornerRadiusPercentTextView = mLastPercent;
        mCornerRadiusTextView = mLastLabel;
        addSeekbarRow("透明度", 100);
        mAlphaSeekbar = mLastSeekbar;
        mAlphaPercentTextView = mLastPercent;

        TextView colorLabel = new TextView(getContext());
        colorLabel.setText("颜色");
        mContent.addView(makeFieldLabel(colorLabel));
        LinearLayout colorRow = new LinearLayout(getContext());
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        mSelectBackgroundColor = makeColorButton("背景色");
        mSelectStrokeColor = makeColorButton("描边色");
        colorRow.addView(mSelectBackgroundColor);
        colorRow.addView(mSelectStrokeColor);
        makeFieldWrap(colorRow);

        mVisibilityTextView = new TextView(getContext());
        mVisibilityTextView.setText("可见性");
        mContent.addView(makeFieldLabel(mVisibilityTextView));
        LinearLayout visRow = new LinearLayout(getContext());
        visRow.setOrientation(LinearLayout.HORIZONTAL);
        visRow.setGravity(Gravity.CENTER_VERTICAL);
        mDisplayInGameCheckbox = new CheckBox(getContext());
        mDisplayInGameCheckbox.setText("游戏内");
        mDisplayInGameCheckbox.setTextColor(Color.WHITE);
        mDisplayInMenuCheckbox = new CheckBox(getContext());
        mDisplayInMenuCheckbox.setText("游戏外");
        mDisplayInMenuCheckbox.setTextColor(Color.WHITE);
        visRow.addView(mDisplayInGameCheckbox);
        visRow.addView(mDisplayInMenuCheckbox);
        makeFieldWrap(visRow);

        setupRealTimeListeners();
        mSpecialArray = ControlData.buildSpecialButtonArray();
    }

    // ---------------- 样式预设 ----------------
    private static final String PRESET_PREFS = "mio_settings";
    private static final String PRESET_KEY = "button_presets";

    private void buildPresetChips() {
        mPresetChips.removeAllViews();
        // 内置预设
        addPresetChip("经典", 0x4D000000, 0xFFFFFFFF, 0, 0, 1f, 1f);
        addPresetChip("实心黑", 0xFF000000, 0xFFFFFFFF, 0, 0, 1f, 1f);
        addPresetChip("红描边", 0x4D000000, 0xFFE53935, 2, 4, 1f, 1f);
        addPresetChip("绿描边", 0x4D000000, 0xFF43A047, 2, 4, 1f, 1f);
        addPresetChip("蓝圆角", 0x4D1565C0, 0xFF64B5F6, 1, 30, 1f, 1f);
        addPresetChip("半透明", 0x33000000, 0xFFFFFFFF, 0, 0, 0.5f, 1f);
        addPresetChip("大按钮", 0x4D000000, 0xFFFFFFFF, 0, 0, 1f, 1.5f);
        addPresetChip("小按钮", 0x4D000000, 0xFFFFFFFF, 0, 0, 1f, 0.7f);
        // 自定义预设
        List<JSONObject> custom = loadCustomPresets();
        for (JSONObject p : custom) {
            addPresetChip(p.optString("name", "预设"),
                    p.optInt("bg", 0x4D000000), p.optInt("stroke", 0xFFFFFFFF),
                    (float) p.optDouble("sw", 0), (float) p.optDouble("radius", 0),
                    (float) p.optDouble("opacity", 1),
                    p.optInt("w", -1), p.optInt("h", -1));
        }
        // 保存当前样式
        TextView save = presetChipView("＋保存", 0xFF27AE60);
        save.setOnClickListener(v -> promptSavePreset());
        mPresetChips.addView(save);
    }

    private void addPresetChip(String name, int bg, int stroke, float sw, float radius, float opacity, float sizeScale) {
        TextView c = presetChipView(name, 0xFF3A6EA5);
        c.setOnClickListener(v -> applyPreset(bg, stroke, sw, radius, opacity, sizeScale, -1, -1));
        mPresetChips.addView(c);
    }

    private void addPresetChip(String name, int bg, int stroke, float sw, float radius, float opacity, int w, int h) {
        TextView c = presetChipView(name, 0xFF8E24AA);
        c.setOnClickListener(v -> applyPreset(bg, stroke, sw, radius, opacity, 1f, w, h));
        mPresetChips.addView(c);
    }

    private TextView presetChipView(String text, int color) {
        TextView c = new TextView(getContext());
        c.setText(text);
        c.setTextColor(Color.WHITE);
        c.setTextSize(12f);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private List<JSONObject> loadCustomPresets() {
        List<JSONObject> out = new ArrayList<>();
        try {
            SharedPreferences sp = getContext().getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(PRESET_KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } catch (Exception e) {
            // ignore
        }
        return out;
    }

    private void saveCustomPreset(JSONObject p) {
        try {
            SharedPreferences sp = getContext().getSharedPreferences(PRESET_PREFS, Context.MODE_PRIVATE);
            List<JSONObject> all = loadCustomPresets();
            all.add(p);
            JSONArray arr = new JSONArray();
            for (JSONObject o : all) arr.put(o);
            sp.edit().putString(PRESET_KEY, arr.toString()).apply();
        } catch (Exception e) {
            // ignore
        }
        buildPresetChips();
    }

    private void promptSavePreset() {
        final EditText input = new EditText(getContext());
        input.setTextColor(Color.WHITE);
        input.setTextSize(14f);
        input.setSingleLine(true);
        input.setHint("输入预设名称");
        input.setHintTextColor(0xFF888888);
        input.setBackgroundColor(0xFF333333);
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21E1E24);
        bg.setCornerRadius(dp(10));
        box.setBackground(bg);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(300), dp(60));
        bp.gravity = Gravity.CENTER;
        box.setLayoutParams(bp);
        box.addView(input, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView ok = new TextView(getContext());
        ok.setText("保存");
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(13f);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable og = new GradientDrawable();
        og.setColor(0xFF27AE60);
        og.setCornerRadius(dp(8));
        ok.setBackground(og);
        ok.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty() || mCurrentlyEditedButton == null) return;
            ControlData props = mCurrentlyEditedButton.getProperties();
            JSONObject p = new JSONObject();
            try {
                p.put("name", name);
                p.put("bg", props.bgColor);
                p.put("stroke", props.strokeColor);
                p.put("sw", props.strokeWidth);
                p.put("radius", props.cornerRadius);
                p.put("opacity", props.opacity);
                p.put("w", (int) props.getWidth());
                p.put("h", (int) props.getHeight());
            } catch (Exception ignored) {}
            saveCustomPreset(p);
            removeView(box);
        });
        box.addView(ok);
        addView(box);
    }

    private void applyPreset(int bg, int stroke, float sw, float radius, float opacity, float sizeScale, int w, int h) {
        if (mCurrentlyEditedButton == null) return;
        ControlData props = mCurrentlyEditedButton.getProperties();
        internalChanges = true;
        props.bgColor = bg;
        props.strokeColor = stroke;
        props.strokeWidth = sw;
        props.cornerRadius = radius;
        props.opacity = opacity;
        if (w > 0 && h > 0) {
            props.setWidth(w);
            props.setHeight(h);
        } else if (sizeScale != 1f) {
            props.setWidth(props.getWidth() * sizeScale);
            props.setHeight(props.getHeight() * sizeScale);
        }
        internalChanges = false;
        mCurrentlyEditedButton.setProperties(props, false);
        mCurrentlyEditedButton.setBackground();
        mCurrentlyEditedButton.getControlView().setAlpha(props.opacity);
        reloadAllFields();
    }

    // 底部固定按钮栏（始终可见，不需滚动）
    private void buildButtonBar() {
        LinearLayout btnRow = new LinearLayout(getContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(dp(8), dp(8), dp(8), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21E1E24);
        bg.setCornerRadius(dp(12));
        btnRow.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(8), 0, dp(8), dp(8));
        btnRow.setLayoutParams(lp);

        TextView save = makeBtn("保存", 0xFF3A6EA5);
        save.setOnClickListener(v -> save());
        TextView del = makeBtn("删除", 0xFFC0392B);
        del.setOnClickListener(v -> {
            disappear(false);
            if (mCurrentlyEditedButton != null) mCurrentlyEditedButton.removeButton();
        });
        TextView done = makeBtn("完成", 0xFF27AE60);
        done.setOnClickListener(v -> disappear(true));
        btnRow.addView(save);
        btnRow.addView(del);
        btnRow.addView(done);

        mBtnBar = btnRow;
        mPanel.addView(btnRow);
    }

    // ---------------- 虚拟键盘 ----------------
    private static final int[][] KB_ROWS = {
            {LwjglGlfwKeycode.GLFW_KEY_ESCAPE, LwjglGlfwKeycode.GLFW_KEY_F1, LwjglGlfwKeycode.GLFW_KEY_F2,
             LwjglGlfwKeycode.GLFW_KEY_F3, LwjglGlfwKeycode.GLFW_KEY_F4, LwjglGlfwKeycode.GLFW_KEY_F5,
             LwjglGlfwKeycode.GLFW_KEY_F6, LwjglGlfwKeycode.GLFW_KEY_F7, LwjglGlfwKeycode.GLFW_KEY_F8,
             LwjglGlfwKeycode.GLFW_KEY_F9, LwjglGlfwKeycode.GLFW_KEY_F10, LwjglGlfwKeycode.GLFW_KEY_F11,
             LwjglGlfwKeycode.GLFW_KEY_F12},
            {LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT, LwjglGlfwKeycode.GLFW_KEY_1, LwjglGlfwKeycode.GLFW_KEY_2,
             LwjglGlfwKeycode.GLFW_KEY_3, LwjglGlfwKeycode.GLFW_KEY_4, LwjglGlfwKeycode.GLFW_KEY_5,
             LwjglGlfwKeycode.GLFW_KEY_6, LwjglGlfwKeycode.GLFW_KEY_7, LwjglGlfwKeycode.GLFW_KEY_8,
             LwjglGlfwKeycode.GLFW_KEY_9, LwjglGlfwKeycode.GLFW_KEY_0, LwjglGlfwKeycode.GLFW_KEY_MINUS,
             LwjglGlfwKeycode.GLFW_KEY_EQUAL, LwjglGlfwKeycode.GLFW_KEY_BACKSPACE},
            {LwjglGlfwKeycode.GLFW_KEY_TAB, LwjglGlfwKeycode.GLFW_KEY_Q, LwjglGlfwKeycode.GLFW_KEY_W,
             LwjglGlfwKeycode.GLFW_KEY_E, LwjglGlfwKeycode.GLFW_KEY_R, LwjglGlfwKeycode.GLFW_KEY_T,
             LwjglGlfwKeycode.GLFW_KEY_Y, LwjglGlfwKeycode.GLFW_KEY_U, LwjglGlfwKeycode.GLFW_KEY_I,
             LwjglGlfwKeycode.GLFW_KEY_O, LwjglGlfwKeycode.GLFW_KEY_P, LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET,
             LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET, LwjglGlfwKeycode.GLFW_KEY_BACKSLASH},
            {LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK, LwjglGlfwKeycode.GLFW_KEY_A, LwjglGlfwKeycode.GLFW_KEY_S,
             LwjglGlfwKeycode.GLFW_KEY_D, LwjglGlfwKeycode.GLFW_KEY_F, LwjglGlfwKeycode.GLFW_KEY_G,
             LwjglGlfwKeycode.GLFW_KEY_H, LwjglGlfwKeycode.GLFW_KEY_J, LwjglGlfwKeycode.GLFW_KEY_K,
             LwjglGlfwKeycode.GLFW_KEY_L, LwjglGlfwKeycode.GLFW_KEY_SEMICOLON, LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE,
             LwjglGlfwKeycode.GLFW_KEY_ENTER},
            {LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, LwjglGlfwKeycode.GLFW_KEY_Z, LwjglGlfwKeycode.GLFW_KEY_X,
             LwjglGlfwKeycode.GLFW_KEY_C, LwjglGlfwKeycode.GLFW_KEY_V, LwjglGlfwKeycode.GLFW_KEY_B,
             LwjglGlfwKeycode.GLFW_KEY_N, LwjglGlfwKeycode.GLFW_KEY_M, LwjglGlfwKeycode.GLFW_KEY_COMMA,
             LwjglGlfwKeycode.GLFW_KEY_PERIOD, LwjglGlfwKeycode.GLFW_KEY_SLASH, LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT},
            {LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT, LwjglGlfwKeycode.GLFW_KEY_SPACE,
             LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT, LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL}
    };

    private void buildKeyboard() {
        mKeyboardScrim = new View(getContext());
        mKeyboardScrim.setBackgroundColor(0x99000000);
        mKeyboardScrim.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mKeyboardScrim.setOnClickListener(v -> hideKeyboard());
        addView(mKeyboardScrim);

        mKeyboardPanel = new LinearLayout(getContext());
        mKeyboardPanel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21E1E24);
        bg.setCornerRadii(new float[]{dp(16), dp(16), 0, 0, 0, 0, dp(16), dp(16)});
        mKeyboardPanel.setBackground(bg);
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(getResources().getDisplayMetrics().heightPixels * 0.68f));
        kp.gravity = Gravity.BOTTOM;
        mKeyboardPanel.setLayoutParams(kp);
        addView(mKeyboardPanel);

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(10), dp(6));
        mKeyboardTitle = new TextView(getContext());
        mKeyboardTitle.setTextColor(Color.WHITE);
        mKeyboardTitle.setTextSize(15f);
        mKeyboardTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(mKeyboardTitle);
        TextView close = new TextView(getContext());
        close.setText("✕");
        close.setTextColor(0xFFCCCCCC);
        close.setTextSize(18f);
        close.setPadding(dp(12), dp(2), dp(12), dp(2));
        close.setOnClickListener(v -> hideKeyboard());
        header.addView(close);
        mKeyboardPanel.addView(header);

        // Tab 切换：键盘 / 特殊按钮
        LinearLayout tabs = new LinearLayout(getContext());
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(8), 0, dp(8), dp(6));
        TextView tabKeys = new TextView(getContext());
        tabKeys.setText("键盘");
        tabKeys.setTextColor(Color.WHITE);
        tabKeys.setTextSize(13f);
        tabKeys.setGravity(Gravity.CENTER);
        tabKeys.setPadding(dp(20), dp(6), dp(20), dp(6));
        tabKeys.setBackground(round(0xFF3A6EA5, dp(16)));
        tabKeys.setOnClickListener(v -> renderKeyboardKeys(true));
        TextView tabSpec = new TextView(getContext());
        tabSpec.setText("特殊按钮");
        tabSpec.setTextColor(Color.WHITE);
        tabSpec.setTextSize(13f);
        tabSpec.setGravity(Gravity.CENTER);
        tabSpec.setPadding(dp(20), dp(6), dp(20), dp(6));
        tabSpec.setBackground(round(0xFF444444, dp(16)));
        tabSpec.setOnClickListener(v -> renderKeyboardKeys(false));
        tabs.addView(tabKeys);
        tabs.addView(tabSpec);
        mKeyboardPanel.addView(tabs);

        ScrollView ksv = new ScrollView(getContext());
        ksv.setFillViewport(true);
        mKeyGrid = new LinearLayout(getContext());
        mKeyGrid.setOrientation(LinearLayout.VERTICAL);
        mKeyGrid.setPadding(dp(8), dp(4), dp(8), dp(8));
        ksv.addView(mKeyGrid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mKeyboardPanel.addView(ksv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mKeyboardScrim.setVisibility(GONE);
        mKeyboardPanel.setVisibility(GONE);
    }

    private void showKeyboard(int slot) {
        mEditingSlot = slot;
        mKeyboardTitle.setText("键位映射 - 键" + (slot + 1));
        renderKeyboardKeys(true);
        mKeyboardScrim.setVisibility(VISIBLE);
        mKeyboardPanel.setVisibility(VISIBLE);
    }

    private void hideKeyboard() {
        mKeyboardScrim.setVisibility(GONE);
        mKeyboardPanel.setVisibility(GONE);
    }

    private void renderKeyboardKeys(boolean physical) {
        mKeyGrid.removeAllViews();
        int[] slots = mCurrentlyEditedButton == null ? mActiveSlots : mCurrentlyEditedButton.getProperties().keycodes;
        if (physical) {
            for (int[] row : KB_ROWS) {
                LinearLayout r = new LinearLayout(getContext());
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER);
                r.setPadding(0, dp(2), 0, dp(2));
                for (int key : row) {
                    r.addView(keyCell(key, slots));
                }
                mKeyGrid.addView(r);
            }
        } else {
            // 特殊按钮
            LinearLayout r = new LinearLayout(getContext());
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER);
            r.setPadding(0, dp(2), 0, dp(2));
            for (int i = 0; i < mSpecialArray.size(); i++) {
                final int spVal = i - mSpecialArray.size(); // 负值 = 特殊按钮键码
                r.addView(specialCell(mSpecialArray.get(i), spVal, slots));
                if ((i + 1) % 4 == 0) {
                    mKeyGrid.addView(r);
                    r = new LinearLayout(getContext());
                    r.setOrientation(LinearLayout.HORIZONTAL);
                    r.setGravity(Gravity.CENTER);
                    r.setPadding(0, dp(2), 0, dp(2));
                }
            }
            if (r.getChildCount() > 0) mKeyGrid.addView(r);
        }
    }

    private TextView keyCell(int key, int[] slots) {
        final int k = key;
        boolean bound = containsKey(slots, k);
        TextView cell = new TextView(getContext());
        cell.setText(keyLabel(k));
        cell.setTextColor(Color.WHITE);
        cell.setTextSize(10f);
        cell.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bound ? 0xFF27AE60 : 0xFF444444);
        g.setCornerRadius(dp(5));
        cell.setBackground(g);
        int w = k >= LwjglGlfwKeycode.GLFW_KEY_F1 && k <= LwjglGlfwKeycode.GLFW_KEY_F12 ? dp(28) : dp(26);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, dp(28));
        lp.setMargins(dp(1), dp(1), dp(1), dp(1));
        cell.setLayoutParams(lp);
        cell.setOnClickListener(v -> {
            if (mCurrentlyEditedButton == null) return;
            int[] codes = mCurrentlyEditedButton.getProperties().keycodes;
            int idx = indexOfKey(codes, k);
            if (idx >= 0) {
                codes[idx] = 0; // 点绿色 = 取消绑定
            } else {
                codes[mEditingSlot] = k; // 绑定到当前槽位
            }
            refreshKeyRow(mEditingSlot);
            renderKeyboardKeys(true);
        });
        return cell;
    }

    private TextView specialCell(String name, int spVal, int[] slots) {
        final int sp = spVal;
        boolean bound = containsKey(slots, sp);
        TextView cell = new TextView(getContext());
        cell.setText(name);
        cell.setTextColor(Color.WHITE);
        cell.setTextSize(9f);
        cell.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bound ? 0xFF27AE60 : 0xFF444444);
        g.setCornerRadius(dp(5));
        cell.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), 1f);
        lp.setMargins(dp(1), dp(1), dp(1), dp(1));
        cell.setLayoutParams(lp);
        cell.setOnClickListener(v -> {
            if (mCurrentlyEditedButton == null) return;
            int[] codes = mCurrentlyEditedButton.getProperties().keycodes;
            int idx = indexOfKey(codes, sp);
            if (idx >= 0) {
                codes[idx] = 0;
            } else {
                codes[mEditingSlot] = sp;
            }
            refreshKeyRow(mEditingSlot);
            renderKeyboardKeys(false);
        });
        return cell;
    }

    private static boolean containsKey(int[] codes, int key) {
        if (codes == null) return false;
        for (int c : codes) if (c == key) return true;
        return false;
    }

    private static int indexOfKey(int[] codes, int key) {
        if (codes == null) return -1;
        for (int i = 0; i < codes.length; i++) if (codes[i] == key) return i;
        return -1;
    }

    private String keyLabel(int key) {
        if (key >= 'A' && key <= 'Z') return String.valueOf((char) key);
        if (key >= '0' && key <= '9') return String.valueOf((char) key);
        switch (key) {
            case LwjglGlfwKeycode.GLFW_KEY_ESCAPE: return "ESC";
            case LwjglGlfwKeycode.GLFW_KEY_ENTER: return "Enter";
            case LwjglGlfwKeycode.GLFW_KEY_SPACE: return "空格";
            case LwjglGlfwKeycode.GLFW_KEY_BACKSPACE: return "退格";
            case LwjglGlfwKeycode.GLFW_KEY_TAB: return "Tab";
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT:
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_SHIFT: return "Shift";
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL:
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_CONTROL: return "Ctrl";
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_ALT:
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_ALT: return "Alt";
            case LwjglGlfwKeycode.GLFW_KEY_CAPS_LOCK: return "Caps";
            case LwjglGlfwKeycode.GLFW_KEY_GRAVE_ACCENT: return "`";
            case LwjglGlfwKeycode.GLFW_KEY_MINUS: return "-";
            case LwjglGlfwKeycode.GLFW_KEY_EQUAL: return "=";
            case LwjglGlfwKeycode.GLFW_KEY_LEFT_BRACKET: return "[";
            case LwjglGlfwKeycode.GLFW_KEY_RIGHT_BRACKET: return "]";
            case LwjglGlfwKeycode.GLFW_KEY_BACKSLASH: return "\\";
            case LwjglGlfwKeycode.GLFW_KEY_SEMICOLON: return ";";
            case LwjglGlfwKeycode.GLFW_KEY_APOSTROPHE: return "'";
            case LwjglGlfwKeycode.GLFW_KEY_COMMA: return ",";
            case LwjglGlfwKeycode.GLFW_KEY_PERIOD: return ".";
            case LwjglGlfwKeycode.GLFW_KEY_SLASH: return "/";
            default:
                if (key >= LwjglGlfwKeycode.GLFW_KEY_F1 && key <= LwjglGlfwKeycode.GLFW_KEY_F12)
                    return "F" + (key - LwjglGlfwKeycode.GLFW_KEY_F1 + 1);
                return "键" + key;
        }
    }

    private void refreshKeyRow(int slot) {
        if (mCurrentlyEditedButton == null) return;
        int[] codes = mCurrentlyEditedButton.getProperties().keycodes;
        int v = slot < codes.length ? codes[slot] : 0;
        mKeycodeTextviews[slot].setText(keyNameOf(v));
        mActiveSlots[slot] = v;
    }

    private void refreshAllKeyRows() {
        for (int i = 0; i < 4; i++) refreshKeyRow(i);
    }

    private String keyNameOf(int v) {
        if (v == 0) return "未绑定";
        if (v < 0) {
            int idx = v + mSpecialArray.size();
            return idx >= 0 && idx < mSpecialArray.size() ? mSpecialArray.get(idx) : "特殊";
        }
        return keyLabel(v);
    }

    // ---------------- 工具 ----------------
    private SeekBar mLastSeekbar;
    private TextView mLastPercent, mLastLabel;

    private void addSeekbarRow(String label, int max) {
        TextView lbl = new TextView(getContext());
        lbl.setText(label);
        lbl.setTextColor(0xFFCCCCCC);
        lbl.setTextSize(13f);
        mContent.addView(makeFieldLabel(lbl));
        mLastLabel = lbl;

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar sb = new SeekBar(getContext());
        sb.setMax(max);
        sb.setLayoutParams(new LinearLayout.LayoutParams(0, dp(34), 1f));
        TextView pct = new TextView(getContext());
        pct.setTextColor(Color.WHITE);
        pct.setTextSize(13f);
        pct.setGravity(Gravity.CENTER);
        pct.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(34)));
        row.addView(sb);
        row.addView(pct);
        makeFieldWrap(row);
        mLastSeekbar = sb;
        mLastPercent = pct;
    }

    private TextView makeFieldLabel(TextView label) {
        label.setTextColor(0xFFCCCCCC);
        label.setTextSize(13f);
        label.setPadding(dp(16), dp(6), dp(16), dp(2));
        return label;
    }

    private LinearLayout makeFieldWrap(View child) {
        LinearLayout wrap = new LinearLayout(getContext());
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setPadding(dp(16), dp(2), dp(16), dp(2));
        wrap.addView(child, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        mContent.addView(wrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private EditText makeEditText() {
        EditText et = new EditText(getContext());
        et.setTextColor(Color.WHITE);
        et.setTextSize(14f);
        et.setSingleLine(true);
        et.setBackgroundColor(0xFF333333);
        et.setPadding(dp(8), 0, dp(8), 0);
        return et;
    }

    private Switch makeSwitch(String text) {
        Switch sw = new Switch(getContext());
        sw.setText(text);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(14f);
        sw.setPadding(dp(16), dp(2), dp(16), dp(2));
        return sw;
    }

    private TextView makeBtn(String text, int color) {
        TextView b = new TextView(getContext());
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(8));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView makeColorButton(String text) {
        TextView b = new TextView(getContext());
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF3A6EA5);
        g.setCornerRadius(dp(8));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private void loadOrientationAdapter() {
        ArrayAdapter<ControlDrawerData.Orientation> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item);
        adapter.addAll(ControlDrawerData.getOrientations());
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        mOrientationSpinner.setAdapter(adapter);
    }

    private void setDefaultVisibilitySetting() {
        mOrientationTextView.setVisibility(VISIBLE);
        mOrientationSpinner.setVisibility(VISIBLE);
        mMappingTextView.setVisibility(VISIBLE);
        mNameTextView.setVisibility(VISIBLE);
        mNameEditText.setVisibility(VISIBLE);
        mSizeTextview.setVisibility(VISIBLE);
        mSizeXTextView.setVisibility(VISIBLE);
        mWidthEditText.setVisibility(VISIBLE);
        mHeightEditText.setVisibility(VISIBLE);
        mCornerRadiusTextView.setVisibility(VISIBLE);
        mCornerRadiusSeekbar.setVisibility(VISIBLE);
        mCornerRadiusPercentTextView.setVisibility(VISIBLE);
        mVisibilityTextView.setVisibility(VISIBLE);
        mDisplayInGameCheckbox.setVisibility(VISIBLE);
        mDisplayInMenuCheckbox.setVisibility(VISIBLE);
        mToggleSwitch.setVisibility(VISIBLE);
        mPassthroughSwitch.setVisibility(VISIBLE);
        mSwipeableSwitch.setVisibility(VISIBLE);
        mForwardLockSwitch.setVisibility(GONE);
        mAbsoluteTrackingSwitch.setVisibility(GONE);
        mOpenKeyboardSwitch.setVisibility(VISIBLE);
        mPresetChips.setVisibility(VISIBLE);
        for (int i = 0; i < 4; i++) mKeyRows[i].setVisibility(VISIBLE);
    }

    // ---------------- LOADING VALUES ----------------
    public void loadValues(ControlData data) {
        setDefaultVisibilitySetting();
        mOrientationTextView.setVisibility(GONE);
        mOrientationSpinner.setVisibility(GONE);
        mForwardLockSwitch.setVisibility(GONE);
        mAbsoluteTrackingSwitch.setVisibility(GONE);

        mNameEditText.setText(data.name);
        mWidthEditText.setText(String.valueOf((int) data.getWidth()));
        mHeightEditText.setText(String.valueOf((int) data.getHeight()));

        mAlphaSeekbar.setProgress((int) (data.opacity * 100));
        mStrokeWidthSeekbar.setProgress((int) data.strokeWidth * 10);
        mCornerRadiusSeekbar.setProgress((int) data.cornerRadius);

        setPercentageText(mAlphaPercentTextView, (int) (data.opacity * 100));
        setPercentageText(mStrokePercentTextView, (int) data.strokeWidth * 10);
        setPercentageText(mCornerRadiusPercentTextView, (int) data.cornerRadius);

        mToggleSwitch.setChecked(data.isToggle);
        mPassthroughSwitch.setChecked(data.passThruEnabled);
        mSwipeableSwitch.setChecked(data.isSwipeable);
        mOpenKeyboardSwitch.setChecked(data.openKeyboardOnClick);

        mDisplayInGameCheckbox.setChecked(data.displayInGame);
        mDisplayInMenuCheckbox.setChecked(data.displayInMenu);

        for (int i = 0; i < 4; i++) mActiveSlots[i] = 0;
        for (int i = 0; i < data.keycodes.length && i < 4; i++) mActiveSlots[i] = data.keycodes[i];
        refreshAllKeyRows();
    }

    public void loadValues(ControlDrawerData data) {
        loadValues(data.properties);
        mOrientationSpinner.setSelection(ControlDrawerData.orientationToInt(data.orientation));
        mMappingTextView.setVisibility(GONE);
        for (int i = 0; i < 4; i++) mKeyRows[i].setVisibility(GONE);
        mOrientationTextView.setVisibility(VISIBLE);
        mOrientationSpinner.setVisibility(VISIBLE);
        mSwipeableSwitch.setVisibility(GONE);
        mPassthroughSwitch.setVisibility(GONE);
        mToggleSwitch.setVisibility(GONE);
        mOpenKeyboardSwitch.setVisibility(GONE);
    }

    public void loadJoystickValues(ControlJoystickData data) {
        loadValues(data);
        mMappingTextView.setVisibility(GONE);
        for (int i = 0; i < 4; i++) mKeyRows[i].setVisibility(GONE);
        mNameTextView.setVisibility(GONE);
        mNameEditText.setVisibility(GONE);
        mCornerRadiusTextView.setVisibility(GONE);
        mCornerRadiusSeekbar.setVisibility(GONE);
        mCornerRadiusPercentTextView.setVisibility(GONE);
        mSwipeableSwitch.setVisibility(GONE);
        mPassthroughSwitch.setVisibility(GONE);
        mToggleSwitch.setVisibility(GONE);
        mForwardLockSwitch.setVisibility(VISIBLE);
        mForwardLockSwitch.setChecked(data.forwardLock);
        mAbsoluteTrackingSwitch.setVisibility(VISIBLE);
        mAbsoluteTrackingSwitch.setChecked(data.absolute);
    }

    public void loadSubButtonValues(ControlData data, ControlDrawerData.Orientation drawerOrientation) {
        loadValues(data);
        if (drawerOrientation != ControlDrawerData.Orientation.FREE) {
            mSizeTextview.setVisibility(GONE);
            mSizeXTextView.setVisibility(GONE);
            mWidthEditText.setVisibility(GONE);
            mHeightEditText.setVisibility(GONE);
        }
        mVisibilityTextView.setVisibility(GONE);
        mDisplayInMenuCheckbox.setVisibility(GONE);
        mDisplayInGameCheckbox.setVisibility(GONE);
    }

    public static void setPercentageText(TextView textView, int progress) {
        textView.setText(progress + "%");
    }

    private void reloadAllFields() {
        if (mCurrentlyEditedButton == null) return;
        internalChanges = true;
        loadValues(mCurrentlyEditedButton.getProperties());
        internalChanges = false;
    }

    // ---------------- 实时监听 ----------------
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private void setupRealTimeListeners() {
        mNameEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().name = s.toString();
            mCurrentlyEditedButton.setProperties(mCurrentlyEditedButton.getProperties(), false);
        });

        mWidthEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges) return;
            float width = safeParseFloat(s.toString());
            if (width >= 0) {
                mCurrentlyEditedButton.getProperties().setWidth(width);
                if (mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData) {
                    mCurrentlyEditedButton.getProperties().setHeight(width);
                }
                mCurrentlyEditedButton.updateProperties();
            }
        });

        mHeightEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges) return;
            float height = safeParseFloat(s.toString());
            if (height >= 0) {
                mCurrentlyEditedButton.getProperties().setHeight(height);
                if (mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData) {
                    mCurrentlyEditedButton.getProperties().setWidth(height);
                }
                mCurrentlyEditedButton.updateProperties();
            }
        });

        mSwipeableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().isSwipeable = isChecked;
        });
        mToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().isToggle = isChecked;
        });
        mPassthroughSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().passThruEnabled = isChecked;
        });
        mOpenKeyboardSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().openKeyboardOnClick = isChecked;
        });
        mForwardLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            if (mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData) {
                ((ControlJoystickData) mCurrentlyEditedButton.getProperties()).forwardLock = isChecked;
            }
        });
        mAbsoluteTrackingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            if (mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData) {
                ((ControlJoystickData) mCurrentlyEditedButton.getProperties()).absolute = isChecked;
            }
        });

        mAlphaSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().opacity = mAlphaSeekbar.getProgress() / 100f;
            mCurrentlyEditedButton.getControlView().setAlpha(mAlphaSeekbar.getProgress() / 100f);
            setPercentageText(mAlphaPercentTextView, progress);
        });

        mStrokeWidthSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().strokeWidth = mStrokeWidthSeekbar.getProgress() / 10F;
            mCurrentlyEditedButton.setBackground();
            setPercentageText(mStrokePercentTextView, progress);
        });

        mCornerRadiusSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().cornerRadius = mCornerRadiusSeekbar.getProgress();
            mCurrentlyEditedButton.setBackground();
            setPercentageText(mCornerRadiusPercentTextView, progress);
        });

        mOrientationSpinner.setOnItemSelectedListener((SimpleItemSelectedListener) (parent, view, position, id) -> {
            if (mCurrentlyEditedButton instanceof ControlDrawer) {
                ((ControlDrawer) mCurrentlyEditedButton).drawerData.orientation =
                        ControlDrawerData.intToOrientation(mOrientationSpinner.getSelectedItemPosition());
                ((ControlDrawer) mCurrentlyEditedButton).syncButtons();
            }
        });

        mDisplayInGameCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().displayInGame = isChecked;
        });
        mDisplayInMenuCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().displayInMenu = isChecked;
        });

        mSelectStrokeColor.setOnClickListener(v -> {
            mEditingBackground = false;
            mColorStrip.setVisibility(VISIBLE);
        });
        mSelectBackgroundColor.setOnClickListener(v -> {
            mEditingBackground = true;
            mColorStrip.setVisibility(VISIBLE);
        });
    }

    // ---------------- 颜色选择条 ----------------
    private void buildColorStrip() {
        mColorStrip = new LinearLayout(getContext());
        mColorStrip.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0xF21E1E24);
        cbg.setCornerRadius(dp(10));
        mColorStrip.setBackground(cbg);
        mColorStrip.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView t = new TextView(getContext());
        t.setText("选择颜色");
        t.setTextColor(Color.WHITE);
        t.setTextSize(14f);
        t.setGravity(Gravity.CENTER);
        mColorStrip.addView(t);

        int[] colors = {0xFFFFFFFF, 0xFF000000, 0xFF9E9E9E, 0xFF795548, 0xFFFF5722, 0xFFFF9800,
                0xFFFFC107, 0xFFFFEB3B, 0xFF8BC34A, 0xFF4CAF50, 0xFF009688, 0xFF00BCD4,
                0xFF2196F3, 0xFF3F51B5, 0xFF673AB7, 0xFF9C27B0, 0xFFE91E63, 0xFFF44336};
        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setGravity(Gravity.CENTER);
        grid.setPadding(dp(2), dp(4), dp(2), dp(4));
        for (int c : colors) {
            TextView sw = new TextView(getContext());
            int size = dp(30);
            GradientDrawable g = new GradientDrawable();
            g.setColor(c);
            g.setCornerRadius(dp(4));
            g.setStroke(dp(1), 0xFF666666);
            sw.setBackground(g);
            sw.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            sw.setOnClickListener(v -> applyColor(c));
            grid.addView(sw);
        }
        mColorStrip.addView(grid);

        TextView at = new TextView(getContext());
        at.setText("透明度");
        at.setTextColor(0xFFCCCCCC);
        at.setTextSize(12f);
        mColorStrip.addView(at);
        SeekBar alphaSb = new SeekBar(getContext());
        alphaSb.setMax(255);
        alphaSb.setProgress(255);
        alphaSb.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            int current = mEditingBackground
                    ? mCurrentlyEditedButton.getProperties().bgColor
                    : mCurrentlyEditedButton.getProperties().strokeColor;
            int col = (current & 0x00FFFFFF) | (progress << 24);
            applyColor(col);
        });
        mColorStrip.addView(alphaSb);

        TextView closeBtn = makeBtn("关闭", 0xFF3A6EA5);
        closeBtn.setOnClickListener(v -> mColorStrip.setVisibility(GONE));
        mColorStrip.addView(closeBtn);

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        cp.setMargins(dp(60), 0, dp(60), 0);
        mColorStrip.setLayoutParams(cp);
        mColorStrip.setVisibility(GONE);
        addView(mColorStrip);
    }

    private void applyColor(int color) {
        if (mCurrentlyEditedButton == null) return;
        if (mEditingBackground) {
            mCurrentlyEditedButton.getProperties().bgColor = color;
        } else {
            mCurrentlyEditedButton.getProperties().strokeColor = color;
        }
        mCurrentlyEditedButton.setBackground();
    }

    // ---------------- 生命周期 ----------------
    public void setCurrentlyEditedButton(ControlInterface button) {
        mCurrentlyEditedButton = button;
    }

    public void appear(boolean left) {
        setVisibility(VISIBLE);
        mScrim.setVisibility(VISIBLE);
        mPanel.setVisibility(VISIBLE);
        mPanel.animate().translationX(0).setDuration(200).start();
    }

    public void disappear(boolean save) {
        if (save) save();
        mPanel.animate().translationX(-dp(500)).setDuration(200)
                .withEndAction(() -> {
                    mPanel.setVisibility(GONE);
                    mScrim.setVisibility(GONE);
                    mColorStrip.setVisibility(GONE);
                    hideKeyboard();
                }).start();
    }

    private void save() {
        // 数据实时写入 properties，无需额外保存
    }

    public void disappearColor() {
        mColorStrip.setVisibility(GONE);
    }

    public boolean disappearLayer() {
        if (mKeyboardPanel.getVisibility() == VISIBLE) {
            hideKeyboard();
            return false;
        }
        if (mColorStrip.getVisibility() == VISIBLE) {
            disappearColor();
            return false;
        }
        disappear(false);
        return true;
    }

    public void adaptPanelPosition() {
        // 左侧固定面板，无需自适应
    }

    private float safeParseFloat(String string) {
        float out = -1;
        try {
            out = Float.parseFloat(string);
        } catch (NumberFormatException e) {
            // ignored
        }
        return out;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
