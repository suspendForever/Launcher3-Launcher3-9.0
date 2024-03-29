package com.android.launcher3.developerspace;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.PointF;

public class LhmLauncherAppWidgetHostView extends AppWidgetHostView {

    private float mScaleToFit;
    private final PointF mTranslationForCentering=new PointF();

    public LhmLauncherAppWidgetHostView(Context context) {
        super(context);
    }

    public void setScaleToFit(float scale) {
        mScaleToFit = scale;
        setScaleX(scale);
        setScaleY(scale);
    }

    public float getScaleToFit() {
        return mScaleToFit;
    }

    public void setTranslationForCentering(float x, float y) {
        mTranslationForCentering.set(x, y);
        setTranslationX(x);
        setTranslationY(y);
    }

    public PointF getTranslationForCentering() {
        return mTranslationForCentering;
    }


}