package com.android.launcher3;


import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class CustomHScrollView extends ViewGroup {
    private final Launcher mLauncher;

    public CustomHScrollView(Context context) {
        this(context, null);
    }

    public CustomHScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomHScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }
}