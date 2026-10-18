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
import dagger.multibindings.IntoSet
import org.avium.systemui.aod.AviumAodController
import org.avium.systemui.aod.AviumAodStyle
import org.avium.systemui.aod.CustomLockscreenAodStyle
import org.avium.systemui.aod.DefaultAodStyle

@Module
abstract class AviumAodModule {

    @Binds
    @IntoSet
    abstract fun bindDefaultAodStyle(impl: DefaultAodStyle): AviumAodStyle

    @Binds
    @IntoSet
    abstract fun bindCustomLockscreenAodStyle(impl: CustomLockscreenAodStyle): AviumAodStyle

    @Binds
    @IntoMap
    @ClassKey(AviumAodController::class)
    abstract fun bindAviumAodController(
        impl: AviumAodController,
    ): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(org.avium.systemui.aod.AviumAodStyleController::class)
    abstract fun bindAviumAodStyleController(
        impl: org.avium.systemui.aod.AviumAodStyleController,
    ): CoreStartable
}
