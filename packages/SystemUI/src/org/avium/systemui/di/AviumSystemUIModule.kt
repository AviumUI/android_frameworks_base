/*
 *
 * Copyright (C) 2026 The AviumUI Project
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.avium.systemui.di

import android.content.Context
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager
import dagger.Lazy
import dagger.Module
import dagger.Provides
import org.avium.systemui.chips.di.AviumChipsModule
import org.avium.systemui.keyguard.AviumMusicLockscreenController
import org.avium.systemui.lockscreen.di.AviumLockscreenModule
import org.avium.systemui.mistouch.di.MistouchModule

@Module(includes = [
    AviumChipsModule::class,
    AviumLockscreenModule::class,
    MistouchModule::class
])
abstract class AviumSystemUIModule {

    companion object {
        @Provides
        @SysUISingleton
        fun provideAviumMusicLockscreenController(
            context: Context,
            statusBarKeyguardViewManagerProvider: Lazy<StatusBarKeyguardViewManager>
        ): AviumMusicLockscreenController {
            return AviumMusicLockscreenController(context, statusBarKeyguardViewManagerProvider)
        }
    }
}
