package com.android.launcher3.developerspace;


class LhmDragObject {

    public int x=-1;

    public int y=-1;

    public LhmDragView dragView = null;

    public boolean dragComplete = false;

    /**
     * X offset from the upper-left corner of the cell to where we touched.
     */
    public int xOffset = -1;

    /**
     * Y offset from the upper-left corner of the cell to where we touched.
     */
    public int yOffset = -1;

    public LhmDragSource dragSource = null;

    public LhmItemInfo dragInfo = null;
    public LhmItemInfo originalDragInfo;

    public final float[] getVisualCenter(float[] recycle) {
        final float res[] = (recycle == null) ? new float[2] : recycle;

        // These represent the visual top and left of drag view if a dragRect was provided.
        // If a dragRect was not provided, then they correspond to the actual view left and
        // top, as the dragRect is in that case taken to be the entire dragView.
        // R.dimen.dragViewOffsetY.
        int left = x - xOffset;
        int top = y - yOffset;

        // In order to find the visual center, we shift by half the dragRect
        res[0] = left + dragView.getDragRegion().width() / 2;
        res[1] = top + dragView.getDragRegion().height() / 2;

        return res;
    }
}