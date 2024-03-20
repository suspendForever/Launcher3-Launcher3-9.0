package com.android.launcher3.developerspace;

import android.content.Context;

public interface ILauncher {
    Boolean finishAutoCancelActionMode();

    Context getContext();

    LhmDragLayer getDragLayer();

    LhmDragController getDragController();
}
