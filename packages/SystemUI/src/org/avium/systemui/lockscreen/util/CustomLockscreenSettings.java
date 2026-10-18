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

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** Per-user lockscreen configuration, committed as a single Settings Provider value. */
public final class CustomLockscreenSettings {
    public static final String CONFIG = "avium_custom_lockscreen_config";
    private static ContentResolver sResolver;
    private static int sUserId;
    private static String sRawConfig;
    private static JSONObject sConfig = new JSONObject();

    private CustomLockscreenSettings() {}

    /** Initialize before constructing SystemUI's dependency graph. */
    public static synchronized void initialize(Context context) {
        sResolver = context.getContentResolver();
        setUserId(ActivityManager.getCurrentUser());
    }

    /** Switch users without reusing the previous user's cached appearance. */
    public static synchronized void setUserId(int userId) {
        sUserId = userId;
        sRawConfig = null;
        sConfig = new JSONObject();
        // Legacy properties were device-wide. Only migrate them into the owner once;
        // other users start with their own defaults. Never write a system property.
        if (sResolver != null
                && userId == UserHandle.USER_SYSTEM
                && Settings.Secure.getStringForUser(sResolver, CONFIG, userId) == null) {
            JSONObject legacy = new JSONObject();
            try {
                legacy.put(
                        "enabled",
                        SystemProperties.getBoolean(
                                "persist.avium.customlockscreen.enable", false));
                legacy.put(
                        "type", SystemProperties.getInt("persist.avium.customlockscreen.type", 1));
                for (String name :
                        new String[] {
                            "color",
                            "hour.color",
                            "minute.color",
                            "weekday.color",
                            "month.color",
                            "day.color",
                            "dot.color"
                        }) {
                    legacy.put(
                            name.replace('.', '_'),
                            SystemProperties.get("persist.avium.customlockscreen." + name, ""));
                }
                legacy.put(
                        "show_lunar",
                        SystemProperties.getBoolean(
                                "persist.avium.customlockscreen.show_lunar", true));
                Settings.Secure.putStringForUser(sResolver, CONFIG, legacy.toString(), userId);
            } catch (JSONException e) {
                Log.e("AVIUM_LOCKSCREEN", "Unable to migrate lockscreen configuration", e);
            }
        }
    }

    private static synchronized JSONObject config() {
        if (sResolver == null) {
            throw new IllegalStateException("CustomLockscreenSettings has not been initialized");
        }
        String raw = Settings.Secure.getStringForUser(sResolver, CONFIG, sUserId);
        if (!java.util.Objects.equals(raw, sRawConfig)) {
            try {
                sConfig = raw == null ? new JSONObject() : new JSONObject(raw);
            } catch (JSONException e) {
                Log.e("AVIUM_LOCKSCREEN", "Invalid lockscreen configuration", e);
                sConfig = new JSONObject();
            }
            sRawConfig = raw;
        }
        return sConfig;
    }

    public static String getThemeZipPath() {
        return config().optString("theme_zip_path");
    }

    public static String getThemeName() {
        return config().optString("theme_name", "custom");
    }

    public static boolean isEnabled() {
        return config().optBoolean("enabled", false);
    }

    public static int getClockType() {
        return config().optInt("type", 1);
    }

    public static String getClockColor() {
        return color("color", "white");
    }

    private static String color(String key, String fallback) {
        String value = config().optString(key, "");
        return value.isEmpty() ? fallback : value;
    }

    public static String getHourColor() {
        return color("hour_color", getClockColor());
    }

    public static String getMinuteColor() {
        return color("minute_color", getClockColor());
    }

    public static String getWeekdayColor() {
        return color("weekday_color", getDayColor());
    }

    public static String getMonthColor() {
        return color("month_color", getDayColor());
    }

    public static String getDayColor() {
        return color("day_color", "white");
    }

    public static String getDotColor() {
        return color("dot_color", "white");
    }

    /** Returns whether individual time colors were saved. */
    public static boolean hasSeparateHourMinuteColors() {
        return !config().optString("hour_color").isEmpty()
                || !config().optString("minute_color").isEmpty();
    }

    /** Returns whether lunar dates should be shown in the current locale. */
    public static boolean shouldShowLunar() {
        return config().optBoolean("show_lunar", true)
                && Locale.getDefault().getLanguage().equals(Locale.CHINESE.getLanguage());
    }
}
