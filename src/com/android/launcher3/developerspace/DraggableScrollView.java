package com.android.launcher3.developerspace;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class DraggableScrollView extends ViewGroup {
    private int mLastX;
    private int mLastY;
    private int mScrollX;
    private View mDraggedView;
    private int mDraggedIndex;

    public DraggableScrollView(Context context) {
        super(context);
        init();
    }

    public DraggableScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DraggableScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 初始化相关参数
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childLeft = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(childLeft, 0, childLeft + child.getMeasuredWidth(), child.getMeasuredHeight());
            childLeft += child.getMeasuredWidth();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = 0;
        int height = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            width += child.getMeasuredWidth();
            height = Math.max(height, child.getMeasuredHeight());
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = (int) ev.getX();
                mLastY = (int) ev.getY();
                return false;
            case MotionEvent.ACTION_MOVE:
                int dx = (int) Math.abs(ev.getX() - mLastX);
                int dy = (int) Math.abs(ev.getY() - mLastY);
                if (dx > dy) {
                    return true; // 拦截事件处理水平滚动
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = (int) event.getX();
                mLastY = (int) event.getY();
                int index = pointToPosition(mLastX, mLastY);
                if (index != -1) {
                    mDraggedView = getChildAt(index);
                    mDraggedIndex = index;
                    bringChildToFront(mDraggedView);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mDraggedView != null) {
                    int dx = (int) (event.getX() - mLastX);
                    mDraggedView.offsetLeftAndRight(dx);
                    mLastX = (int) event.getX();
                    checkForSwap();
                } else {
                    int dx = (int) (mLastX - event.getX());
                    scrollBy(dx, 0);
                    mLastX = (int) event.getX();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mDraggedView != null) {
                    mDraggedView = null;
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private int pointToPosition(int x, int y) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (x >= child.getLeft() && x <= child.getRight() && y >= child.getTop() && y <= child.getBottom()) {
                return i;
            }
        }
        return -1;
    }

    private void checkForSwap() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != mDraggedView && mDraggedView.getRight() > child.getLeft() && mDraggedView.getLeft() < child.getRight()) {
                swapViews(mDraggedIndex, i);
                mDraggedIndex = i;
                break;
            }
        }
    }

    private void swapViews(int fromIndex, int toIndex) {
        View fromView = getChildAt(fromIndex);
        View toView = getChildAt(toIndex);

        removeViewAt(fromIndex);
        removeViewAt(toIndex);

        if (fromIndex < toIndex) {
            addView(toView, fromIndex);
            addView(fromView, toIndex);
        } else {
            addView(fromView, toIndex);
            addView(toView, fromIndex);
        }

        animateSwap(fromView, toView);
    }

    private void animateSwap(View fromView, View toView) {
        float fromX = fromView.getX();
        float toX = toView.getX();

        ObjectAnimator animatorFrom = ObjectAnimator.ofFloat(fromView, "x", fromX, toX);
        ObjectAnimator animatorTo = ObjectAnimator.ofFloat(toView, "x", toX, fromX);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animatorFrom, animatorTo);
        set.setDuration(300);
        set.start();
    }
}
