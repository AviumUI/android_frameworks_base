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

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout

object DepthWallpaperAttacher {
    fun wrapIfNeeded(target: View): View {
        if (!DepthWallpaperSwitch.isEnabled(target.context)) return target
        if (target is OccludingMaskLayout) return target

        val parent = target.parent as? ViewGroup ?: run {
            val wrapper = OccludingMaskLayout(target.context)
            wrapper.id = target.id
            wrapper.layoutParams = copyAsIs(target.layoutParams)
            target.id = View.generateViewId()
            target.layoutParams = childLpForWrapper(target.layoutParams)
            wrapper.addView(target)
            return wrapper
        }

        val index = parent.indexOfChild(target)
        val oldId = target.id
        val oldLp = target.layoutParams

        parent.removeViewAt(index)

        val wrapper = OccludingMaskLayout(target.context)
        wrapper.id = if (oldId != View.NO_ID) oldId else View.generateViewId()
        wrapper.layoutParams = copyAsIs(oldLp)

        target.id = View.generateViewId()
        target.layoutParams = childLpForWrapper(oldLp)

        wrapper.addView(target)
        parent.addView(wrapper, index, wrapper.layoutParams)
        return wrapper
    }

    private fun copyAsIs(lp: ViewGroup.LayoutParams?): ViewGroup.LayoutParams {
        if (lp == null) return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return when (lp) {
            is ConstraintLayout.LayoutParams -> ConstraintLayout.LayoutParams(lp)
            is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(lp)
            else -> ViewGroup.LayoutParams(lp)
        }
    }

    private fun childLpForWrapper(parentLp: ViewGroup.LayoutParams?): FrameLayout.LayoutParams {
        var w = parentLp?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT
        var h = parentLp?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT

        if (parentLp is ConstraintLayout.LayoutParams) {
            if (w == 0) w = ViewGroup.LayoutParams.MATCH_PARENT
            if (h == 0) h = ViewGroup.LayoutParams.MATCH_PARENT
        }

        if (w <= 0) w = ViewGroup.LayoutParams.MATCH_PARENT
        if (h <= 0) h = ViewGroup.LayoutParams.MATCH_PARENT

        return FrameLayout.LayoutParams(w, h)
    }
}
