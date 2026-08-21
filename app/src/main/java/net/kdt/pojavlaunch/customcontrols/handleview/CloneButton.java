package net.kdt.pojavlaunch.customcontrols.handleview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.Button;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;

@SuppressLint("AppCompatCustomView")
public class CloneButton extends Button implements ActionButtonInterface {
    public CloneButton(Context context) {super(context); init();}
    public CloneButton(Context context, @Nullable AttributeSet attrs) {super(context, attrs); init();}

    public void init() {
        setOnClickListener(this);
        setAllCaps(true);
        setText("克隆");
        setTextColor(Color.WHITE);
        setTextSize(12f);
        setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC3A6EA5);
        g.setCornerRadius(dp(8));
        setBackground(g);
    }

    private ControlInterface mCurrentlySelectedButton = null;

    @Override
    public boolean shouldBeVisible() {
        return mCurrentlySelectedButton != null;
    }

    @Override
    public void setFollowedView(ControlInterface view) {
        mCurrentlySelectedButton = view;
    }

    @Override
    public void onClick() {
        if(mCurrentlySelectedButton == null) return;

        mCurrentlySelectedButton.cloneButton();
        mCurrentlySelectedButton.getControlLayoutParent().removeEditWindow();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
