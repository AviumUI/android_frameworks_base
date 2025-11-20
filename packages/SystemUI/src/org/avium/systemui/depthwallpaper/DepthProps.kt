package org.avium.systemui.depthwallpaper

import android.os.SystemProperties

object DepthProps {
    fun maskScale(): Float = SystemProperties.get("persist.avium.depthwallpaper.scale", "1.0").toFloatOrNull() ?: 1.0f
    fun maskTx(): Float = SystemProperties.getInt("persist.avium.depthwallpaper.tx", 0).toFloat()
    fun maskTy(): Float = SystemProperties.getInt("persist.avium.depthwallpaper.ty", 0).toFloat()
}
