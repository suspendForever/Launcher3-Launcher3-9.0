package com.android.launcher3.developerspace;

import android.app.Activity;
import android.content.Context;

public class MainLauncher extends Activity implements ILauncher {

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
}
