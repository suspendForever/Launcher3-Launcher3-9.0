package com.android.launcher3.developerspace;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.R;

public class LhmDragView extends View {

    private final ILauncher mLauncher;

    private final LhmDragLayer mDragLayer;
    private final LhmDragController mDragController;

    public static final int VIEW_ZOOM_DURATION = 150;

    static float sDragAlpha = 1f;
    private final Bitmap mBitmap;
    private final int mRegistrationX;
    private final int mRegistrationY;
    private final float mInitialScale;
    private final float mScaleOnDrop;
    private final Paint mPaint;

    private int mLastTouchX;
    private int mLastTouchY;

    //模糊尺寸轮廓
    private final int mBlurSizeOutline;

    private Boolean mAnimationCancelled = false;

    private Rect mDragRegion = null;


    ValueAnimator mAnim;

    public LhmDragView(ILauncher launcher, Bitmap bitmap, int registrationX, int registrationY, final float initialScale, final float scaleOnDrop, final float finalScaleDps) {
        super(launcher.getContext());
        mLauncher = launcher;
        mDragLayer = launcher.getDragLayer();
        mDragController = launcher.getDragController();

        final float scale = (bitmap.getWidth() + finalScaleDps) / bitmap.getWidth();
        setScaleX(initialScale);
        setScaleY(initialScale);

        // 将视图动画处理到正确的位置
        mAnim = LhmAnimUtils.ofFloat(0f, 1f);
        mAnim.setDuration(VIEW_ZOOM_DURATION);
        mAnim.addUpdateListener(animation -> {
            final float value = (Float) animation.getAnimatedValue();

            setScaleX(initialScale + (value * (scale - initialScale)));
            setScaleY(initialScale + (value * (scale - initialScale)));

            if (sDragAlpha != 1f) {
                setAlpha(sDragAlpha * value + (1f - value));

                if (getParent() == null) animation.cancel();

            }
        });

        mBitmap = bitmap;
        setDragRegion(new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()));

        // 触摸事件所在的缩放位图中的点
        mRegistrationX = registrationX;
        mRegistrationY = registrationY;

        mInitialScale = initialScale;
        mScaleOnDrop = scaleOnDrop;

        //Force a measure, because Workspace uses getMeasuredHeight() before the layout pass
        //强制度量，因为 Workspace 在布局传递之前使用 getMeasuredHeight（）
        int measureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        measure(measureSpec, measureSpec);

        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        mBlurSizeOutline = getResources().getDimensionPixelSize(R.dimen.blur_size_medium_outline);
        setElevation(getResources().getDimension(R.dimen.drag_elevation));


    }

    public void setDragRegion(Rect r) {
        mDragRegion = r;
    }

    public Rect getDragRegion() {
        return mDragRegion;
    }

    public void show(int touchX, int touchY) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, 0);
        lp.width=mBitmap.getWidth();
        lp.height=mBitmap.getHeight();
        setLayoutParams(lp);
        move(touchX, touchY);
        post(()->{
            mAnim.start();
        });
    }

    public void move(int touchX, int touchY) {
        mLastTouchX = touchX;
        mLastTouchY = touchY;
        applyTranslation();
    }

    private void applyTranslation() {
        setTranslationX(mLastTouchX - mRegistrationX);
        setTranslationY(mLastTouchY - mRegistrationY);
    }
}
