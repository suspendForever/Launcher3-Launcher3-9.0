package com.android.launcher3.developerspace;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.Utilities;


public class LhmDragLayer extends LhmBaseDragLayer<MainLauncher> {
    public LhmDragLayer(@NonNull Context context) {
        super(context);
    }

    public LhmDragLayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LhmDragLayer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void mapCoordInSelfToDescendant(View descendant, int[] coord) {
        Utilities.mapCoordInSelfToDescendant(descendant, this, coord);
    }

}
