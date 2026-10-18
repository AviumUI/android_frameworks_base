/* Copyright (C) 2026 The AviumUI Project */
package org.avium.systemui.aod

import java.io.PrintWriter

/** A visual provider for the AOD surface. Doze remains the lifecycle owner. */
interface AviumAodStyle {
    val id: String
    val priority: Int get() = 0
    fun isAvailable(): Boolean = true
    fun onAodStarted() {}
    fun onAodStopped() {}
    fun onTimeTick() {}
    fun dump(pw: PrintWriter) {}
}
