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

package org.avium.systemui.lockscreen;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.plugins.statusbar.StatusBarStateController;

import org.avium.systemui.depthwallpaper.DepthWallpaperSetup;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;

import javax.inject.Inject;

@SysUISingleton
public class CustomLockscreenClockManager {

    private static final String TAG = "AVIUM_LOCKSCREEN";

    private final Context mContext;
    private ICustomLockScreenClock mCustomClock;
    private final NativeLockscreenViewHider mNativeViewHider;
    private final StatusBarStateController mStatusBarStateController;
    private View mCustomClockView;
    private boolean mHasNotifications;

    /** Creates the custom clock manager. */
    @Inject
    public CustomLockscreenClockManager(
            Context context,
            NativeLockscreenViewHider nativeViewHider,
            StatusBarStateController statusBarStateController) {
        this.mContext = context;
        this.mNativeViewHider = nativeViewHider;
        this.mStatusBarStateController = statusBarStateController;
    }

    /** Returns whether the current user enabled a custom lockscreen. */
    public boolean isEnabled() {
        return CustomLockscreenSettings.isEnabled();
    }

    /** Hides native clock content for a custom clock. */
    public void hideNativeClock(View view) {
        mNativeViewHider.hideNativeViews(view);
    }

    /** Returns the current clock, constructing it with the saved appearance if needed. */
    public View getView() {
        if (mCustomClockView == null && isEnabled()) {
            // Keep wallpaper synchronization independent of the selected clock implementation.
            DepthWallpaperSetup.INSTANCE.applyIfNeeded(mContext);
            mCustomClock = CustomLockScreenClockFactory.create(mContext, mStatusBarStateController);
            if (mCustomClock != null) {
                mCustomClockView = mCustomClock.getView(mContext);
                if (mCustomClockView != null) {
                    mCustomClock.onNotificationStateChanged(mHasNotifications);
                }
            }
        }
        return mCustomClockView;
    }

    /** Forwards notification visibility to the current clock. */
    public void onNotificationStateChanged(boolean hasNotifications) {
        if (mHasNotifications == hasNotifications) return;
        mHasNotifications = hasNotifications;
        if (mCustomClock != null && isEnabled()) {
            mCustomClock.onNotificationStateChanged(hasNotifications);
        }
    }

    /** Releases the current clock and its views. */
    public void onDestroy() {
        if (mCustomClockView != null) {
            mNativeViewHider.restoreNativeViews(mCustomClockView);
        }
        detachCurrentView();
        if (mCustomClock != null) {
            mCustomClock.onDestroy();
            mCustomClock = null;
            mCustomClockView = null;
        }
    }

    /** Disposes the current clock so the next blueprint build gets fresh properties and views. */
    public void reload() {
        if (mCustomClockView != null) {
            mNativeViewHider.restoreNativeViews(mCustomClockView);
        }
        detachCurrentView();
        if (mCustomClock != null) {
            mCustomClock.onDestroy();
        }
        mCustomClock = null;
        mCustomClockView = null;
        DepthWallpaperSetup.INSTANCE.applyIfNeeded(mContext);
    }

    private void detachCurrentView() {
        if (mCustomClockView == null) return;
        ViewGroup parent = (ViewGroup) mCustomClockView.getParent();
        if (parent != null) {
            parent.removeView(mCustomClockView);
        }
    }

    /**
     * Reloads clock views without restarting SystemUI.
     * @deprecated Use {@link #reload()} instead.
     */
    @Deprecated
    public void restartSystemUI() {
        reload();
    }
}
