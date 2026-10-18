/* Copyright (C) 2026 The AviumUI Project */
package org.avium.systemui.aod

import org.avium.systemui.lockscreen.CustomLockscreenClockManager
import javax.inject.Inject

class CustomLockscreenAodStyle @Inject constructor(
    private val clockManager: CustomLockscreenClockManager,
) : AviumAodStyle {
    override val id = "custom_lockscreen"
    override val priority = 100
    override fun isAvailable(): Boolean = clockManager.isEnabled()
}
