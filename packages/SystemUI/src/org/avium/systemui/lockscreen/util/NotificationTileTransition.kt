/* Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.avium.systemui.lockscreen.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.android.app.animation.Interpolators
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.systemui.qs.QSExpansionPathInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

/** A progress-driven expansion, using the same position paths as QSAnimator. */
internal class NotificationTileTransition(private val path: QSExpansionPathInterpolator) {
    private var animator: ValueAnimator? = null
    private var source: View? = null
    private var target: ViewGroup? = null
    private var targetOutline: ViewOutlineProvider? = null
    private var offsetX = 0f
    private var offsetY = 0f
    private var sourceTranslationX = 0f
    private var sourceTranslationY = 0f
    private var sourceTranslationZ = 0f
    private var sourceWidth = 0
    private var sourceHeight = 0
    private val bounds = Rect()
    private var radius = 0f
    private var startRadius = 0f
    private var endRadius = 0f
    var isRunning = false
        private set

    fun prepare(source: View, target: ViewGroup, startRadius: Float, endRadius: Float) {
        this.startRadius = startRadius
        this.endRadius = endRadius
        this.source = source
        this.target = target
        targetOutline = target.outlineProvider
        sourceTranslationX = source.translationX
        sourceTranslationY = source.translationY
        sourceTranslationZ = source.translationZ
        source.translationZ = target.z + source.resources.displayMetrics.density - source.elevation
        val from = IntArray(2)
        val to = IntArray(2)
        source.getLocationOnScreen(from)
        target.getLocationOnScreen(to)
        offsetX = (from[0] - to[0]).toFloat()
        offsetY = (from[1] - to[1]).toFloat()
        sourceWidth = source.width
        sourceHeight = source.height
        target.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(bounds, radius)
                }
            }
    }

    fun beginInteraction() {
        cancel()
        isRunning = true
    }

    fun setPosition(progress: Float) {
        val source = source ?: return
        val target = target ?: return
        val x = path.xInterpolator.getInterpolation(progress)
        val y = path.yInterpolator.getInterpolation(progress)
        // QSAnimator uses its Y path for tile size changes. Share that same curve
        // between both dimensions so the panel expands together, while translations
        // continue to follow the individual QS X/Y paths.
        val size = y
        bounds.set(
            0,
            0,
            (sourceWidth + (target.width - sourceWidth) * size).roundToInt(),
            (sourceHeight + (target.height - sourceHeight) * size).roundToInt(),
        )
        radius = startRadius + (endRadius - startRadius) * progress
        setCornerRadius(target.background, radius)
        target.clipBounds = bounds
        target.invalidateOutline()
        target.translationX = offsetX * (1f - x)
        target.translationY = offsetY * (1f - y)
        target.alpha = 1f
        val contentAlpha = ((progress - 0.2f) / 0.6f).coerceIn(0f, 1f)
        for (index in 0 until target.childCount) {
            target.getChildAt(index).alpha = contentAlpha
        }
        source.translationX = sourceTranslationX - offsetX * x
        source.translationY = sourceTranslationY - offsetY * y
        source.alpha = (1f - progress / 0.4f).coerceIn(0f, 1f)
        // Keep the touch target visible even at full progress until the pointer is released.
        // Making it INVISIBLE here would cancel a drag that reaches the endpoint.
        source.visibility = View.VISIBLE
    }

    fun animateTo(from: Float, to: Float, onProgress: (Float) -> Unit, onFinished: () -> Unit) {
        cancel()
        isRunning = true
        val settling =
            ValueAnimator.ofFloat(from, to).apply {
                // Match the QS panel size/clipping settle animation. Gesture updates
                // still feed setPosition directly, without temporal interpolation.
                interpolator = Interpolators.FAST_OUT_SLOW_IN
                duration = (350L * abs(to - from)).toLong().coerceAtLeast(80L)
                addUpdateListener { onProgress(it.animatedValue as Float) }
                addListener(
                    object : AnimatorListenerAdapter() {
                        private var cancelled = false

                        override fun onAnimationCancel(animation: Animator) {
                            cancelled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (cancelled) return
                            this@NotificationTileTransition.animator = null
                            this@NotificationTileTransition.isRunning = false
                            onFinished()
                        }
                    }
                )
            }
        animator = settling
        settling.start()
    }

    fun cancel() {
        animator?.cancel()
        animator = null
        isRunning = false
    }

    private fun setCornerRadius(drawable: Drawable?, radius: Float) {
        when (drawable) {
            is GradientDrawable -> drawable.cornerRadius = radius
            is BackgroundBlurDrawable -> drawable.setCornerRadius(radius)
            is LayerDrawable ->
                for (index in 0 until drawable.numberOfLayers) {
                    setCornerRadius(drawable.getDrawable(index), radius)
                }
        }
    }

    fun reset() {
        cancel()
        source?.apply {
            translationX = sourceTranslationX
            translationY = sourceTranslationY
            translationZ = sourceTranslationZ
            alpha = 1f
            visibility = View.VISIBLE
        }
        target?.apply {
            translationX = 0f
            translationY = 0f
            clipBounds = null
            outlineProvider = targetOutline
            setCornerRadius(background, endRadius)
            for (index in 0 until childCount) getChildAt(index).alpha = 1f
        }
        source = null
        target = null
        targetOutline = null
    }
}
