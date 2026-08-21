package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.customcontrols.mouse.Touchpad;

/**
 * MioLauncher stub（对齐 FCL 控件所需静态成员）。
 */
public class MainActivity extends Activity {
    public static Touchpad touchpad;
    public static TouchCharInput touchCharInput;
    public static ClipboardManager GLOBAL_CLIPBOARD;

    public static void openLink(String url) {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url));
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            sAppContext.startActivity(i);
        } catch (Exception ignored) {}
    }

    private static Context sAppContext;

    public static void init(Context ctx) {
        sAppContext = ctx.getApplicationContext();
        GLOBAL_CLIPBOARD = (ClipboardManager) sAppContext.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sAppContext = getApplicationContext();
        if (GLOBAL_CLIPBOARD == null)
            GLOBAL_CLIPBOARD = (ClipboardManager) sAppContext.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    public static void switchKeyboardState() {
        android.util.Log.i("MioKB", "switchKeyboardState, touchCharInput=" + (touchCharInput != null));
        if (touchCharInput != null) {
            touchCharInput.switchKeyboardState();
            return;
        }
        // 兜底：无 TouchCharInput 时尝试用系统输入法
        try {
            if (sAppContext == null) return;
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            sAppContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.toggleSoftInput(
                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT, 0);
        } catch (Exception ignored) {}
    }

    public static void toggleMouse(Context ctx) {}

    public void setmLastIndex(int index) {}

    public static void showError(Context ctx, Throwable e, boolean exit) {
        Toast.makeText(ctx, "错误: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}
