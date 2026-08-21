package net.kdt.pojavlaunch.customcontrols.buttons;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.handleview.EditControlSideDialog;

import java.util.ArrayList;



@SuppressLint("ViewConstructor")
public class ControlDrawer extends ControlButton {


    public final ArrayList<ControlSubButton> buttons;
    public final ControlDrawerData drawerData;
    public final ControlLayout parentLayout;
    public boolean areButtonsVisible;


    public ControlDrawer(ControlLayout layout, ControlDrawerData drawerData) {
        super(layout, drawerData.properties);

        buttons = new ArrayList<>(drawerData.buttonProperties.size());
        this.parentLayout = layout;
        this.drawerData = drawerData;
        areButtonsVisible = layout.getModifiable();
    }


    public void addButton(ControlData properties){
        addButton(new ControlSubButton(parentLayout, properties, this));
    }

    public void addButton(ControlSubButton button){
        buttons.add(button);
        syncButtons();
        setControlButtonVisibility(button, areButtonsVisible);
    }

    private void setControlButtonVisibility(ControlButton button, boolean isVisible){
        button.getControlView().setVisibility(isVisible ? VISIBLE : GONE);
    }

    private void switchButtonVisibility(){
        areButtonsVisible = !areButtonsVisible;
        int visibility = areButtonsVisible ? VISIBLE : GONE;
        for(ControlButton button : buttons){
            button.getControlView().setVisibility(visibility);
        }
    }

    //Syncing stuff
    private void alignButtons(){
        if(buttons == null) return;
        if(drawerData.orientation == ControlDrawerData.Orientation.FREE) return;
        int margin = (int) ControlInterface.getMarginDistance();

        for(int i = 0; i < buttons.size(); ++i){
            switch (drawerData.orientation){
                case RIGHT:
                    buttons.get(i).setDynamicX(generateDynamicX(getX() + (drawerData.properties.getWidth() + margin)*(i+1) ));
                    buttons.get(i).setDynamicY(generateDynamicY(getY()));
                    break;

                case LEFT:
                    buttons.get(i).setDynamicX(generateDynamicX(getX() - (drawerData.properties.getWidth() + margin)*(i+1)));
                    buttons.get(i).setDynamicY(generateDynamicY(getY()));
                    break;

                case UP:
                    buttons.get(i).setDynamicY(generateDynamicY(getY() - (drawerData.properties.getHeight() + margin)*(i+1)));
                    buttons.get(i).setDynamicX(generateDynamicX(getX()));
                    break;

                case DOWN:
                    buttons.get(i).setDynamicY(generateDynamicY(getY() + (drawerData.properties.getHeight() + margin)*(i+1)));
                    buttons.get(i).setDynamicX(generateDynamicX(getX()));
                    break;
            }
            buttons.get(i).updateProperties();
        }
    }


    private void resizeButtons(){
        if (buttons == null || drawerData.orientation == ControlDrawerData.Orientation.FREE) return;
        for(ControlSubButton subButton : buttons){
            subButton.mProperties.setWidth(mProperties.getWidth());
            subButton.mProperties.setHeight(mProperties.getHeight());

            subButton.updateProperties();
        }
    }

    public void syncButtons(){
        alignButtons();
        resizeButtons();
    }

    /**
     * Check whether or not the button passed as a parameter belongs to this drawer.
     *
     * @param button The button to look for
     * @return Whether the button is in the buttons list of the drawer.
     */
    public boolean containsChild(ControlInterface button){
        for(ControlButton childButton : buttons){
            if (childButton == button) return true;
        }
        return false;
    }

    @Override
    public ControlData preProcessProperties(ControlData properties, ControlLayout layout) {
        ControlData data = super.preProcessProperties(properties, layout);
        data.isHideable = true;
        return data;
    }

