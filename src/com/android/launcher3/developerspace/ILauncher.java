package com.android.launcher3.developerspace;

import android.content.Context;
import android.view.View;

public interface ILauncher {
    Boolean finishAutoCancelActionMode();

    Context getContext();

    LhmDragLayer getDragLayer();

    LhmDragController getDragController();

    View getRootView();

    LhmDropTarget getDropTarget();
}
