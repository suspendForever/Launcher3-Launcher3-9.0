package com.android.launcher3.developerspace;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class WidgetContainer extends ViewGroup {

    private int mCellWidth = 0;

    private int mCellHeight = 0;

    private int mCountX = 0;

    private int mCountY = 0;

    private float widgetScale = 0.8f;


    public WidgetContainer(Context context) {
        super(context);
    }

    public WidgetContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WidgetContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCellDimensions(int cellWidth, int cellHeight, int countX, int countY) {
        mCellWidth = cellWidth;
        mCellHeight = cellHeight;
        mCountX = countX;
        mCountY = countY;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                measureChild(child);
            }
        }
    }

    private void measureChild(View view) {
        LhmCellLayout.LayoutParams layoutParams = (LhmCellLayout.LayoutParams) view.getLayoutParams();
        //重置子view的宽度和高度，这两个值都根据所占的cell的长度单位和单位长度的乘积所计算
        layoutParams.setup(mCellWidth, mCellHeight, false, mCountX, widgetScale, widgetScale);
        //给定子view的宽度值和模式，代表了view最多只有cellHSpan个单位这么宽
        int childWidthMeasureSpec =
                MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY);
        int childHeightMeasureSpec =
                MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY);
        view.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return super.generateLayoutParams(p);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) return;
            LhmCellLayout.LayoutParams lp = (LhmCellLayout.LayoutParams) child.getLayoutParams();
            int childLeft = lp.x;
            int childTop = lp.y;
            if (child instanceof LhmLauncherAppWidgetHostView) {
                LhmLauncherAppWidgetHostView hostView = (LhmLauncherAppWidgetHostView) child;
                hostView.setScaleToFit(widgetScale);
                hostView.setTranslationForCentering(
                        -(lp.width - lp.width * widgetScale) / 2.0f,
                        -(lp.height - lp.height * widgetScale) / 2.0f
                );
            }
            child.layout(childLeft, childTop, childLeft + lp.width, childTop + lp.height);
        }
    }
}
