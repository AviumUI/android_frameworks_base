/* Copyright (C) 2026 The AviumUI Project */
package org.avium.systemui.aod

import java.io.PrintWriter
import javax.inject.Inject
import com.android.systemui.dagger.SysUISingleton

@SysUISingleton
class AviumAodStyleRegistry @Inject constructor(
    styles: Set<@JvmSuppressWildcards AviumAodStyle>,
) {
    private val styles = styles.sortedWith(compareByDescending<AviumAodStyle> { it.priority }.thenBy { it.id })
    fun activeStyle(): AviumAodStyle = styles.firstOrNull { it.isAvailable() } ?: EmptyAodStyle
    fun dump(pw: PrintWriter) = styles.forEach { pw.println("  ${it.id}: available=${it.isAvailable()}") }
    private object EmptyAodStyle : AviumAodStyle { override val id = "empty" }
}