    @Override
    public void setVisible(boolean isVisible) {
        int visibility = isVisible ? VISIBLE : GONE;
        setVisibility(visibility);
        if(visibility == GONE || areButtonsVisible) {
            for(ControlSubButton button : buttons){
                button.getControlView().setVisibility(isVisible ? VISIBLE : (!mProperties.isHideable && getVisibility() == GONE) ? VISIBLE : View.GONE);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(!getControlLayoutParent().getModifiable()){
            // MioLauncher: 游戏模式下抽屉可拖动（左上"更多"按钮）
            switch (event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    mDragStartX = event.getRawX();
                    mDragStartY = event.getRawY();
                    mDragging = false;
                    break;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - mDragStartX;
                    float dy = event.getRawY() - mDragStartY;
                    if (!mDragging && dx*dx + dy*dy > dp(6)*dp(6)) mDragging = true;
                    if (mDragging) {
                        ViewGroup.LayoutParams lp = getLayoutParams();
                        if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                            android.widget.FrameLayout.LayoutParams flp = (android.widget.FrameLayout.LayoutParams) lp;
                            flp.leftMargin = Math.max(0, Math.min(((android.view.ViewGroup)getParent()).getWidth() - getWidth(), getLeft() + (int)dx));
                            flp.topMargin = Math.max(0, Math.min(((android.view.ViewGroup)getParent()).getHeight() - getHeight(), getTop() + (int)dy));
                            flp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                            setLayoutParams(flp);
                            moveSubButtons((int)dx, (int)dy);
                            mDragStartX = event.getRawX();
                            mDragStartY = event.getRawY();
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: // 6
                    if (!mDragging) switchButtonVisibility();
                    mDragging = false;
                    break;
            }
            return true;
        }

        return super.onTouchEvent(event);
    }

    private void moveSubButtons(int dx, int dy) {
        for (ControlSubButton sub : buttons) {
            if (sub == null) continue;
            View view = sub.getControlView();
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp instanceof android.widget.FrameLayout.LayoutParams) {
                android.widget.FrameLayout.LayoutParams flp = (android.widget.FrameLayout.LayoutParams) lp;
                flp.leftMargin = Math.max(0, Math.min(((android.view.ViewGroup) getParent()).getWidth() - view.getWidth(), (flp.leftMargin > 0 ? flp.leftMargin : view.getLeft()) + dx));
                flp.topMargin = Math.max(0, Math.min(((android.view.ViewGroup) getParent()).getHeight() - view.getHeight(), (flp.topMargin > 0 ? flp.topMargin : view.getTop()) + dy));
                flp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                view.setLayoutParams(flp);
            }
        }
    }

    private static int dp(float v) {
        return Math.round(v * android.content.res.Resources.getSystem().getDisplayMetrics().density);
    }
    private float mDragStartX, mDragStartY;
    private boolean mDragging;


    @Override
    public void setX(float x) {
        super.setX(x);
        alignButtons();
    }

    @Override
    public void setY(float y) {
        super.setY(y);
        alignButtons();
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        super.setLayoutParams(params);
        syncButtons();
    }

    @Override
    public boolean canSnap(ControlInterface button) {
        boolean result = super.canSnap(button);
        return result && !containsChild(button);
    }

    //Getters
    public ControlDrawerData getDrawerData() {
        return drawerData;
    }

    @Override
    public void loadEditValues(EditControlSideDialog editControlPopup) {
        editControlPopup.loadValues(drawerData);
    }

    /**
     * 编辑模式下点击抽屉：弹出选择——管理主按钮 or 管理子按钮。
     * 子按钮编辑：直接打开对应子按钮的编辑面板。
     */
    @Override
    public boolean onLongClick(View v) {
        ControlLayout layout = getControlLayoutParent();
        if (layout == null || !layout.getModifiable()) return super.onLongClick(v);

        String[] options;
        if (buttons.isEmpty()) {
            options = new String[]{"管理抽屉主按钮"};
        } else {
            options = new String[]{"管理抽屉主按钮", "管理子按钮（" + buttons.size() + " 个）"};
        }
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("抽屉编辑")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // 主按钮
                        layout.editControlButton(this);
                        if (layout.mActionRow != null) layout.mActionRow.setFollowedButton(this);
                    } else {
                        // 子按钮选择列表
                        String[] names = new String[buttons.size()];
                        for (int i = 0; i < buttons.size(); i++) {
                            ControlSubButton sub = buttons.get(i);
                            String n = sub.getProperties() != null ? sub.getProperties().name : null;
                            names[i] = (n == null || n.isEmpty()) ? ("子按钮 " + (i + 1)) : n;
                        }
                        new android.app.AlertDialog.Builder(getContext())
                                .setTitle("选择要编辑的子按钮")
                                .setItems(names, (d2, idx) -> {
                                    ControlSubButton sub = buttons.get(idx);
                                    layout.editControlButton(sub);
                                    if (layout.mActionRow != null) layout.mActionRow.setFollowedButton(sub);
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        return true;
    }

    @Override
    public void cloneButton() {
        ControlDrawerData cloneData = new ControlDrawerData(getDrawerData());
        cloneData.properties.dynamicX = "0.5 * ${screen_width}";
        cloneData.properties.dynamicY = "0.5 * ${screen_height}";
        ((ControlLayout) getParent()).addDrawer(cloneData);
    }

    @Override
    public void removeButton() {
        ControlLayout layout = getControlLayoutParent();
        for(ControlSubButton subButton : buttons){
            layout.removeView(subButton);
        }

        layout.getLayout().mDrawerDataList.remove(getDrawerData());
        layout.removeView(this);
    }

}
