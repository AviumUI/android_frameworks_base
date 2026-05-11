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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Log;
import javax.inject.Inject;
import com.android.systemui.dagger.SysUISingleton;

@SysUISingleton
public class SystemPropertiesWatcher {

    private static final String TAG = "AVIUM_LOCKSCREEN";
    private static final String ACTION_SETTINGS_CHANGED = "org.avium.systemui.lockscreen.SETTINGS_CHANGED";
    
    private static final String[] WATCHED_PROPERTIES = {
        "persist.avium.customlockscreen.enable",
        "persist.avium.customlockscreen.type", 
        "persist.avium.customlockscreen.color",
        "persist.avium.customlockscreen.hour.color",
        "persist.avium.customlockscreen.minute.color"
    };

    private final Context mContext;
    private final BroadcastReceiver mSettingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SETTINGS_CHANGED.equals(intent.getAction())) {
                Log.d(TAG, "Received settings changed broadcast, restarting SystemUI");
                Process.killProcess(Process.myPid());
            }
        }
    };

    @Inject
    public SystemPropertiesWatcher(Context context) {
        mContext = context;
        registerReceiver();
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_SETTINGS_CHANGED);
        mContext.registerReceiver(mSettingsReceiver, filter, Context.RECEIVER_EXPORTED);
        Log.d(TAG, "SystemPropertiesWatcher registered for action: " + ACTION_SETTINGS_CHANGED);
    }

    public void destroy() {
        try {
            mContext.unregisterReceiver(mSettingsReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered", e);
        }
    }
}