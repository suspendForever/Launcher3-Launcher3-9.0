/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import com.android.launcher3.Alarm;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.OnAlarmListener;

public class CustomSpringDragController implements OnAlarmListener {
    // how long the user must hover over a mini-screen before it unshrinks
    private static final long ENTER_SPRING_LOAD_HOVER_TIME = 0;
    final long ENTER_SPRING_LOAD_CANCEL_HOVER_TIME = 950;

    Alarm mAlarm;

    // the screen the user is currently hovering over, if any
    private CellLayout mScreen;
    private Launcher mLauncher;

    public CustomSpringDragController(Launcher launcher) {
        mLauncher = launcher;
        mAlarm = new Alarm();
        mAlarm.setOnAlarmListener(this);
    }

    public void cancel() {
        mAlarm.cancelAlarm();
    }

    // Set a new alarm to expire for the screen that we are hovering over now
    public void setAlarm(CellLayout cl) {
        mAlarm.cancelAlarm();
        mAlarm.setAlarm(ENTER_SPRING_LOAD_HOVER_TIME);
        mScreen = cl;
    }

    // this is called when our timer runs out
    public void onAlarm(Alarm alarm) {
        if (mScreen != null) {
            // Snap to the screen that we are hovering over now
//            Workspace w = mLauncher.getWorkspace();
//            int page = w.indexOfChild(mScreen);
//            if (page != w.getCurrentPage()) {
//                w.snapToPage(page);
//            }
        } else {
            mLauncher.getDragController().cancelDrag();
        }
    }
}
