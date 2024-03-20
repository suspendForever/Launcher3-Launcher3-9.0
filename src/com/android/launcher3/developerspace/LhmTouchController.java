package com.android.launcher3.developerspace;

import android.view.MotionEvent;

public interface LhmTouchController {

    /**
     * Called when the draglayer receives touch event.
     */
    boolean onControllerTouchEvent(MotionEvent ev);

    /**
     * Called when the draglayer receives a intercept touch event.
     */
    boolean onControllerInterceptTouchEvent(MotionEvent ev);

}
