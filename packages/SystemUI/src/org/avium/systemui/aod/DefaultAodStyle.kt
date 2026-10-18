/* Copyright (C) 2026 The AviumUI Project */
package org.avium.systemui.aod

import javax.inject.Inject

/** The stock AOSP keyguard/doze renderer remains the default visual style. */
class DefaultAodStyle @Inject constructor() : AviumAodStyle {
    override val id = "aosp"
    override val priority = Int.MIN_VALUE
}
