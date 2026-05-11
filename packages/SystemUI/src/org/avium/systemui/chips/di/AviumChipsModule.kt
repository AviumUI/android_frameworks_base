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
package org.avium.systemui.chips.di

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import dagger.Module
import dagger.Provides
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import org.avium.systemui.chips.AviumChipController

@Module
abstract class AviumChipsModule {

    companion object {
        @Provides
        @SysUISingleton
        @IntoMap
        @ClassKey(AviumChipController::class)
        fun provideAviumChipControllerAsCoreStartable(
            controller: AviumChipController
        ): CoreStartable {
            return controller
        }
    }
}
