package com.android.launcher3.developerspace;

import android.graphics.Rect;


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
    void onDrop(LhmDragObject dragObject);

    void onDragEnter(LhmDragObject dragObject);

    void onDragOver(LhmDragObject dragObject);

    void onDragExit(LhmDragObject dragObject);

    /**
     * Check if a drop action can occur at, or near, the requested location.
     * This will be called just before onDrop.
     * @return True if the drop will be accepted, false otherwise.
     */
    boolean acceptDrop(LhmDragObject dragObject);

    void prepareAccessibilityDrop();

    // These methods are implemented in Views
    void getHitRectRelativeToDragLayer(Rect outRect);
}
