package com.android.launcher3.developerspace;

public class LhmCellAndSpan {

    /**
     * Indicates the X position of the associated cell.
     */
    public int cellX = -1;

    /**
     * Indicates the Y position of the associated cell.
     */
    public int cellY = -1;

    /**
     * Indicates the X cell span.
     */
    public int spanX = 1;

    /**
     * Indicates the Y cell span.
     */
    public int spanY = 1;

    public LhmCellAndSpan() {
    }

    public void copyFrom(LhmCellAndSpan copy) {
        cellX = copy.cellX;
        cellY = copy.cellY;
        spanX = copy.spanX;
        spanY = copy.spanY;
    }

    public LhmCellAndSpan(int cellX, int cellY, int spanX, int spanY) {
        this.cellX = cellX;
        this.cellY = cellY;
        this.spanX = spanX;
        this.spanY = spanY;
    }

    public String toString() {
        return "(" + cellX + ", " + cellY + ": " + spanX + ", " + spanY + ")";
    }
}
