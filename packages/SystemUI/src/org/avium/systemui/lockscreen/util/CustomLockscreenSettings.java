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
import java.util.Locale;

public class CustomLockscreenSettings {

    private static final String PROP_ENABLED = "persist.avium.customlockscreen.enable";
    private static final String PROP_TYPE = "persist.avium.customlockscreen.type";
    private static final String PROP_COLOR = "persist.avium.customlockscreen.color";
    private static final String PROP_HOUR_COLOR = "persist.avium.customlockscreen.hour.color";
    private static final String PROP_MINUTE_COLOR = "persist.avium.customlockscreen.minute.color";
    private static final String PROP_WEEKDAY_COLOR = "persist.avium.customlockscreen.weekday.color";
    private static final String PROP_MONTH_COLOR = "persist.avium.customlockscreen.month.color";
    private static final String PROP_DAY_COLOR = "persist.avium.customlockscreen.day.color";
    private static final String PROP_DOT_COLOR = "persist.avium.customlockscreen.dot.color";
    private static final String PROP_SHOW_LUNAR = "persist.avium.customlockscreen.show_lunar";

    public static boolean isEnabled() {
        return SystemProperties.getBoolean(PROP_ENABLED, false);
    }

    public static int getClockType() {
        return SystemProperties.getInt(PROP_TYPE, 0);
    }

    @Deprecated
    public static String getClockColor() {
        return SystemProperties.get(PROP_COLOR, "white");
    }

    public static String getHourColor() {
        String hourColor = SystemProperties.get(PROP_HOUR_COLOR, "");
        if (hourColor.isEmpty()) {
            hourColor = SystemProperties.get(PROP_COLOR, "white");
        }
        return hourColor;
    }

    public static String getMinuteColor() {
        String minuteColor = SystemProperties.get(PROP_MINUTE_COLOR, "");
        if (minuteColor.isEmpty()) {
            minuteColor = SystemProperties.get(PROP_COLOR, "white");
        }
        return minuteColor;
    }

    public static String getWeekdayColor() {
        String weekdayColor = SystemProperties.get(PROP_WEEKDAY_COLOR, "");
        if (weekdayColor.isEmpty()) {
            weekdayColor = "white";
        }
        return weekdayColor;
    }

    public static String getMonthColor() {
        String monthColor = SystemProperties.get(PROP_MONTH_COLOR, "");
        if (monthColor.isEmpty()) {
            monthColor = "white";
        }
        return monthColor;
    }

    public static String getDayColor() {
        String dayColor = SystemProperties.get(PROP_DAY_COLOR, "");
        if (dayColor.isEmpty()) {
            dayColor = "white";
        }
        return dayColor;
    }

    public static String getDotColor() {
        String dotColor = SystemProperties.get(PROP_DOT_COLOR, "");
        if (dotColor.isEmpty()) {
            dotColor = "white";
        }
        return dotColor;
    }

    public static boolean hasSeparateHourMinuteColors() {
        String hourColor = SystemProperties.get(PROP_HOUR_COLOR, "");
        String minuteColor = SystemProperties.get(PROP_MINUTE_COLOR, "");
        return !hourColor.isEmpty() || !minuteColor.isEmpty();
    }

    public static boolean shouldShowLunar() {
        boolean showLunar = SystemProperties.getBoolean(PROP_SHOW_LUNAR, true);
        boolean isChinese = Locale.getDefault().getLanguage().equals(Locale.CHINESE.getLanguage());
        return showLunar && isChinese;
    }
}
