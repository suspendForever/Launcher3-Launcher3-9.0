package com.android.launcher3.developerspace;

import android.view.View;

public interface LhmDragSource  {

    /**
     * A callback made back to the source after an item from this source has been dropped on a
     * DropTarget.
     */
    void onDropCompleted(View target, LhmDragObject d, boolean success);
}
