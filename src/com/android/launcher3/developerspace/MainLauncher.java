package com.android.launcher3.developerspace;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.CellLayout;

public class MainLauncher extends Activity implements ILauncher,LhmDropTarget {

    private CellLayout mDropToLayout = null;

    float[] mDragViewVisualCenter = new float[2];

    private LhmDragController mDragController;

    LhmCellLayout mDragTargetLayout = null;
    private LhmCellLayout mDragOverlappingLayout;


    public void addItemInCell(View child){
        mDragController.addDropTarget((LhmDropTarget) child);
    }


    @Override
    public Boolean finishAutoCancelActionMode() {
        return null;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public LhmDragLayer getDragLayer() {
        return null;
    }

    @Override
    public LhmDragController getDragController() {
        return null;
    }

    @Override
    public View getRootView() {
        return null;
    }

    @Override
    public LhmDropTarget getDropTarget() {
        return null;
    }


    @Override
    public boolean isDropEnabled() {
        return false;
    }

    @Override
    public void onDrop(LhmDragObject dragObject) {

    }

    @Override
    public void onDragEnter(LhmDragObject dragObject) {


        mDropToLayout = null;
        mDragViewVisualCenter = dragObject.getVisualCenter(mDragViewVisualCenter);
        setDropLayoutForDragObject(dragObject, mDragViewVisualCenter[0]);
    }

    private boolean setDropLayoutForDragObject(LhmDragObject d, float centerX){
        ViewGroup rootView = (ViewGroup) getRootView();
        LhmCellLayout layout = (LhmCellLayout)rootView.getChildAt(0);
        if (layout != mDragTargetLayout) {
            setCurrentDropLayout(layout);
            setCurrentDragOverlappingLayout(layout);
            return true;
        }
        return false;
    }

    void  setCurrentDropLayout(LhmCellLayout layout){
        if (mDragTargetLayout != null) {
//            mDragTargetLayout.revertTempState();
//            mDragTargetLayout.onDragExit();
        }
        mDragTargetLayout = layout;
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragEnter();
        }

    }

    void setCurrentDragOverlappingLayout(LhmCellLayout layout) {
//        if (mDragOverlappingLayout != null) {
//            mDragOverlappingLayout.setIsDragOverlapping(false);
//        }
//        mDragOverlappingLayout = layout;
//        if (mDragOverlappingLayout != null) {
//            mDragOverlappingLayout.setIsDragOverlapping(true);
//        }
//        // Invalidating the scrim will also force this CellLayout
//        // to be invalidated so that it is highlighted if necessary.
//        mLauncher.getDragLayer().getScrim().invalidate();
    }

    @Override
    public void onDragOver(LhmDragObject dragObject) {


    }

    @Override
    public void onDragExit(LhmDragObject dragObject) {

    }

    @Override
    public boolean acceptDrop(LhmDragObject dragObject) {
        return false;
    }

    @Override
    public void prepareAccessibilityDrop() {

    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {

    }
}
