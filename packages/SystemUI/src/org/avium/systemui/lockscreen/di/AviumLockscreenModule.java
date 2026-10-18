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

package org.avium.systemui.lockscreen.di;

import android.content.Context;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.settings.UserTracker;

import dagger.Module;
import dagger.Provides;

import org.avium.systemui.lockscreen.CustomLockscreenClockManager;
import org.avium.systemui.lockscreen.CustomLockscreenRepository;
import org.avium.systemui.lockscreen.NativeLockscreenViewHider;

import java.util.concurrent.Executor;

@Module
public class AviumLockscreenModule {

    /** Provides native-clock visibility management. */
    @Provides
    @SysUISingleton
    public NativeLockscreenViewHider provideNativeLockscreenViewHider(Context context) {
        return new NativeLockscreenViewHider(context);
    }

    /** Provides the current custom clock and its lifecycle. */
    @Provides
    @SysUISingleton
    public CustomLockscreenClockManager provideCustomLockscreenClockManager(
            Context context,
            NativeLockscreenViewHider nativeLockscreenViewHider,
            StatusBarStateController statusBarStateController) {
        return new CustomLockscreenClockManager(
                context, nativeLockscreenViewHider, statusBarStateController);
    }

    /** Provides observation of per-user lockscreen settings. */
    @Provides
    @SysUISingleton
    public CustomLockscreenRepository provideCustomLockscreenRepository(
            Context context,
            UserTracker userTracker,
            @Main Executor mainExecutor) {
        return new CustomLockscreenRepository(context, userTracker, mainExecutor);
    }
}
