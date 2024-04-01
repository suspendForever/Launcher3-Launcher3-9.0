package com.android.launcher3.developerspace;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class LhmCellLayout extends ViewGroup {

    public static final int CELL_COUNT_X = 4;
    public static final int CELL_COUNT_Y = 4;

    private int mFixedCellWidth = -1;
    private int mFixedCellHeight = -1;

    private int mCellWidth;

    private int mCellHeight;

    private int mCountX;

    private int mCountY;

    private int mFixedWidth = -1;
    private int mFixedHeight = -1;

    private WidgetContainer mWidgetContainer;

    private LhmGridOccupancy mOccupied = new LhmGridOccupancy(mCountX, mCountY);
    private boolean mDragging;


    public LhmCellLayout(Context context) {
        this(context, null);
    }

    public LhmCellLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LhmCellLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFixedCellHeight = -1;
        mFixedCellWidth = -1;
        mCellHeight = -1;
        mCellWidth = -1;

        mCountX = CELL_COUNT_X;
        mCountY = CELL_COUNT_Y;

        mWidgetContainer = new WidgetContainer(context);
        mWidgetContainer.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY);
        addView(mWidgetContainer);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int childWidthSize = widthSize - (getPaddingLeft() + getPaddingRight());
        int childHeightSize = heightSize - (getPaddingTop() + getPaddingBottom());

        if (mFixedCellHeight < 0 || mFixedCellWidth < 0) {
            int cellWidth = childWidthSize / mCountX;
            int cellHeight = childHeightSize / mCountY;
            if (cellWidth != mCellWidth || cellHeight != mCellHeight) {
                mCellHeight = cellHeight;
                mCellWidth = cellWidth;
                mWidgetContainer.setCellDimensions(mCellWidth, mCellHeight, mCountX, mCountY);
            }
        }

        int newWidth = childWidthSize;
        int newHeight = childHeightSize;
        if (mFixedWidth > 0 && mFixedHeight > 0) {
            newWidth = mFixedWidth;
            newHeight = mFixedHeight;
        } else if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            throw new RuntimeException("CellLayout cannot have UNSPECIFIED dimensions");
        }

        mWidgetContainer.measure(
                MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY)
        );

        int maxWidth = mWidgetContainer.getMeasuredWidth();
        int maxHeight = mWidgetContainer.getMeasuredHeight();
        if (mFixedWidth > 0 && mFixedHeight > 0) {
            setMeasuredDimension(maxWidth, maxHeight);
        } else {
            setMeasuredDimension(widthSize, heightSize);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        left += Math.ceil((getUnusedHorizontalSpace() / 2f));
        int right = r - l - getPaddingRight();
        right -= Math.ceil((getUnusedHorizontalSpace() / 2f));

        int top = getPaddingTop();
        int bottom = b - t - getPaddingBottom();
        mWidgetContainer.layout(left, top, right, bottom);
    }

    private int getUnusedHorizontalSpace() {
        return getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - mCountX * mCellWidth;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    public boolean addViewToCellLayout(View child, int index, LayoutParams lp) {
        if (lp.cellX >= 0 && lp.cellX <= mCountX - 1 && lp.cellY >= 0 && lp.cellY <= mCountY - 1) {
            // If the horizontal or vertical span is set to -1, it is taken to
            // mean that it spans the extent of the CellLayout
            if (lp.cellHSpan < 0) lp.cellHSpan = mCountX;
            if (lp.cellVSpan < 0) lp.cellVSpan = mCountY;
            mWidgetContainer.addView(child, index, lp);
            markCellsAsOccupiedForView(child);
            return true;
        }
        return false;
    }

    private void markCellsAsOccupiedForView(View view) {
        if (view == null || view.getParent() != mWidgetContainer) return;
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        mOccupied.markCells(lp.cellX, lp.cellY, lp.cellHSpan, lp.cellVSpan, true);
    }

    void onDragEnter() {
        mDragging = true;
    }

    class LayoutParams extends ViewGroup.MarginLayoutParams {

        boolean isLockedToGrid = true;

        int cellX = 0;

        int cellY = 0;

        // X coordinate of the view in the layout.
        //布局中视图的 X 坐标。
        int x = 0;

        // Y coordinate of the view in the layout.
        int y = 0;

        /**
         * Temporary horizontal location of the item in the grid during reorder
         * 重新排序期间时 item 在网格中的临时水平位置
         */
        int tmpCellX = 0;

        /**
         * Temporary vertical location of the item in the grid during reorder
         */
        int tmpCellY = 0;

        /**
         * Indicates that the temporary coordinates should be used to layout the items
         */
        boolean useTmpCoords = false;


        int cellHSpan = 0;

        int cellVSpan = 0;

        LayoutParams(Context context, AttributeSet attributeSet) {
            super(context, attributeSet);
            cellHSpan = 1;
            cellVSpan = 1;
        }

        LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            cellHSpan = 1;
            cellVSpan = 1;
        }

        LayoutParams(LayoutParams source) {
            super(source);
            cellX = source.cellX;
            cellY = source.cellY;
            cellHSpan = source.cellHSpan;
            cellVSpan = source.cellVSpan;
        }

        LayoutParams(int cellX, int cellY, int cellHSpan, int cellVSpan) {
            super(MATCH_PARENT, MATCH_PARENT);
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellHSpan = cellHSpan;
            this.cellVSpan = cellVSpan;
        }

        public void setup(
                int cellWidth, int cellHeight, boolean invertHorizontally, int colCount, float cellScaleX, float cellScaleY
        ) {
            if (isLockedToGrid) {
                int myCellHSpan = cellHSpan;
                int myCellVSpan = cellVSpan;
                int myCellX = useTmpCoords ? tmpCellX : cellX;
                int myCellY = useTmpCoords ? tmpCellY : cellY;
                if (invertHorizontally) {
                    myCellX = colCount - myCellX - cellHSpan;
                }
                // 横向所占单位*单位长度-左margin-右margin
                width = (int) (myCellHSpan * cellWidth / cellScaleX - leftMargin - rightMargin);
                height = (int) (myCellVSpan * cellHeight / cellScaleY - topMargin - bottomMargin);
                x = myCellX * cellWidth + leftMargin;
                y = myCellY * cellHeight + topMargin;
            }
        }
    }
}
