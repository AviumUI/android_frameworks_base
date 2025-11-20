package org.avium.systemui.depthwallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Point
import android.util.Log
import java.io.File
import java.io.FileInputStream

object DepthWallpaperSetup {
    private const val TAG = "DepthWallpaperSetup"
    private const val WALLPAPER_PATH = "/data/system/avium/wallpaper"
    
    private var lastAppliedMTime: Long = 0

    fun applyIfNeeded(context: Context) {
        if (!DepthWallpaperSwitch.isEnabled(context)) return

        val file = File(WALLPAPER_PATH)
        if (!file.exists()) {
            Log.w(TAG, "Wallpaper file not found at $WALLPAPER_PATH")
            return
        }
        if (file.lastModified() == lastAppliedMTime) {
            return
        }
        Thread {
            try {
                Log.d(TAG, "Start setting lockscreen wallpaper async...")
                
                val wm = WallpaperManager.getInstance(context)
                val display = context.display
                val size = Point()
                display?.getRealSize(size)
                val width = size.x
                val height = size.y
                wm.suggestDesiredDimensions(width, height)

                FileInputStream(file).use { stream ->
                    wm.setStream(stream, null, true, WallpaperManager.FLAG_LOCK)
                }
                lastAppliedMTime = file.lastModified()
                Log.d(TAG, "Async: Lockscreen wallpaper set successfully.")

            } catch (e: Exception) {
                Log.e(TAG, "Async: Failed to set wallpaper", e)
            }
        }.start()
    }
}