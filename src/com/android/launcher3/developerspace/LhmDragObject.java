package com.android.launcher3.developerspace;


class LhmDragObject {

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

    public LhmItemInfo dragInfo=null;
    public LhmItemInfo originalDragInfo;
}