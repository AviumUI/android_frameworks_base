/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.view.View
import android.view.ViewTreeObserver.OnPreDrawListener
import androidx.constraintlayout.helper.widget.Layer
import androidx.constraintlayout.widget.ConstraintLayout

class AodBurnInLayer(context: Context) : Layer(context) {
    // For setScale in Layer class, it stores it in mScaleX/Y and directly apply scale to
    // referenceViews instead of keeping the value in fields of View class
    // when we try to clone ConstraintSet, it will call getScaleX from View class and return 1.0
    // and when we clone and apply, it will reset everything in the layer
    // which cause the flicker from AOD to LS
    private var _scaleX = 1F
    private var _scaleY = 1F

    // As described for _scaleX and _scaleY, we have similar issue with translation
    private var _translationX = 0F
    private var _translationY = 0F

    // Layer caches referenced views only in updatePostLayout. ConstraintSet can
    // call transform setters after references change but before that cache is
    // rebuilt, otherwise indexing the old array with the new count can crash.
    private var referencesDirty = true
    private var groupRotation = 0f
    private var groupPivotX = Float.NaN
    private var groupPivotY = Float.NaN

    private fun applyTransforms() {
        if (referencesDirty) return
        super.setPivotX(groupPivotX)
        super.setPivotY(groupPivotY)
        super.setRotation(groupRotation)
        super.setScaleX(_scaleX)
        super.setScaleY(_scaleY)
        super.setTranslationX(_translationX)
        super.setTranslationY(_translationY)
    }

    private val _predrawListener = OnPreDrawListener {
        if (!referencesDirty) {
            super.setScaleX(_scaleX)
            super.setScaleY(_scaleY)
            super.setTranslationX(_translationX)
            super.setTranslationY(_translationY)
        }
        true
    }

    // avoid adding views with same ids
    override fun addView(view: View?) {
        view?.let {
            if (it.id !in referencedIds) {
                referencesDirty = true
                super.addView(view)
            }
        }
    }

    override fun removeView(view: View): Int {
        referencesDirty = true
        return super.removeView(view)
    }

    override fun setReferencedIds(ids: IntArray) {
        referencesDirty = true
        super.setReferencedIds(ids)
        requestLayout()
    }

    override fun updatePostLayout(container: ConstraintLayout) {
        // This is Layer's cache rebuild entry point. No transform setter may
        // access the cache between reference mutation and this call.
        super.updatePostLayout(container)
        referencesDirty = false
        applyTransforms()
    }

    override fun setRotation(rotation: Float) {
        groupRotation = rotation
        if (!referencesDirty) super.setRotation(rotation)
    }

    override fun setPivotX(pivotX: Float) {
        groupPivotX = pivotX
        if (!referencesDirty) super.setPivotX(pivotX)
    }

    override fun setPivotY(pivotY: Float) {
        groupPivotY = pivotY
        if (!referencesDirty) super.setPivotY(pivotY)
    }

    fun registerListener(rootView: View) {
        rootView.viewTreeObserver.addOnPreDrawListener(_predrawListener)
    }

    fun unregisterListener(rootView: View) {
        rootView.viewTreeObserver.removeOnPreDrawListener(_predrawListener)
    }

    override fun setScaleX(scaleX: Float) {
        _scaleX = scaleX
        if (!referencesDirty) super.setScaleX(scaleX)
    }

    override fun getScaleX(): Float {
        return _scaleX
    }

    override fun setScaleY(scaleY: Float) {
        _scaleY = scaleY
        if (!referencesDirty) super.setScaleY(scaleY)
    }

    override fun getScaleY(): Float {
        return _scaleY
    }

    override fun setTranslationX(dx: Float) {
        _translationX = dx
        if (!referencesDirty) super.setTranslationX(dx)
    }

    override fun getTranslationX(): Float {
        return _translationX
    }

    override fun setTranslationY(dy: Float) {
        _translationY = dy
        if (!referencesDirty) super.setTranslationY(dy)
    }

    override fun getTranslationY(): Float {
        return _translationY
    }
}
