package net.kdt.pojavlaunch.customcontrols.handleview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.Button;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;

@SuppressLint("AppCompatCustomView")
public class AddSubButton extends Button implements ActionButtonInterface {
    public AddSubButton(Context context) {super(context); init();}
    public AddSubButton(Context context, @Nullable AttributeSet attrs) {super(context, attrs); init();}

    public void init() {
        setOnClickListener(this);
        setAllCaps(false);
        setText("加子按钮");
        setTextColor(Color.WHITE);
        setTextSize(12f);
        setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC27AE60);
        g.setCornerRadius(dp(8));
        setBackground(g);
    }

    private ControlInterface mCurrentlySelectedButton = null;

    @Override
    public boolean shouldBeVisible() {
        return mCurrentlySelectedButton != null && mCurrentlySelectedButton instanceof ControlDrawer;
    }

    @Override
    public void setFollowedView(ControlInterface view) {
        mCurrentlySelectedButton = view;
    }

    @Override
    public void onClick() {
        if(mCurrentlySelectedButton instanceof ControlDrawer){
            ((ControlDrawer)mCurrentlySelectedButton).getControlLayoutParent().addSubButton(
                    (ControlDrawer)mCurrentlySelectedButton,
                    new ControlData()
            );
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
