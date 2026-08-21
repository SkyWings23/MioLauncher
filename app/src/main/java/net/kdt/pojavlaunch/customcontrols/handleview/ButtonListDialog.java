package net.kdt.pojavlaunch.customcontrols.handleview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlJoystick;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlSubButton;

import java.util.List;

/**
 * 按钮列表 + 按钮组管理面板：列出当前所有控件（可编辑）、改名/新建/切换按钮组。
 */
public class ButtonListDialog extends FrameLayout {

    private final ControlLayout mLayout;
    private final LinearLayout mPanel;
    private LinearLayout mGroupChips;
    private LinearLayout mButtonList;
    private LinearLayout mControlsContainer;
    private LinearLayout mNewGroupRow;
    private EditText mNewGroupInput;
    private EditText mGroupNameInput;

    public ButtonListDialog(Context context, ControlLayout layout) {
        super(context);
        mLayout = layout;
        setClipChildren(false);
        setTranslationZ(99998f);

        // 遮罩
        View scrim = new View(context);
        scrim.setBackgroundColor(0x99000000);
        scrim.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setOnClickListener(v -> setVisibility(GONE));
        addView(scrim);

        // 右侧面板
        mPanel = new LinearLayout(context);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21E1E24);
        bg.setCornerRadii(new float[]{dp(14), dp(14), 0, 0, 0, 0, dp(14), dp(14)});
        mPanel.setBackground(bg);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.82f),
                ViewGroup.LayoutParams.MATCH_PARENT);
        pp.gravity = Gravity.END;
        mPanel.setLayoutParams(pp);
        addView(mPanel);

        buildContent();
    }

    private void buildContent() {
        // 标题（固定）
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(10), dp(10));
        TextView title = new TextView(getContext());
        title.setText("按钮列表");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16f);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        TextView close = new TextView(getContext());
        close.setText("✕");
        close.setTextColor(0xFFCCCCCC);
        close.setTextSize(18f);
        close.setPadding(dp(12), dp(4), dp(12), dp(4));
        close.setOnClickListener(v -> setVisibility(GONE));
        header.addView(close);
        mPanel.addView(header);

        // 统一滚动内容：按钮组 + 控件列表 全部一起滚动
        ScrollView sv = new ScrollView(getContext());
        sv.setFillViewport(true);
        mButtonList = new LinearLayout(getContext());
        mButtonList.setOrientation(LinearLayout.VERTICAL);
        mButtonList.setPadding(0, 0, 0, dp(8));
        sv.addView(mButtonList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mPanel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(getContext());
        hint.setText("提示：编辑模式点一下任意控件，即可打开详细设置");
        hint.setTextColor(0xFF88CCAA);
        hint.setTextSize(11f);
        hint.setPadding(dp(16), dp(4), dp(16), dp(6));
        mButtonList.addView(hint);

        // ===== 按钮组（随列表滚动）=====
        mButtonList.addView(sectionLabel("按钮组"));
        LinearLayout nameRow = new LinearLayout(getContext());
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameRow.setPadding(dp(12), dp(4), dp(12), dp(4));
        TextView nl = new TextView(getContext());
        nl.setText("名称");
        nl.setTextColor(0xFFCCCCCC);
        nl.setTextSize(13f);
        nl.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(36)));
        nameRow.addView(nl);
        mGroupNameInput = new EditText(getContext());
        mGroupNameInput.setTextColor(Color.WHITE);
        mGroupNameInput.setTextSize(14f);
        mGroupNameInput.setSingleLine(true);
        mGroupNameInput.setBackgroundColor(0xFF333333);
        mGroupNameInput.setPadding(dp(8), 0, dp(8), 0);
        mGroupNameInput.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
        mGroupNameInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                mLayout.setCurrentGroupName(s.toString());
                mLayout.autoSave();
                refreshGroupChips();
            }
        });
        nameRow.addView(mGroupNameInput);
        mButtonList.addView(nameRow);

        // 已有按钮组（横向 chips）
        HorizontalScrollView hsv = new HorizontalScrollView(getContext());
        hsv.setHorizontalScrollBarEnabled(false);
        mGroupChips = new LinearLayout(getContext());
        mGroupChips.setOrientation(LinearLayout.HORIZONTAL);
        mGroupChips.setPadding(dp(12), dp(4), dp(12), dp(4));
        hsv.addView(mGroupChips, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mButtonList.addView(hsv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 新建按钮组
        mNewGroupRow = new LinearLayout(getContext());
        mNewGroupRow.setOrientation(LinearLayout.HORIZONTAL);
        mNewGroupRow.setGravity(Gravity.CENTER_VERTICAL);
        mNewGroupRow.setPadding(dp(12), dp(4), dp(12), dp(4));
        mNewGroupRow.setVisibility(GONE);
        mNewGroupInput = new EditText(getContext());
        mNewGroupInput.setTextColor(Color.WHITE);
        mNewGroupInput.setTextSize(14f);
        mNewGroupInput.setSingleLine(true);
        mNewGroupInput.setHint("输入新按钮组名称");
        mNewGroupInput.setHintTextColor(0xFF888888);
        mNewGroupInput.setBackgroundColor(0xFF333333);
        mNewGroupInput.setPadding(dp(8), 0, dp(8), 0);
        mNewGroupInput.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
        mNewGroupRow.addView(mNewGroupInput);
        TextView ok = smallBtn("创建", 0xFF27AE60);
        ok.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
        ok.setOnClickListener(v -> {
            String name = mNewGroupInput.getText().toString().trim();
            if (!name.isEmpty()) {
                mLayout.createControlGroup(name);
                mNewGroupInput.setText("");
                mNewGroupRow.setVisibility(GONE);
                refresh();
            }
        });
        mNewGroupRow.addView(ok);
        mButtonList.addView(mNewGroupRow);

        TextView newGroupBtn = smallBtn("＋ 新建按钮组", 0xFF3A6EA5);
        newGroupBtn.setOnClickListener(v ->
                mNewGroupRow.setVisibility(mNewGroupRow.getVisibility() == VISIBLE ? GONE : VISIBLE));
        mButtonList.addView(newGroupBtn);

        // 导出 / 导入（FCL 兼容）
        LinearLayout exportRow = new LinearLayout(getContext());
        exportRow.setOrientation(LinearLayout.HORIZONTAL);
        exportRow.setPadding(dp(10), dp(4), dp(10), dp(4));
        TextView exportBtn = smallBtn("导出当前按钮组", 0xFF27AE60);
        exportBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
        exportBtn.setOnClickListener(v -> mLayout.exportLayoutForSharing());
        exportRow.addView(exportBtn);
        TextView importBtn = smallBtn("导入按钮组", 0xFF8E24AA);
        importBtn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
        importBtn.setOnClickListener(v -> mLayout.startLayoutImport());
        exportRow.addView(importBtn);
        mButtonList.addView(exportRow);

        // ===== 控件列表 =====
        mButtonList.addView(sectionLabel("当前屏幕上的控件（点击编辑）"));
        mControlsContainer = new LinearLayout(getContext());
        mControlsContainer.setOrientation(LinearLayout.VERTICAL);
        mButtonList.addView(mControlsContainer);

        TextView done = smallBtn("完成", 0xFF3A6EA5);
        done.setOnClickListener(v -> setVisibility(GONE));
        mPanel.addView(done);
    }

    private TextView sectionLabel(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(0xFF88CCAA);
        t.setTextSize(13f);
        t.setPadding(dp(16), dp(10), dp(16), dp(4));
        return t;
    }

    private TextView smallBtn(String text, int color) {
        TextView b = new TextView(getContext());
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13f);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(8));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), dp(4), dp(10), dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView chip(String text, boolean active) {
        TextView c = new TextView(getContext());
        c.setText(text);
        c.setTextColor(Color.WHITE);
        c.setTextSize(12f);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(active ? 0xFF3A6EA5 : 0xFF444444);
        g.setCornerRadius(dp(14));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        c.setLayoutParams(lp);
        return c;
    }

    public void refresh() {
        mGroupNameInput.setText(mLayout.getCurrentGroupName());
        refreshGroupChips();
        refreshButtonList();
    }

    private void refreshGroupChips() {
        mGroupChips.removeAllViews();
        List<String> groups = mLayout.listControlGroups();
        String current = mLayout.getCurrentGroupName();
        for (String g : groups) {
            boolean active = g.equals(current);
            TextView chip = chip(g, active);
            if (!active) {
                chip.setOnClickListener(v -> {
                    mLayout.loadControlGroup(g);
                    refresh();
                });
            }
            mGroupChips.addView(chip);
        }
    }

    private void refreshButtonList() {
        mControlsContainer.removeAllViews();
        // 直接遍历 ControlLayout 全部子控件（含抽屉子按钮/摇杆），不依赖可能过期的缓存
        java.util.List<ControlInterface> buttons = new java.util.ArrayList<>();
        android.view.ViewGroup parent = (android.view.ViewGroup) mLayout;
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View v = parent.getChildAt(i);
            if (v instanceof ControlInterface) buttons.add((ControlInterface) v);
        }
        if (buttons.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("暂无控件，点右上角 + 添加");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(13f);
            empty.setPadding(dp(16), dp(8), dp(16), dp(8));
            mControlsContainer.addView(empty);
            return;
        }
        for (ControlInterface ci : buttons) {
            try {
                mControlsContainer.addView(buildRow(ci));
            } catch (Throwable t) {
                android.util.Log.e("MioList", "buildRow failed for " + (ci.getProperties() != null ? ci.getProperties().name : "?"), t);
            }
        }
    }

    private View buildRow(ControlInterface ci) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(4), dp(12), dp(4));
        row.setBackgroundColor(0x11000000);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String type = "按钮";
        if (ci instanceof ControlSubButton) type = "子按钮";
        else if (ci instanceof ControlDrawer) type = "抽屉";
        else if (ci instanceof ControlJoystick) type = "摇杆";

        TextView typeBadge = new TextView(getContext());
        typeBadge.setText(type);
        typeBadge.setTextColor(Color.WHITE);
        typeBadge.setTextSize(10f);
        typeBadge.setGravity(Gravity.CENTER);
        typeBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(0xFF673AB7);
        tg.setCornerRadius(dp(4));
        typeBadge.setBackground(tg);
        row.addView(typeBadge);

        LinearLayout mid = new LinearLayout(getContext());
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(8), 0, 0, 0);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView name = new TextView(getContext());
        String n = ci.getProperties().name;
        name.setText(n == null || n.isEmpty() ? "(未命名)" : n);
        name.setTextColor(Color.WHITE);
        name.setTextSize(13f);
        mid.addView(name);
        // 游戏内/游戏外 徽标
        LinearLayout visRow = new LinearLayout(getContext());
        visRow.setOrientation(LinearLayout.HORIZONTAL);
        String game = ci.getProperties().displayInGame ? "游戏内" : null;
        String menu = ci.getProperties().displayInMenu ? "游戏外" : null;
        if (game != null) visRow.addView(visChip(game, 0xFF27AE60));
        if (menu != null) visRow.addView(visChip(menu, 0xFF3A6EA5));
        if (game == null && menu == null) {
            visRow.addView(visChip("隐藏", 0xFF777777));
        }
        mid.addView(visRow);
        row.addView(mid);

        TextView edit = new TextView(getContext());
        edit.setText("编辑");
        edit.setTextColor(Color.WHITE);
        edit.setTextSize(12f);
        edit.setGravity(Gravity.CENTER);
        edit.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable eg = new GradientDrawable();
        eg.setColor(0xFF3A6EA5);
        eg.setCornerRadius(dp(6));
        edit.setBackground(eg);
        edit.setOnClickListener(v -> {
            setVisibility(GONE);
            mLayout.editControlButton(ci);
        });
        row.addView(edit);

        return row;
    }

    private TextView visChip(String text, int color) {
        TextView c = new TextView(getContext());
        c.setText(text);
        c.setTextColor(Color.WHITE);
        c.setTextSize(9f);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(5), dp(1), dp(5), dp(1));
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(4));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(2), dp(4), 0);
        c.setLayoutParams(lp);
        return c;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
