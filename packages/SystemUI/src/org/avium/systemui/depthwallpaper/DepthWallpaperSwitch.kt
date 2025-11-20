package org.avium.systemui.depthwallpaper

import android.content.Context
import android.os.SystemProperties

object DepthWallpaperSwitch {
    private const val PROP = "persist.avium.depthwallpaper"
    fun isEnabled(context: Context): Boolean {
        //return true
        return SystemProperties.getBoolean(PROP, false)
    }
}
