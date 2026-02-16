/*
 * Copyright (C) 2024 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.internal.util.android;

import static android.provider.Settings.System.POP_UP_NOTIFICATION_BLACKLIST;
import static android.provider.Settings.System.POP_UP_NOTIFICATION_JUMP_LANDSCAPE;
import static android.provider.Settings.System.POP_UP_NOTIFICATION_JUMP_PORTRAIT;

import android.content.Context;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.R;

/** @hide */
public class PopUpSettingsHelper {

    private PopUpSettingsHelper() {}

    public static boolean isNotificationJumpEnabled(Context context, boolean landscape) {
        return isNotificationJumpEnabled(context, landscape, UserHandle.USER_CURRENT);
    }

    public static boolean isNotificationJumpEnabled(Context context, boolean landscape, int userId) {
        final boolean defaultEnabled = landscape ?
                context.getResources().getBoolean(
                        R.bool.config_popUpView_defaultNotificationLandJump) :
                context.getResources().getBoolean(
                        R.bool.config_popUpView_defaultNotificationPortJump);
        return Settings.System.getIntForUser(context.getContentResolver(), landscape ?
                POP_UP_NOTIFICATION_JUMP_LANDSCAPE : POP_UP_NOTIFICATION_JUMP_PORTRAIT,
                defaultEnabled ? 1 : 0, userId) == 1;
    }

    public static String getNotificationJumpBlacklist(Context context) {
        return getNotificationJumpBlacklist(context, UserHandle.USER_CURRENT);
    }

    public static String getNotificationJumpBlacklist(Context context, int userId) {
        return Settings.System.getStringForUser(context.getContentResolver(),
                POP_UP_NOTIFICATION_BLACKLIST, userId);
    }
}
