package com.android.launcher3.developerspace;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class LhmBaseDragLayer<T extends ILauncher> extends FrameLayout {

    private static final String TAG = "LhmBaseDragLayer";

    private TouchCompleteListener mTouchCompleteListener;

    protected LhmTouchController mActiveController;

    protected LhmTouchController[] mTouchControllers;

    protected final T mActivity;

    public LhmBaseDragLayer(@NonNull Context context) {
        super(context);
        mActivity = (T) context;
    }

    public LhmBaseDragLayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mActivity = (T) context;
    }

    public LhmBaseDragLayer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivity = (T) context;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        LogUtil.d(TAG, "onInterceptTouchEvent: " + ev.getAction());
        //DragLayer派发事件 包括拖拽view有关的一系列事件
        int action = ev.getAction();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        } else if (action == MotionEvent.ACTION_DOWN) {
            mActivity.finishAutoCancelActionMode();
        }

        boolean hasFind = findActiveController(ev);
        LogUtil.d(TAG, "onInterceptTouchEvent: findController " + hasFind);
        //当触发了拖动后拦截此事件 自己处理
        return hasFind;
    }

    protected boolean findActiveController(MotionEvent event) {
        mActiveController = null;

        for (LhmTouchController controller : mTouchControllers) {
            if (controller.onControllerTouchEvent(event)) {
                mActiveController = controller;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (mTouchCompleteListener != null) {
                mTouchCompleteListener.onTouchComplete();
            }
            mTouchCompleteListener = null;
        }

        if (mActiveController != null) {
            return mActiveController.onControllerTouchEvent(event);
        } else {
            return findActiveController(event);
        }

    }

    public interface TouchCompleteListener {
        void onTouchComplete();
    }
}
