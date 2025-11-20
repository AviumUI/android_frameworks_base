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
import android.graphics.BitmapFactory
import android.util.Log
import kotlin.math.max

object LockWallpaperCrop {

    private const val TAG = "LockWallpaperCrop"

    data class Params(
        val rawW: Int,
        val rawH: Int,
        val screenW: Int,
        val screenH: Int,
        val scale: Float,
        val dx: Float,
        val dy: Float
    )

    fun compute(context: Context): Params? {
        val wm = WallpaperManager.getInstance(context)
        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val pfd = try {
            wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)
                ?: wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
        } catch (e: Throwable) {
            Log.w(TAG, "getWallpaperFile failed: ${e.message}")
            null
        }

        if (pfd == null) {
            Log.w(TAG, "no wallpaper file from WallpaperManager")
            return null
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        try {
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
        } catch (e: Throwable) {
            Log.w(TAG, "decode bounds failed: ${e.message}")
            return null
        } finally {
            try {
                pfd.close()
            } catch (_: Throwable) {}
        }

        val rawW = opts.outWidth
        val rawH = opts.outHeight

        if (rawW <= 0 || rawH <= 0) {
            Log.w(TAG, "invalid wallpaper size: $rawW x $rawH")
            return null
        }

        val sx = screenW.toFloat() / rawW.toFloat()
        val sy = screenH.toFloat() / rawH.toFloat()
        val s = max(sx, sy)
        val dx = (screenW - rawW * s) * 0.5f
        val dy = (screenH - rawH * s) * 0.5f

        Log.d(
            TAG,
            "compute: raw=${rawW}x$rawH, screen=${screenW}x$screenH, " +
                "sx=$sx sy=$sy s=$s dx=$dx dy=$dy"
        )

        return Params(rawW, rawH, screenW, screenH, s, dx, dy)
    }
}
