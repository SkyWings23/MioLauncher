package net.kdt.pojavlaunch.customcontrols;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import static org.lwjgl.glfw.CallbackBridge.isGrabbing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.google.gson.JsonSyntaxException;

import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlButton;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlJoystick;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlSubButton;
import net.kdt.pojavlaunch.customcontrols.handleview.ActionRow;
import net.kdt.pojavlaunch.customcontrols.handleview.ControlHandleView;
import net.kdt.pojavlaunch.customcontrols.handleview.EditControlSideDialog;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ControlLayout extends FrameLayout {
	protected CustomControls mLayout;
	/* Accessible when inside the game by ControlInterface implementations, cached for perf. */
	private MinecraftGLSurface mGameSurface = null;

	/* Cache to buttons for performance purposes */
	private List<ControlInterface> mButtons;
	private boolean mModifiable = false;
	private boolean mIsModified;
	private boolean mControlVisible = false;

	private EditControlSideDialog mControlDialog = null;
	private ControlHandleView mHandleView;
	private ControlButtonMenuListener mMenuListener;
	public ActionRow mActionRow = null;
	public String mLayoutFileName;
	private TextView mAddButton = null;
	private LinearLayout mAddMenu = null;
	private TextView mListButton = null;
	private net.kdt.pojavlaunch.customcontrols.handleview.ButtonListDialog mButtonListDialog = null;

	public ControlLayout(Context ctx) {
		super(ctx);
	}

	public ControlLayout(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
	}


	public void loadLayout(String jsonPath) throws IOException, JsonSyntaxException {
		CustomControls layout = LayoutConverter.loadAndConvertIfNecessary(jsonPath);
		if(layout != null) {
			loadLayout(layout);
			updateLoadedFileName(jsonPath);
			return;
		}

		throw new IOException("Unsupported control layout version");
	}

	public void loadLayout(CustomControls controlLayout) {
		boolean sanitizedModified = false;
		if(controlLayout != null) {
			sanitizedModified = LayoutSanitizer.sanitizeLayout(controlLayout);
		}
		if(mActionRow == null){
			mActionRow = new ActionRow(getContext());
			addView(mActionRow);
		}

		removeAllButtons();
		if(mLayout != null) {
			mLayout.mControlDataList = null;
			mLayout = null;
		}

		System.gc();
		mapTable.clear();

		// Cleanup buttons only when input layout is null
		if (controlLayout == null) return;

		mLayout = controlLayout;
		

		// Joystick(s) first, to workaround the touch dispatch
		for(ControlJoystickData joystick : mLayout.mJoystickDataList){
			addJoystickView(joystick);
		}

		//CONTROL BUTTON
		for (ControlData button : controlLayout.mControlDataList) {
			addControlView(button);
		}

		//CONTROL DRAWER
		for(ControlDrawerData drawerData : controlLayout.mDrawerDataList){
			ControlDrawer drawer = addDrawerView(drawerData); if(mModifiable) drawer.areButtonsVisible = true;
		}

		mLayout.scaledAt = LauncherPreferences.PREF_BUTTONSIZE;

		setModified(sanitizedModified);
		mButtons = null;
		getButtonChildren(); // Force refresh
	} // loadLayout

	//CONTROL BUTTON
	public ControlButton addControlButton(ControlData controlButton) {
		mLayout.mControlDataList.add(controlButton);
		return addControlView(controlButton);
	}

	private ControlButton addControlView(ControlData controlButton) {
		final ControlButton view = new ControlButton(this, controlButton);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}
		addView(view);

		setModified(true);
		return view;
	}

	// CONTROL DRAWER
	public ControlDrawer addDrawer(ControlDrawerData drawerData){
		mLayout.mDrawerDataList.add(drawerData);
		return addDrawerView(null);
	}

	private void addDrawerView(){
		addDrawerView(null);
	}

	private ControlDrawer addDrawerView(ControlDrawerData drawerData){

		final ControlDrawer view = new ControlDrawer(this,drawerData == null ? mLayout.mDrawerDataList.get(mLayout.mDrawerDataList.size()-1) : drawerData);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}
		addView(view);
		//CONTROL SUB BUTTON
		for (ControlData subButton : view.getDrawerData().buttonProperties) {
			addSubView(view, subButton);
		}

		setModified(true);
		return view;
	}

	//CONTROL SUB-BUTTON
	public void addSubButton(ControlDrawer drawer, ControlData controlButton){
		//Yep there isn't much here
		drawer.getDrawerData().buttonProperties.add(controlButton);
		addSubView(drawer, drawer.getDrawerData().buttonProperties.get(drawer.getDrawerData().buttonProperties.size()-1 ));
	}

	private void addSubView(ControlDrawer drawer, ControlData controlButton){
		final ControlSubButton view = new ControlSubButton(this, controlButton, drawer);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}else{
			view.setVisible(true);
		}

		addView(view);
		drawer.addButton(view);


		setModified(true);
	}

	// JOYSTICK BUTTON
	public ControlJoystick addJoystickButton(ControlJoystickData data){
		mLayout.mJoystickDataList.add(data);
		return addJoystickView(data);
	}

	private ControlJoystick addJoystickView(ControlJoystickData data){
		ControlJoystick view = new ControlJoystick(this, data);

		if (!mModifiable) {
			view.setAlpha(view.getProperties().opacity);
			view.setFocusable(false);
			view.setFocusableInTouchMode(false);
		}
		addView(view);
		return view;

	}


	private void removeAllButtons() {
		for(ControlInterface button : getButtonChildren()){
			removeView(button.getControlView());
		}

		System.gc();
		//i wanna be sure that all the removed Views will be removed after a reload
		//because if frames will slowly go down after many control changes it will be warm and bad
	}

	public void saveLayout(String path) throws Exception {
		mLayout.save(path);
		setModified(false);
	}

	public void toggleControlVisible(){
		mControlVisible = !mControlVisible;
		setControlVisible(mControlVisible);
	}

	public float getLayoutScale(){
		return mLayout.scaledAt;
	}

	public CustomControls getLayout(){
		return mLayout;
	}

	public void setControlVisible(boolean isVisible) {
		if (mModifiable) return; // Not using on custom controls activity

		mControlVisible = isVisible;
		for(ControlInterface button : getButtonChildren()){
			button.setVisible(((button.getProperties().displayInGame && isGrabbing()) || (button.getProperties().displayInMenu && !isGrabbing())) && isVisible);
		}
	}

	public void setModifiable(boolean isModifiable) {
		if(!isModifiable && mModifiable){
			removeEditWindow();
			autoSave();
		}
		mModifiable = isModifiable;
		if(isModifiable){
			// In edit mode, all controls have to be shown
			for(ControlInterface button : getButtonChildren()){
				button.setVisible(true);
			}
			showAddButton();
		} else {
			hideAddButton();
		}
	}

	public boolean getModifiable(){
		return mModifiable;
	}

	// ============ 编辑模式「添加新控件」按钮 ============
	private void showAddButton() {
		if (mAddButton == null) {
			mAddButton = new TextView(getContext());
			mAddButton.setText("+");
			mAddButton.setTextColor(Color.WHITE);
			mAddButton.setTextSize(26f);
			mAddButton.setGravity(Gravity.CENTER);
			GradientDrawable g = new GradientDrawable();
			g.setColor(0xEE27AE60);
			g.setShape(GradientDrawable.OVAL);
			mAddButton.setBackground(g);
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(52), dp(52));
			lp.gravity = Gravity.TOP | Gravity.END;
			lp.setMargins(0, dp(70), dp(14), 0);
			mAddButton.setLayoutParams(lp);
			mAddButton.setElevation(12f);
			mAddButton.setOnClickListener(v -> toggleAddMenu());
			addView(mAddButton);
		}
		mAddButton.setVisibility(VISIBLE);

		if (mListButton == null) {
			mListButton = new TextView(getContext());
			mListButton.setText("≡");
			mListButton.setTextColor(Color.WHITE);
			mListButton.setTextSize(24f);
			mListButton.setGravity(Gravity.CENTER);
			GradientDrawable lg = new GradientDrawable();
			lg.setColor(0xEE3A6EA5);
			lg.setShape(GradientDrawable.OVAL);
			mListButton.setBackground(lg);
			FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(dp(52), dp(52));
			llp.gravity = Gravity.TOP | Gravity.END;
			llp.setMargins(0, dp(126), dp(14), 0);
			mListButton.setLayoutParams(llp);
			mListButton.setElevation(12f);
			mListButton.setOnClickListener(v -> toggleButtonList());
			addView(mListButton);
		}
		mListButton.setVisibility(VISIBLE);
	}

	private void hideAddButton() {
		if (mAddButton != null) mAddButton.setVisibility(GONE);
		if (mAddMenu != null) mAddMenu.setVisibility(GONE);
		if (mListButton != null) mListButton.setVisibility(GONE);
		if (mButtonListDialog != null) mButtonListDialog.setVisibility(GONE);
	}

	private void toggleButtonList() {
		if (mButtonListDialog == null) {
			mButtonListDialog = new net.kdt.pojavlaunch.customcontrols.handleview.ButtonListDialog(getContext(), this);
			addView(mButtonListDialog, new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			mButtonListDialog.setVisibility(GONE);
		}
		mButtonListDialog.setVisibility(mButtonListDialog.getVisibility() == VISIBLE ? GONE : VISIBLE);
		if (mButtonListDialog.getVisibility() == VISIBLE) mButtonListDialog.refresh();
	}

	// ============ 按钮组（布局）管理 ============
	/** 布局保存目录：files/mio/controlmap */
	public java.io.File getControlMapDir() {
		java.io.File dir = new java.io.File(getContext().getFilesDir(), "mio/controlmap");
		dir.mkdirs();
		return dir;
	}

	public String getCurrentGroupName() {
		if (mLayoutFileName == null || mLayoutFileName.isEmpty()) return "default";
		String n = mLayoutFileName;
		int slash = n.lastIndexOf('/');
		if (slash >= 0) n = n.substring(slash + 1);
		if (n.isEmpty()) n = "default";
		return n;
	}

	public void setCurrentGroupName(String name) {
		String safe = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5-]", "_");
		if (safe.isEmpty()) return;
		mLayoutFileName = safe;
		try {
			android.content.SharedPreferences sp = getContext()
					.getSharedPreferences("mio_settings", Context.MODE_PRIVATE);
			sp.edit().putString("current_group", safe).apply();
		} catch (Exception ignored) {}
	}

	/** 上次使用的按钮组名（重启后恢复） */
	public String getLastUsedGroupName() {
		try {
			android.content.SharedPreferences sp = getContext()
					.getSharedPreferences("mio_settings", Context.MODE_PRIVATE);
			return sp.getString("current_group", "default");
		} catch (Exception e) {
			return "default";
		}
	}

	/** 自动保存当前布局到当前按钮组文件 */
	public void autoSave() {
		try {
			java.io.File dir = getControlMapDir();
			java.io.File out = new java.io.File(dir, getCurrentGroupName() + ".json");
			if (mLayout != null) mLayout.save(out.getAbsolutePath());
			setModified(false);
		} catch (Exception e) {
			Log.e("ControlLayout", "autoSave failed", e);
		}
	}

	/** 列出所有已有按钮组名 */
	public java.util.List<String> listControlGroups() {
		java.util.List<String> names = new ArrayList<>();
		java.io.File[] files = getControlMapDir().listFiles((d, n) -> n.endsWith(".json"));
		if (files != null) {
			for (java.io.File f : files) {
				String n = f.getName();
				names.add(n.substring(0, n.length() - 5));
			}
		}
		if (!names.contains("default")) names.add("default");
		return names;
	}

	/** 加载指定按钮组（先自动保存当前） */
	public void loadControlGroup(String name) {
		if (name == null || name.equals(getCurrentGroupName())) return;
		autoSave();
		try {
			java.io.File f = new java.io.File(getControlMapDir(), name + ".json");
			java.io.File src = f.exists() ? f : new java.io.File(getContext().getFilesDir(), "default.json");
			loadLayout(src.getAbsolutePath());
			setCurrentGroupName(name);
			refreshControlButtonPositions();
		} catch (Exception e) {
			Log.e("ControlLayout", "loadControlGroup failed", e);
		}
	}

	// ============ 导出 / 导入（FCL 兼容）============
	public static final int LAYOUT_IMPORT_REQUEST = 7001;

	/** 导出当前按钮组：保存 + 系统分享（文件为 FCL 布局格式） */
	public void exportLayoutForSharing() {
		try {
			autoSave();
			java.io.File file = new java.io.File(getControlMapDir(), getCurrentGroupName() + ".json");
			android.content.Context ctx = getContext();
			android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(ctx,
					ctx.getPackageName() + ".fileprovider", file);
			android.content.Intent share = new android.content.Intent(android.content.Intent.ACTION_SEND);
			share.setType("application/json");
			share.putExtra(android.content.Intent.EXTRA_STREAM, uri);
			share.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
			share.putExtra(android.content.Intent.EXTRA_SUBJECT, getCurrentGroupName() + ".json");
			android.content.Intent chooser = android.content.Intent.createChooser(share, "导出按钮组（FCL 兼容）");
			if (ctx instanceof android.app.Activity) {
				((android.app.Activity) ctx).startActivity(chooser);
			} else {
				chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
				ctx.startActivity(chooser);
			}
		} catch (Exception e) {
			Log.e("ControlLayout", "export failed", e);
			Toast.makeText(getContext(), "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	/** 打开系统文件选择器导入按钮组（FCL 布局 JSON） */
	public void startLayoutImport() {
		android.content.Context ctx = getContext();
		android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES,
				new String[]{"application/json", "text/plain", "application/octet-stream"});
		if (ctx instanceof android.app.Activity) {
			((android.app.Activity) ctx).startActivityForResult(intent, LAYOUT_IMPORT_REQUEST);
		} else {
			Toast.makeText(ctx, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
		}
	}

	/** 处理导入结果：把选择的文件复制到按钮组目录并加载 */
	public void importLayoutFromUri(android.net.Uri uri) {
		try {
			android.content.Context ctx = getContext();
			String name = "import_" + System.currentTimeMillis();
			java.io.File out = new java.io.File(getControlMapDir(), name + ".json");
			try (java.io.InputStream in = ctx.getContentResolver().openInputStream(uri);
				 java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
				if (in == null) throw new java.io.IOException("无法读取所选文件");
				byte[] buf = new byte[65536];
				int n;
				while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
			}
			autoSave();
			loadLayout(out.getAbsolutePath());
			setCurrentGroupName(name);
			refreshControlButtonPositions();
			Toast.makeText(ctx, "已导入按钮组: " + name, Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Log.e("ControlLayout", "import failed", e);
			Toast.makeText(getContext(), "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	/** 新建空按钮组并加载 */
	public void createControlGroup(String name) {
		if (name == null) return;
		String safe = name.trim().replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5-]", "_");
		if (safe.isEmpty()) return;
		autoSave();
		loadLayout(new CustomControls());
		setCurrentGroupName(safe);
		autoSave();
	}

	private void toggleAddMenu() {
		if (mAddMenu == null) {
			mAddMenu = new LinearLayout(getContext());
			mAddMenu.setOrientation(LinearLayout.VERTICAL);
			GradientDrawable g = new GradientDrawable();
			g.setColor(0xF21E1E24);
			g.setCornerRadius(dp(10));
			mAddMenu.setBackground(g);
			mAddMenu.setElevation(12f);
			mAddMenu.addView(addMenuItem("添加按钮", () -> addNewControl(0)));
			mAddMenu.addView(addMenuItem("添加抽屉", () -> addNewControl(1)));
			mAddMenu.addView(addMenuItem("添加摇杆", () -> addNewControl(2)));
			mAddMenu.addView(addMenuItem("添加键盘按钮", () -> addNewControl(3)));
			FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.gravity = Gravity.TOP | Gravity.END;
			lp.setMargins(0, dp(124), dp(14), 0);
			mAddMenu.setLayoutParams(lp);
			addView(mAddMenu);
		}
		mAddMenu.setVisibility(mAddMenu.getVisibility() == VISIBLE ? GONE : VISIBLE);
	}

	private TextView addMenuItem(String text, Runnable action) {
		TextView item = new TextView(getContext());
		item.setText(text);
		item.setTextColor(Color.WHITE);
		item.setTextSize(14f);
		item.setGravity(Gravity.CENTER);
		item.setPadding(dp(8), dp(12), dp(8), dp(12));
		item.setOnClickListener(v -> {
			if (mAddMenu != null) mAddMenu.setVisibility(GONE);
			action.run();
		});
		return item;
	}

	private void addNewControl(int type) {
		if (mLayout == null) return;
		ControlInterface created;
		switch (type) {
			case 1:
				created = addDrawer(new ControlDrawerData());
				break;
			case 2:
				created = addJoystickButton(new ControlJoystickData());
				break;
			case 3: {
				// 键盘按钮：按下即调出输入法（游戏内输入指令/聊天）
				ControlData kb = new ControlData("键盘", new int[]{ControlData.SPECIALBTN_KEYBOARD});
				kb.setWidth(dp(50));
				kb.setHeight(dp(50));
				created = addControlButton(kb);
				break;
			}
			default:
				created = addControlButton(new ControlData());
				break;
		}
		editControlButton(created);
	}

	private int dp(float v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}

	public void setModified(boolean isModified) {
		mIsModified = isModified;
	}

	public List<ControlInterface> getButtonChildren(){
		if(mModifiable || mButtons == null){
			mButtons = new ArrayList<>();
			for(int i=0; i<getChildCount(); ++i){
				View v = getChildAt(i);
				if(v instanceof ControlInterface)
					mButtons.add(((ControlInterface) v));
			}
		}

		return mButtons;
	}

	public void refreshControlButtonPositions(){
		for(ControlInterface button : getButtonChildren()){
			button.setDynamicX(button.getProperties().dynamicX);
			button.setDynamicY(button.getProperties().dynamicY);
		}
	}

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if(child instanceof ControlInterface && mControlDialog != null){
			mControlDialog.disappearColor();
            mControlDialog.disappear(false);
        }
    }

    /**
	 * Load the layout if needed, and pass down the burden of filling values
	 * to the button at hand.
	 */
	public void editControlButton(ControlInterface button){
		if(mControlDialog == null){
			// When the panel is null, it needs to inflate first.
			// So inflate it, then process it on the next frame
			mControlDialog = new EditControlSideDialog(getContext(), this);
			addView(mControlDialog, new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			post(() -> editControlButton(button));
			return;
		}

		mControlDialog.internalChanges = true;
		mControlDialog.setCurrentlyEditedButton(button);

		mControlDialog.appear(button.getControlView().getX() + button.getControlView().getWidth()/2f < currentDisplayMetrics.widthPixels/2f);
		button.loadEditValues(mControlDialog);

		mControlDialog.internalChanges = false;

		mControlDialog.disappearColor();

		if(mHandleView == null){
			mHandleView = new ControlHandleView(getContext());
			addView(mHandleView);
		}
		mHandleView.setControlButton(button);

		//mHandleView.show();
	}

	/** Swap the panel if the button position requires it */
	public void adaptPanelPosition(){
		if(mControlDialog != null) mControlDialog.adaptPanelPosition();
	}


	final HashMap<View, ControlInterface> mapTable = new HashMap<>();

	//While this is called onTouch, this should only be called from a ControlButton.
	public void onTouch(View v, MotionEvent ev) {
		ControlInterface lastControlButton = mapTable.get(v);

		// Map location to screen coordinates
		ev.offsetLocation(v.getX(), v.getY());


		//Check if the action is cancelling, reset the lastControl button associated to the view
		if (ev.getActionMasked() == MotionEvent.ACTION_UP
				|| ev.getActionMasked() == MotionEvent.ACTION_CANCEL
				|| ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
			if (lastControlButton != null) lastControlButton.sendKeyPresses(false);
			mapTable.put(v, null);
			return;
		}

		if (ev.getActionMasked() != MotionEvent.ACTION_MOVE) return;


		//Optimization pass to avoid looking at all children again
		if (lastControlButton != null) {
			System.out.println("last control button check" + ev.getX() + "-" + ev.getY() + "-" + lastControlButton.getControlView().getX() + "-" + lastControlButton.getControlView().getY());
			if (ev.getX() > lastControlButton.getControlView().getX()
					&& ev.getX() < lastControlButton.getControlView().getX() + lastControlButton.getControlView().getWidth()
					&& ev.getY() > lastControlButton.getControlView().getY()
					&& ev.getY() < lastControlButton.getControlView().getY() + lastControlButton.getControlView().getHeight()) {
				return;
			}
		}

		//Release last keys
		if (lastControlButton != null) lastControlButton.sendKeyPresses(false);
		mapTable.remove(v);

		// Update the state of all swipeable buttons
		for (ControlInterface button : getButtonChildren()) {
			if (!button.getProperties().isSwipeable) continue;

			if (ev.getX() > button.getControlView().getX()
					&& ev.getX() < button.getControlView().getX() + button.getControlView().getWidth()
					&& ev.getY() > button.getControlView().getY()
					&& ev.getY() < button.getControlView().getY() + button.getControlView().getHeight()) {

				//Press the new key
				if (!button.equals(lastControlButton)) {
					button.sendKeyPresses(true);
					mapTable.put(v, button);
					return;
				}

			}
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mModifiable && event.getActionMasked() != MotionEvent.ACTION_UP || mControlDialog == null)
			return true;

		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);

		// When the input window cannot be hidden, it returns false
		if(!imm.hideSoftInputFromWindow(getWindowToken(), 0)){
			if(mControlDialog.disappearLayer()){
				mActionRow.setFollowedButton(null);
				mHandleView.hide();
			}
		}
		return true;
	}

	public void removeEditWindow() {
		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);

		// When the input window cannot be hidden, it returns false
		imm.hideSoftInputFromWindow(getWindowToken(), 0);
		if(mControlDialog != null) {
			mControlDialog.disappearColor();
			mControlDialog.disappear(true);
		}

		if(mActionRow != null) mActionRow.setFollowedButton(null);
		if(mHandleView != null) mHandleView.hide();
	}

	public void save(String path){
		try {
			mLayout.save(path);
		} catch (IOException e) {Log.e("ControlLayout", "Failed to save the layout at:" + path);}
	}


	public boolean hasMenuButton() {
		for(ControlInterface controlInterface : getButtonChildren()){
			for (int keycode : controlInterface.getProperties().keycodes) {
				if (keycode == ControlData.SPECIALBTN_MENU) return true;
			}
		}
		return false;
	}

	public void setMenuListener(ControlButtonMenuListener menuListener) {
		this.mMenuListener = menuListener;
	}

	public void notifyAppMenu() {
		if(mMenuListener != null) mMenuListener.onClickedMenu();
	}

	/** Cached getter for perf purposes */
	public MinecraftGLSurface getGameSurface(){
		if(mGameSurface == null){
			mGameSurface = findViewById(R.id.main_game_render_view);
		}
		return mGameSurface;
	}

	public void askToExit(EditorExitable editorExitable) {
		if(mIsModified) {
			openSaveDialog(editorExitable);
		}else{
			openExitDialog(editorExitable);
		}
	}

	public void updateLoadedFileName(String path) {
		path = path.replace(Tools.CTRLMAP_PATH, ".");
		path = path.substring(0, path.length() - 5);
		mLayoutFileName = path;
	}

	public String saveToDirectory(String name) throws Exception{
		String jsonPath = Tools.CTRLMAP_PATH + "/" + name + ".json";
		saveLayout(jsonPath);
		return jsonPath;
	}

	/** MioLauncher: 编辑器对话框置空（可玩路径不触发）。 */
	public void openSaveDialog(EditorExitable editorExitable) {}
	public void openLoadDialog() {}
	public void openSetDefaultDialog() {}
	public void openExitDialog(EditorExitable exitListener) {}

	public boolean areControlVisible(){
		return mControlVisible;
	}
}
