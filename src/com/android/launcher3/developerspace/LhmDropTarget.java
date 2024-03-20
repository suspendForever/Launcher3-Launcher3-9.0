package com.android.launcher3.developerspace;

import android.graphics.Rect;

import com.android.launcher3.DropTarget;
import com.android.launcher3.dragndrop.DragOptions;

public interface LhmDropTarget {

    boolean isDropEnabled();

    /**
     * Handle an object being dropped on the DropTarget.
     *
     * This will be called only if this target previously returned true for {@link #acceptDrop}. It
     * is the responsibility of this target to exit out of the spring loaded mode (either
     * immediately or after any pending animations).
     *
     * If the drop was cancelled for some reason, onDrop will never get called, the UI will
     * automatically exit out of this mode.
     */
    void onDrop(DropTarget.DragObject dragObject, DragOptions options);

    void onDragEnter(DropTarget.DragObject dragObject);

    void onDragOver(DropTarget.DragObject dragObject);

    void onDragExit(DropTarget.DragObject dragObject);

    /**
     * Check if a drop action can occur at, or near, the requested location.
     * This will be called just before onDrop.
     * @return True if the drop will be accepted, false otherwise.
     */
    boolean acceptDrop(DropTarget.DragObject dragObject);

    void prepareAccessibilityDrop();

    // These methods are implemented in Views
    void getHitRectRelativeToDragLayer(Rect outRect);
}
