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

import android.util.Log;

import com.android.launcher3.Alarm;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.Workspace;

public class CustomSpringLoadedDragController implements OnAlarmListener {
    // how long the user must hover over a mini-screen before it unshrinks
    final long ENTER_SPRING_LOAD_HOVER_TIME = 20;
    final long ENTER_SPRING_LOAD_CANCEL_HOVER_TIME = 950;

    Alarm mAlarm;

    // the screen the user is currently hovering over, if any
    private Launcher mLauncher;

    private int mDirection = 0;

    public CustomSpringLoadedDragController(Launcher launcher) {
        mLauncher = launcher;
        mAlarm = new Alarm();
        mAlarm.setOnAlarmListener(this);
    }

    public void cancel() {
        mAlarm.cancelAlarm();
        mDirection = 0;
    }

    // Set a new alarm to expire for the screen that we are hovering over now
    public void setAlarm(int direction) {
        if(direction==mDirection)return;
        mAlarm.cancelAlarm();
        mAlarm.setAlarm((direction == 0) ? ENTER_SPRING_LOAD_CANCEL_HOVER_TIME :
                ENTER_SPRING_LOAD_HOVER_TIME);
        mDirection = direction;
    }

    // this is called when our timer runs out
    public void onAlarm(Alarm alarm) {
        if (mDirection != 0) {
            Log.d("test0514", "onAlarm: ");
            // Snap to the screen that we are hovering over now
            Hotseat hotseat = mLauncher.getHotseat();
            hotseat.smoothToDirectSmoothly(mDirection);
            mDirection=0;
        } else {
            mLauncher.getDragController().cancelDrag();
        }
    }
}
