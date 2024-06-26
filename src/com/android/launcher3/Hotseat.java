/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import static com.android.launcher3.LauncherState.ALL_APPS;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.logging.UserEventDispatcher.LogContainerProvider;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.views.OptionsPopupView;

public class Hotseat extends ViewGroup implements LogContainerProvider, Insettable {

    private final Launcher mLauncher;
    private CellLayout mContent;
    private int mLastX;

    private boolean isScrolling = false;

    public static final int MULTIPE = 2;

    public static final int HOTSEAT_ROWS=6;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);
        setOnTouchListener(new HotSeatTouchListener(this, mLauncher));
    }

    public CellLayout getLayout() {
        return mContent;
    }

    /* Get the orientation invariant order of the item in the hotseat for persistence. */
    int getOrderInHotseat(int x, int y) {
        return mHasVerticalHotseat ? (mContent.getCountY() - y - 1) : x;
    }

    /* Get the orientation specific coordinates given an invariant order in the hotseat. */
    int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (mContent.getCountY() - (rank + 1)) : 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.layout);
    }

    void resetLayout(boolean hasVerticalHotseat) {
        mContent.removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        InvariantDeviceProfile idp = mLauncher.getDeviceProfile().inv;
        if (hasVerticalHotseat) {
            mContent.setGridSize(1, idp.numHotseatIcons);
        } else {
            mContent.setGridSize(idp.numHotseatIcons , HOTSEAT_ROWS);
        }

        if (!FeatureFlags.NO_ALL_APPS_ICON) {
            // Add the Apps button
            Context context = getContext();
            DeviceProfile grid = mLauncher.getDeviceProfile();
            int allAppsButtonRank = grid.inv.getAllAppsButtonRank();

            LayoutInflater inflater = LayoutInflater.from(context);
            TextView allAppsButton = (TextView)
                    inflater.inflate(R.layout.all_apps_button, mContent, false);
            Drawable d = context.getResources().getDrawable(R.drawable.all_apps_button_icon);
            d.setBounds(0, 0, grid.iconSizePx, grid.iconSizePx);

            int scaleDownPx = getResources().getDimensionPixelSize(R.dimen.all_apps_button_scale_down);
            Rect bounds = d.getBounds();
            d.setBounds(bounds.left, bounds.top + scaleDownPx / 2, bounds.right - scaleDownPx,
                    bounds.bottom - scaleDownPx / 2);
            allAppsButton.setCompoundDrawables(null, d, null, null);

            allAppsButton.setContentDescription(context.getString(R.string.all_apps_button_label));
            if (mLauncher != null) {
                allAppsButton.setOnClickListener((v) -> {
                    if (!mLauncher.isInState(ALL_APPS)) {
                        mLauncher.getUserEventDispatcher().logActionOnControl(Action.Touch.TAP,
                                ControlType.ALL_APPS_BUTTON);
                        mLauncher.getStateManager().goToState(ALL_APPS);
                    }
                });
                allAppsButton.setOnFocusChangeListener(mLauncher.mFocusHandler);
            }

            // Note: We do this to ensure that the hotseat is always laid out in the orientation of
            // the hotseat in order regardless of which orientation they were added
            int x = getCellXFromOrder(allAppsButtonRank);
            int y = getCellYFromOrder(allAppsButtonRank);
            CellLayout.LayoutParams lp = new CellLayout.LayoutParams(x, y, 1, 1);
            lp.canReorder = false;
            mContent.addViewToCellLayout(allAppsButton, -1, allAppsButton.getId(), lp, true);
        }
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        Log.d("test0521", "dispatchTouchEvent: " + ev.getAction());
        return super.dispatchTouchEvent(ev);
    }

    float startX = 0;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We don't want any clicks to go through to the hotseat unless the workspace is in
        // the normal state or an accessible drag is in progress.
//        return !mLauncher.getWorkspace().workspaceIconsCanBeDragged() &&
//                !mLauncher.getAccessibilityDelegate().isInAccessibleDrag();
        Log.d("test0521", "onInterceptTouchEvent: " + ev.getAction());

        //add by lhm
        int action = ev.getAction();
        float nowX = ev.getX();

        if (action == MotionEvent.ACTION_DOWN) {
            startX = ev.getX();
            return super.onInterceptTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_MOVE) {
            float scrollX = nowX - startX;
            if (Math.abs(scrollX) > ViewConfiguration.get(this.getContext()).getScaledTouchSlop()) {
                return true;
            }
            return super.onInterceptTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_UP) {
            startX = 0;
        }

        return super.onInterceptTouchEvent(ev);
    }

    public void smoothToDirectSmoothly(int direction) {
        if (direction > 0) {
            scrollBy(10, 0);
        }
        if (direction < 0 && getScrollX() > 0) {
            scrollBy(-10, 0);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        Log.d("test0521", "onTouchEvent: " + ev.getAction());
        int x = (int) ev.getX();
        Log.d("test0510", "onTouchEvent: x:" + x);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = x;
                break;
            case MotionEvent.ACTION_MOVE:
                int dx = mLastX - x;
                int oldScrollX = getScrollX();//原来的偏移量
                int preScrollX = oldScrollX + dx;//本次滑动后形成的偏移量
                if (preScrollX > getWidth()) {
                    preScrollX = getWidth();
                }
                if (preScrollX < 0) {
                    preScrollX = 0;
                }
                scrollTo(preScrollX, getScrollY());
                mLastX = x;
                isScrolling = true;
                return true;
            case MotionEvent.ACTION_UP:
                mLastX = 0;
        }
        isScrolling = false;
        return false;

    }

    public boolean isScrolling() {
        return isScrolling;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int spec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) * MULTIPE, MeasureSpec.getMode(widthMeasureSpec));
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            // 为ScrollerLayout中的每一个子控件测量大小
            measureChild(childView, spec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();
        int left = 0;
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            // 为ScrollerLayout中的每一个子控件在水平方向上进行布局
            childView.layout(left, 0, left + childView.getMeasuredWidth(), childView.getMeasuredHeight());
            left += childView.getMeasuredWidth();
        }
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        target.gridX = info.cellX;
        target.gridY = info.cellY;
        targetParent.containerType = ContainerType.HOTSEAT;
    }

    //add by lhm
    //此处确定hotseat的布局
    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mLauncher.getDeviceProfile();

        if (grid.isVerticalBarLayout()) {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left + grid.hotseatBarSidePaddingPx;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right + grid.hotseatBarSidePaddingPx;
            }
        } else {
            lp.gravity = Gravity.CENTER;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            //add by lhm
//            lp.height = (grid.hotseatBarSizePx + insets.bottom) * MULTIPE+50;
            lp.height = LayoutParams.MATCH_PARENT;
        }
//        Rect padding = grid.getHotseatLayoutPadding();
//        getLayout().setPadding(padding.left, padding.top, padding.right, padding.bottom);

        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }
}
