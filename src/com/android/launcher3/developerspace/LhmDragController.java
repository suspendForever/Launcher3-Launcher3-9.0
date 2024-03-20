package com.android.launcher3.developerspace;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;

import com.android.launcher3.DragSource;
import com.android.launcher3.ItemInfo;

public class LhmDragController implements LhmTouchController, LhmDragDriver.EventListener {

    /**
     * X coordinate of the down event.
     */
    private int mMotionDownX;

    /**
     * Y coordinate of the down event.
     */
    private int mMotionDownY;

    private LhmDropTarget mLastDropTarget;

    private LhmDragObject mDragObject;

    private LhmDragDriver mDragDriver = null;

    private ILauncher mLauncher;

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    public LhmDragController(ILauncher launcher) {
        this.mLauncher = launcher;
    }

    public LhmDragView startDrag(Bitmap b, int dragLayerX, int dragLayerY,
                                 LhmDragSource source, LhmItemInfo dragInfo,
                                 float initialDragViewScale, float dragViewScaleOnDrop) {

        final int registrationX = mMotionDownX - dragLayerX;
        final int registrationY = mMotionDownY - dragLayerY;


        mLastDropTarget = null;

        mDragObject = new LhmDragObject();

        final float scaleDps = 0f;

        final LhmDragView dragView = mDragObject.dragView = new LhmDragView(mLauncher, b, registrationX,
                registrationY, initialDragViewScale, dragViewScaleOnDrop, scaleDps);

        mDragObject.dragComplete = false;
        mDragObject.xOffset = mMotionDownX - (dragLayerX + dragRegionLeft);
        mDragObject.yOffset = mMotionDownY - (dragLayerY + dragRegionTop);

        mDragDriver = LhmDragDriver.create(mLauncher.getContext(), this, mDragObject);

        mDragObject.dragSource = source;
        mDragObject.dragInfo = dragInfo;
        mDragObject.originalDragInfo = new LhmItemInfo();
        mDragObject.originalDragInfo.copyFrom(dragInfo);

        dragView.show(mMotionDownX, mMotionDownY);

        return null;
    }


    @Override
    public void onDriverDragMove(float x, float y) {

    }

    @Override
    public void onDriverDragExitWindow() {

    }

    @Override
    public void onDriverDragEnd(float x, float y) {

    }

    @Override
    public void onDriverDragCancel() {

    }
}
