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
import android.util.Log;
import android.view.View;
import android.os.Process;
import javax.inject.Inject;
import com.android.systemui.dagger.SysUISingleton;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.util.SystemPropertiesWatcher;

@SysUISingleton
public class CustomLockscreenClockManager {

    private static final String TAG = "AVIUM_LOCKSCREEN";

    private final Context mContext;
    private ICustomLockScreenClock mCustomClock;
    private final NativeLockscreenViewHider mNativeViewHider;
    private final SystemPropertiesWatcher mPropertiesWatcher;
    private View mCustomClockView;

    @Inject
    public CustomLockscreenClockManager(Context context, NativeLockscreenViewHider nativeViewHider, SystemPropertiesWatcher propertiesWatcher) {
        this.mContext = context;
        this.mNativeViewHider = nativeViewHider;
        this.mPropertiesWatcher = propertiesWatcher;
    }

    public boolean isEnabled() {
        return CustomLockscreenSettings.isEnabled();
    }
    
    public void hideNativeClock(View view) {
        mNativeViewHider.hideNativeViews(view);
    }

    public View getView() {
        if (mCustomClockView == null && isEnabled()) {
            mCustomClock = CustomLockScreenClockFactory.create(mContext);
            if (mCustomClock != null) {
                mCustomClockView = mCustomClock.getView(mContext);
            }
        }
        return mCustomClockView;
    }

    public void onNotificationStateChanged(boolean hasNotifications) {
        if (mCustomClock != null && isEnabled()) {
            mCustomClock.onNotificationStateChanged(hasNotifications);
        }
    }

    public void onDestroy() {
        if (mCustomClock != null) {
            mCustomClock.onDestroy();
            mCustomClock = null;
            mCustomClockView = null; 
        }
        if (mPropertiesWatcher != null) {
            mPropertiesWatcher.destroy();
        }
    }

    public void restartSystemUI() {
        Process.killProcess(Process.myPid());
    }
}