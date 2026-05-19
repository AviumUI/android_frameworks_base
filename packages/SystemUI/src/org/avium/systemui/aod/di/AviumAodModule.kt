/*
 * Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.avium.systemui.aod.di

import com.android.systemui.CoreStartable
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import org.avium.systemui.aod.AviumAodController

@Module
abstract class AviumAodModule {

    @Binds
    @IntoMap
    @ClassKey(AviumAodController::class)
    abstract fun bindAviumAodController(
        impl: AviumAodController,
    ): CoreStartable
}
