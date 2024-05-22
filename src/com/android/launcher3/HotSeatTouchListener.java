package com.android.launcher3;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.ViewConfiguration.getLongPressTimeout;

import static com.android.launcher3.LauncherState.NORMAL;

import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.views.OptionsPopupView;

public class HotSeatTouchListener implements View.OnTouchListener,Runnable {

    private static final int STATE_CANCELLED = 0;
    private static final int STATE_REQUESTED = 1;
    private static final int STATE_PENDING_PARENT_INFORM = 2;
    private static final int STATE_COMPLETED = 3;

    private int mLongPressState = STATE_CANCELLED;

    private final PointF mTouchDownPoint = new PointF();


    private Hotseat mHotseat;
    private Launcher mLauncher;

    private final Rect mTempRect = new Rect();


    public HotSeatTouchListener(Hotseat hotseat, Launcher launcher) {
        this.mHotseat = hotseat;
        this.mLauncher = launcher;
    }

    private boolean canHandleLongPress() {
        return AbstractFloatingView.getTopOpenView(mLauncher) == null
                && mLauncher.isInState(NORMAL);
    }

    private void cancelLongPress() {
        mHotseat.removeCallbacks(this);
        mLongPressState = STATE_CANCELLED;
    }


    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        Log.d("test0521", "touchlistener onTouch: "+ev.getAction());
        int action = ev.getActionMasked();
        if (action == ACTION_DOWN) {
            // Check if we can handle long press.
            boolean handleLongPress = canHandleLongPress();

            if (handleLongPress) {
                // Check if the event is not near the edges
                DeviceProfile dp = mLauncher.getDeviceProfile();
                DragLayer dl = mLauncher.getDragLayer();
                Rect insets = dp.getInsets();

                mTempRect.set(insets.left, insets.top, dl.getWidth() - insets.right,
                        dl.getHeight() - insets.bottom);
                mTempRect.inset(dp.edgeMarginPx, dp.edgeMarginPx);
                handleLongPress = mTempRect.contains((int) ev.getX(), (int) ev.getY());
            }

            cancelLongPress();
            if (handleLongPress) {
                mLongPressState = STATE_REQUESTED;
                mTouchDownPoint.set(ev.getX(), ev.getY());
                mHotseat.postDelayed(this, getLongPressTimeout());
            }

            mHotseat.onTouchEvent(ev);
            // Return true to keep receiving touch events
            return true;
        }

        if (mLongPressState == STATE_PENDING_PARENT_INFORM) {
            // Inform the workspace to cancel touch handling
            ev.setAction(ACTION_CANCEL);
            mHotseat.onTouchEvent(ev);

            ev.setAction(action);
            mLongPressState = STATE_COMPLETED;
        }

        final boolean result;
        if (mLongPressState == STATE_COMPLETED) {
            // We have handled the touch, so workspace does not need to know anything anymore.
            result = true;
        } else if (mLongPressState == STATE_REQUESTED) {
            mHotseat.onTouchEvent(ev);

            result = true;
        } else {
            // We don't want to handle touch, let workspace handle it as usual.
            result = false;
        }

        if(action==ACTION_MOVE){
            cancelLongPress();
        }

        if (action == ACTION_UP || action == ACTION_CANCEL) {
            cancelLongPress();
        }
        return result;
    }


    @Override
    public void run() {
        if (mLongPressState == STATE_REQUESTED) {
            if (canHandleLongPress()) {
                mLongPressState = STATE_PENDING_PARENT_INFORM;
                mHotseat.getParent().requestDisallowInterceptTouchEvent(true);

                mHotseat.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);

                OptionsPopupView.showDefaultOptions(mLauncher, mTouchDownPoint.x, mTouchDownPoint.y);
            } else {
                cancelLongPress();
            }
        }
    }
}
