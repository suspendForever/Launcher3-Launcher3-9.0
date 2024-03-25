package com.android.launcher3.developerspace;

import android.app.Activity;
import android.content.Context;
import android.view.View;

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

    @Override
    public LhmDragController getDragController() {
        return null;
    }

    @Override
    public View getRootView() {
        return null;
    }

    @Override
    public LhmDropTarget getDropTarget() {
        return null;
    }


}
