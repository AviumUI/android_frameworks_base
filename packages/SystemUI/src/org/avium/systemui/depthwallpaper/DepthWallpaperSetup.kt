/*
 * Copyright (C) 2025-2026 The AviumUI Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.avium.systemui.depthwallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

object DepthWallpaperSetup {
    private const val TAG = "DepthWallpaperSetup"
    private const val WALLPAPER_PATH = "/data/system/avium/wallpaper"
    
    @Volatile private var lastAppliedMTime: Long = 0
    @Volatile private var applying = false
    @Volatile private var pendingMTime: Long = 0
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DepthWallpaperSetup").apply { isDaemon = true }
    }

    fun applyIfNeeded(context: Context) {
        if (!DepthWallpaperSwitch.isEnabled(context)) return

        val file = File(WALLPAPER_PATH)
        if (!file.exists()) {
            Log.w(TAG, "Wallpaper file not found at $WALLPAPER_PATH")
            return
        }
        val mtime = file.lastModified()
        if (mtime == 0L || mtime == lastAppliedMTime) {
            return
        }
        synchronized(this) {
            if (applying) {
                pendingMTime = maxOf(pendingMTime, mtime)
                return
            }
            if (mtime == lastAppliedMTime) return
            applying = true
        }
        executor.execute {
            try {
                Log.d(TAG, "Start setting lockscreen wallpaper async...")
                
                val wm = WallpaperManager.getInstance(context)
                val display = context.display
                    ?: context.getSystemService(DisplayManager::class.java)
                        ?.getDisplay(Display.DEFAULT_DISPLAY)
                val size = Point()
                if (display == null) {
                    Log.w(TAG, "No display available; delaying wallpaper setup")
                    return@execute
                }
                display.getRealSize(size)
                val width = size.x
                val height = size.y
                if (width <= 0 || height <= 0) {
                    Log.w(TAG, "Invalid display size ${width}x${height}")
                    return@execute
                }
                wm.suggestDesiredDimensions(width, height)

                FileInputStream(file).use { stream ->
                    wm.setStream(stream, null, true, WallpaperManager.FLAG_LOCK)
                }
                lastAppliedMTime = file.lastModified().takeIf { it >= mtime } ?: mtime
                Log.d(TAG, "Async: Lockscreen wallpaper set successfully.")

            } catch (e: Exception) {
                Log.e(TAG, "Async: Failed to set wallpaper", e)
            } finally {
                val retry = synchronized(this) {
                    applying = false
                    val requested = pendingMTime
                    pendingMTime = 0
                    requested
                }
                if (retry > lastAppliedMTime) {
                    applyIfNeeded(context)
                }
            }
        }
    }
}
