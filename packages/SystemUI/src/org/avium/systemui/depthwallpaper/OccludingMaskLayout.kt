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

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import java.io.File
import java.io.FileInputStream
import kotlin.math.max

import com.android.systemui.Dependency
import com.android.systemui.plugins.statusbar.StatusBarStateController

class OccludingMaskLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), StatusBarStateController.StateListener {

    companion object {
        private const val TAG = "OccludingMaskLayout"
        private const val WALLPAPER_PATH = "/data/system/avium/wallpaper"
        private const val MASK_PATH = "/data/system/avium/mask"
    }

    private var bgBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawMatrix = Matrix()
    
    private var lastBgMTime: Long = 0L
    private var lastMaskMTime: Long = 0L
    
    private val viewLoc = IntArray(2)
    private var screenW = 0
    private var screenH = 0

    private var mDozeAmount: Float = 0f
    private var mStatusBarStateController: StatusBarStateController? = null

    init {
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        updateScreenSize()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            mStatusBarStateController = Dependency.get(StatusBarStateController::class.java)
            mStatusBarStateController?.addCallback(this)
            mDozeAmount = mStatusBarStateController?.dozeAmount ?: 0f
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach StatusBarStateController", e)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mStatusBarStateController?.removeCallback(this)
        mStatusBarStateController = null
    }

    override fun onDozeAmountChanged(linear: Float, eased: Float) {
        mDozeAmount = eased
        invalidate()
    }

    override fun onStateChanged(newState: Int) {}
    override fun onDozingChanged(isDozing: Boolean) {}

    private fun updateScreenSize() {
        val display = context.display
        val p = Point()
        display?.getRealSize(p)
        screenW = p.x
        screenH = p.y
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
    }

    private fun loadBitmap(path: String, currentBmp: Bitmap?, lastMTime: Long): Pair<Bitmap?, Long> {
        val f = File(path)
        if (!f.exists()) {
            currentBmp?.recycle()
            return null to 0L
        }

        if (f.lastModified() == lastMTime && currentBmp != null && !currentBmp.isRecycled) {
            return currentBmp to lastMTime
        }

        return try {
            FileInputStream(f).use { fis ->
                val decoded = BitmapFactory.decodeStream(fis)
                if (decoded != null) {
                    val finalBmp = if (decoded.config == Bitmap.Config.ARGB_8888) {
                        decoded
                    } else {
                        val copy = decoded.copy(Bitmap.Config.ARGB_8888, true)
                        decoded.recycle()
                        copy
                    }
                    finalBmp to f.lastModified()
                } else {
                    currentBmp to lastMTime
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $path", e)
            currentBmp to lastMTime
        }
    }

    private fun reloadImagesIfNeeded() {
        val (newBg, bgTime) = loadBitmap(WALLPAPER_PATH, bgBitmap, lastBgMTime)
        if (newBg != bgBitmap) {
            bgBitmap?.recycle()
            bgBitmap = newBg
            lastBgMTime = bgTime
        }

        val (newMask, maskTime) = loadBitmap(MASK_PATH, maskBitmap, lastMaskMTime)
        if (newMask != maskBitmap) {
            maskBitmap?.recycle()
            maskBitmap = newMask
            lastMaskMTime = maskTime
        }
        
        if (bgBitmap != null || maskBitmap != null) {
            updateMatrix()
        }
    }

    private fun updateMatrix() {
        val bmp = bgBitmap ?: maskBitmap ?: return
        if (screenW == 0 || screenH == 0) updateScreenSize()

        val bmpW = bmp.width
        val bmpH = bmp.height

        val scale = max(screenW.toFloat() / bmpW, screenH.toFloat() / bmpH)
        val dx = (screenW - bmpW * scale) * 0.5f
        val dy = (screenH - bmpH * scale) * 0.5f

        getLocationOnScreen(viewLoc)
        val vx = viewLoc[0].toFloat()
        val vy = viewLoc[1].toFloat()

        drawMatrix.reset()
        drawMatrix.postScale(scale, scale)
        drawMatrix.postTranslate(dx, dy)
        drawMatrix.postTranslate(-vx, -vy)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!DepthWallpaperSwitch.isEnabled(context) || mDozeAmount == 1f) {
            super.dispatchDraw(canvas)
            return
        }

        reloadImagesIfNeeded()
        val alpha = ((1f - mDozeAmount) * 255).toInt().coerceIn(0, 255)
        
        if (alpha == 0) {
            super.dispatchDraw(canvas)
            return
        }

        paint.alpha = alpha
        val bg = bgBitmap
        if (bg != null && !bg.isRecycled) {
            paint.xfermode = null
            canvas.drawBitmap(bg, drawMatrix, paint)
        }

        super.dispatchDraw(canvas)

        val mask = maskBitmap
        if (mask != null && !mask.isRecycled) {
            paint.xfermode = null
            canvas.drawBitmap(mask, drawMatrix, paint)
        }
    }
}