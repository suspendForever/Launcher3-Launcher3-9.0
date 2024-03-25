package com.android.launcher3.developerspace;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ItemInfo;


import java.util.ArrayList;

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

    // temporaries to avoid gc thrash
    private Rect mRectTemp = new Rect();

    private int mDistanceSinceScroll = 0;

    private final int[] mCoordinatesTemp = new int[2];

    /**
     * Who can receive drop events
     */
    private ArrayList<LhmDropTarget> mDropTargets = new ArrayList<>();


    int mLastTouch[] = new int[2];

    private int mTmpPoint[] = new int[2];

    private ArrayList<LhmDragController.DragListener> mListeners = new ArrayList<>();

    long mLastTouchUpTime = -1;

    private Rect mDragLayerRect = new Rect();


    public interface DragListener {
        /**
         * A drag has begun
         *
         * @param dragObject The object being dragged
         */
        void onDragStart(LhmDragObject dragObject);

        /**
         * The drag has ended
         */
        void onDragEnd();
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        if (mDragDriver == null) return false;

        int action = ev.getAction();
        int[] dragLayerPos = getClampedDragLayerPos(ev.getX(), ev.getY());
        int dragLayerX = dragLayerPos[0];
        int dragLayerY = dragLayerPos[1];

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mMotionDownX = dragLayerX;
                mMotionDownY = dragLayerY;
                break;
            }
        }
        return mDragDriver.onTouchEvent(ev);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {


        final int action = ev.getAction();
        final int[] dragLayerPos = getClampedDragLayerPos(ev.getX(), ev.getY());
        final int dragLayerX = dragLayerPos[0];
        final int dragLayerY = dragLayerPos[1];

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                mMotionDownX = dragLayerX;
                mMotionDownY = dragLayerY;
                break;
            case MotionEvent.ACTION_UP:
                mLastTouchUpTime = System.currentTimeMillis();
                break;
        }

        //只有dragdriver 不为空时 且 dragdriver要拦截此事件时 返回true
        return mDragDriver != null && mDragDriver.onInterceptTouchEvent(ev);
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
        mDragObject.xOffset = mMotionDownX - (dragLayerX);
        mDragObject.yOffset = mMotionDownY - (dragLayerY);

        mDragDriver = LhmDragDriver.create(mLauncher.getContext(), this, mDragObject);

        mDragObject.dragSource = source;
        mDragObject.dragInfo = dragInfo;
        mDragObject.originalDragInfo = new LhmItemInfo();
        mDragObject.originalDragInfo.copyFrom(dragInfo);

        dragView.show(mMotionDownX, mMotionDownY);
        mDistanceSinceScroll = 0;

        callOnDragStart();
        mLastTouch[0] = mMotionDownX;
        mLastTouch[1] = mMotionDownY;
        handleMoveEvent(mMotionDownX, mMotionDownY);

        return dragView;
    }

    private void callOnDragStart() {
        for (DragListener listener : new ArrayList<>(mListeners)) {
            listener.onDragStart(mDragObject);
        }
    }

    private void handleMoveEvent(int x, int y) {
        //移动dragview
        mDragObject.dragView.move(x, y);

        // Drop on someone?
        final int[] coordinates = mCoordinatesTemp;
        LhmDropTarget dropTarget = findDropTarget(x, y, coordinates);
        mDragObject.x = coordinates[0];
        mDragObject.y = coordinates[1];
        checkTouchMove(dropTarget);

        // Check if we are hovering over the scroll areas
        //检查我们是否将鼠标悬停在滚动区域上
        mDistanceSinceScroll += Math.hypot(mLastTouch[0] - x, mLastTouch[1] - y);
        mLastTouch[0] = x;
        mLastTouch[1] = y;

    }

    private LhmDropTarget findDropTarget(int x, int y, int[] dropCoordinates) {
        mDragObject.x = x;
        mDragObject.y = y;

        final Rect r = mRectTemp;
        final ArrayList<LhmDropTarget> dropTargets = mDropTargets;
        final int count = dropTargets.size();
        for (int i = count - 1; i >= 0; i--) {
            LhmDropTarget target = dropTargets.get(i);
            if (!target.isDropEnabled())
                continue;

            target.getHitRectRelativeToDragLayer(r);
            if (r.contains(x, y)) {
                dropCoordinates[0] = x;
                dropCoordinates[1] = y;
                mLauncher.getDragLayer().mapCoordInSelfToDescendant((View) target, dropCoordinates);
                return target;
            }
        }
        // Pass all unhandled drag to workspace. Workspace finds the correct
        // cell layout to drop to in the existing drag/drop logic.
        dropCoordinates[0] = x;
        dropCoordinates[1] = y;
        mLauncher.getDragLayer().mapCoordInSelfToDescendant(mLauncher.getRootView(),
                dropCoordinates);
        return mLauncher.getDropTarget();
    }

    private void checkTouchMove(LhmDropTarget dropTarget) {
        if (dropTarget != null) {
            if (mLastDropTarget != dropTarget) {
                if (mLastDropTarget != null) {
                    mLastDropTarget.onDragExit(mDragObject);
                }
                dropTarget.onDragEnter(mDragObject);
            }
            dropTarget.onDragOver(mDragObject);
        } else {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
        }
        mLastDropTarget = dropTarget;
    }


    private int[] getClampedDragLayerPos(float x, float y) {
        mLauncher.getDragLayer().getLocalVisibleRect(mDragLayerRect);
        mTmpPoint[0] = (int) Math.max(mDragLayerRect.left, Math.min(x, mDragLayerRect.right - 1));
        mTmpPoint[1] = (int) Math.max(mDragLayerRect.top, Math.min(y, mDragLayerRect.bottom - 1));
        return mTmpPoint;
    }

    @Override
    public void onDriverDragMove(float x, float y) {
        final int[] dragLayerPos = getClampedDragLayerPos(x, y);

        handleMoveEvent(dragLayerPos[0], dragLayerPos[1]);
    }

    @Override
    public void onDriverDragExitWindow() {

    }

    @Override
    public void onDriverDragEnd(float x, float y) {
        LhmDropTarget dropTarget = findDropTarget((int) x, (int) y, mCoordinatesTemp)

//        drop(dropTarget, flingAnimation);

//        endDrag();
    }

    @Override
    public void onDriverDragCancel() {
        cancelDrag();
    }

    private void cancelDrag() {
    }
}
