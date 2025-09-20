/*
 * Copyright (C) 2025-2026 The AviumUI Project
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

package org.avium.systemui.lockscreen.util;

import android.os.SystemProperties;
import android.util.Log;

public class CustomLockscreenSettings {

    private static final String TAG = "AVIUM_LOCKSCREEN";

    private static final String PROP_ENABLED = "persist.avium.customlockscreen.enable";
    private static final String PROP_TYPE = "persist.avium.customlockscreen.type";
    private static final String PROP_COLOR = "persist.avium.customlockscreen.color";
    private static final String PROP_HOUR_COLOR = "persist.avium.customlockscreen.hour.color";
    private static final String PROP_MINUTE_COLOR = "persist.avium.customlockscreen.minute.color";

    public static boolean isEnabled() {
        boolean enabled = SystemProperties.getBoolean(PROP_ENABLED, false);
        Log.d(TAG, "Custom lockscreen enabled: " + enabled);
        return enabled;
    }

    public static int getClockType() {
        int type = SystemProperties.getInt(PROP_TYPE, 0);
        Log.d(TAG, "Clock type: " + type);
        return type;
    }

    @Deprecated
    public static String getClockColor() {
        String color = SystemProperties.get(PROP_COLOR, "white");
        Log.d(TAG, "Clock color (deprecated): " + color);
        return color;
    }

    public static String getHourColor() {
        String hourColor = SystemProperties.get(PROP_HOUR_COLOR, "");
        if (hourColor.isEmpty()) {
            hourColor = SystemProperties.get(PROP_COLOR, "white");
        }
        Log.d(TAG, "Hour color: " + hourColor);
        return hourColor;
    }

    public static String getMinuteColor() {
        String minuteColor = SystemProperties.get(PROP_MINUTE_COLOR, "");
        if (minuteColor.isEmpty()) {
            minuteColor = SystemProperties.get(PROP_COLOR, "white");
        }
        Log.d(TAG, "Minute color: " + minuteColor);
        return minuteColor;
    }

    public static boolean hasSeparateHourMinuteColors() {
        String hourColor = SystemProperties.get(PROP_HOUR_COLOR, "");
        String minuteColor = SystemProperties.get(PROP_MINUTE_COLOR, "");
        boolean hasSeparate = !hourColor.isEmpty() || !minuteColor.isEmpty();
        Log.d(TAG, "Has separate hour/minute colors: " + hasSeparate);
        return hasSeparate;
    }
}