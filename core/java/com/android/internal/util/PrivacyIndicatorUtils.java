/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.provider.Settings;

/**
 * Shared policy for the SystemUI privacy indicators and their usage dialog.
 * @hide
 */
public final class PrivacyIndicatorUtils {
    public static final String SHOW_SYSTEM_APPS = "privacy_indicator_show_system_apps";

    private PrivacyIndicatorUtils() {}

    public static boolean showSystemApps(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(), SHOW_SYSTEM_APPS,
                0, UserHandle.USER_CURRENT) == 1;
    }

    public static boolean shouldShowApplication(Context context, String packageName, int uid) {
        if (showSystemApps(context)) {
            return true;
        }
        try {
            final ApplicationInfo info = context.getPackageManager().getApplicationInfoAsUser(
                    packageName, 0, UserHandle.getUserId(uid));
            return (info.flags & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            // Keep access visible if a package disappears while its app op is being processed.
            return true;
        }
    }
}
