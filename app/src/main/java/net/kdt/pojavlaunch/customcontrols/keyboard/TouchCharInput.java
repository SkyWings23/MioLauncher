package net.kdt.pojavlaunch.customcontrols.keyboard;

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 输入法桥接框：调出系统输入法，并把输入的字符转发到游戏（聊天/命令输入）。
 * 点击「呼出输入法」按钮时 enable()，失焦/完成时 disable()。
 */
public class TouchCharInput extends android.widget.EditText {
    public static final String TEXT_FILLER = "                              ";

    private boolean mIsDoingInternalChanges = false;
    private CharacterSenderStrategy mCharacterSender = new LwjglCharSender();

    public TouchCharInput(@NonNull Context context) {
        this(context, null);
    }

    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        disable();
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            disable();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    /** 切换输入法：有焦点则关闭，否则打开 */
    public void switchKeyboardState() {
        android.util.Log.i("MioKB", "TouchCharInput.switchKeyboardState, hasFocus=" + hasFocus());
        if (hasFocus()) {
            clear();
            disable();
        } else {
            enable();
        }
    }

    /** 清空残留输入（不影响游戏内输入） */
    public void clear() {
        mIsDoingInternalChanges = true;
        Editable editable = getEditableText();
        if (editable != null) {
            editable.clear();
            editable.append(TEXT_FILLER);
            Selection.setSelection(editable, TEXT_FILLER.length());
        }
        mIsDoingInternalChanges = false;
    }

    /** 弹出输入法并获取焦点 */
    public void enable() {
        android.util.Log.i("MioKB", "TouchCharInput.enable");
        // 沉浸式全屏会抑制软键盘弹出：先临时退出沉浸式，输入法关闭后由 disable() 恢复
        setFullscreen(false);
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        setEnabled(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setVisibility(VISIBLE);
        requestFocus();
        if (imm != null) {
            post(() -> imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT));
        }
    }

    /** 关闭输入法并释放焦点 */
    public void disable() {
        clear();
        setVisibility(GONE);
        clearFocus();
        setEnabled(false);
        setFocusable(false);
        setFullscreen(true);
    }

    /** 切换沉浸式全屏（弹出输入法时临时退出，避免被全屏抑制） */
    private void setFullscreen(boolean fullscreen) {
        try {
            android.app.Activity act = getActivity();
            if (act == null) return;
            if (fullscreen) {
                act.getWindow().getDecorView().setSystemUiVisibility(
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            } else {
                act.getWindow().getDecorView().setSystemUiVisibility(
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        } catch (Exception ignored) {}
    }

    private android.app.Activity getActivity() {
        android.content.Context c = getContext();
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof android.app.Activity) return (android.app.Activity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private void sendEnter() {
        mCharacterSender.sendEnter();
        clear();
    }

    public void setCharacterSender(CharacterSenderStrategy characterSender) {
        mCharacterSender = characterSender;
    }

    private void setup() {
        // 对齐 FCL 的 TouchCharInput 配置：这些 imeOptions/inputType 保证输入法正常弹出
        setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                | android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        setInputType(android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                | android.text.InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_VARIATION_FILTER);
        setGravity(android.view.Gravity.BOTTOM);
        setEms(10);
        addTextChangedListener(new InputTextWatcher());
        setOnEditorActionListener((v, i, keyEvent) -> {
            sendEnter();
            clear();
            disable();
            return false;
        });
        setSingleLine(false);
        setTextColor(android.graphics.Color.TRANSPARENT);
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setPadding(0, 0, 0, 0);
        clear();
        disable();
    }

    private class InputTextWatcher implements android.text.TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
            if (mIsDoingInternalChanges) return;
            if (mCharacterSender != null) {
                for (int i = 0; i < lengthBefore; ++i) {
                    mCharacterSender.sendBackspace();
                }
                for (int i = start, count = 0; count < lengthAfter; ++i) {
                    mCharacterSender.sendChar(text.charAt(i));
                    ++count;
                }
            }
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (mIsDoingInternalChanges) return;
            if (editable.length() < 1) clear();
        }
    }
}
