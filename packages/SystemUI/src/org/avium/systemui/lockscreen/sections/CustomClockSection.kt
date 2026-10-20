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

package org.avium.systemui.lockscreen.sections

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.shade.LargeScreenHeaderHelper
import com.android.systemui.res.R
import com.android.systemui.customization.clocks.R as clocksR
import dagger.Lazy
import org.avium.systemui.lockscreen.CustomLockscreenClockManager
import javax.inject.Inject

private const val TAG = "AVIUM_LOCKSCREEN" 

@SysUISingleton
class CustomClockSection @Inject constructor(
    private val customLockscreenClockManager: CustomLockscreenClockManager,
    private val clockViewModel: KeyguardClockViewModel,
    private val clockInteractor: KeyguardClockInteractor,
    private val largeScreenHeaderHelper: Lazy<LargeScreenHeaderHelper>,
) : KeyguardSection() {

    private var customView: View? = null

    override fun addViews(parent: ConstraintLayout) {
        val view = customLockscreenClockManager.view ?: return
        customView = view

        if (view.id == View.NO_ID) {
            view.id = View.generateViewId()
        }
        
        val oldParent = view.parent
        if (oldParent is ViewGroup) {
            oldParent.removeView(view)
        }
        
        // Keep the clock behind native notification, media, status and unlock controls.
        parent.addView(view, 0, ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        // A ConstraintSet cannot turn an existing Barrier instance into a Guideline.
        removeAnchors(parent)
    }

    override fun removeViews(parent: ConstraintLayout) {
        customView?.let { parent.removeView(it) }
        customView = null
        removeAnchors(parent)
    }

    private fun removeAnchors(parent: ConstraintLayout) {
        listOf(R.id.smart_space_barrier_top, R.id.smart_space_barrier_bottom,
            R.id.keyguard_slice_view).forEach { id ->
            parent.findViewById<View>(id)?.let { parent.removeView(it) }
        }
    }

    override fun bindData(parent: ConstraintLayout) {
        Log.d(TAG, "do nothing")
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        val view = customView ?: return

        constraintSet.constrainWidth(view.id, ConstraintSet.MATCH_CONSTRAINT)
        constraintSet.constrainHeight(view.id, ConstraintSet.MATCH_CONSTRAINT)

        constraintSet.connect(view.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(view.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        // Native notifications, AOD icons and split-shade media consume these anchors even
        // when the native clock/date sections are absent. Reserve the native small-clock
        // area rather than leaving their constraints connected to missing views.
        val resources = view.resources
        val top = clockViewModel.getSmallClockTopMargin()
        val bottom = top + resources.getDimensionPixelSize(clocksR.dimen.small_clock_height) +
            resources.getDimensionPixelSize(clocksR.dimen.date_weather_view_height)
        for ((id, position) in listOf(
            R.id.smart_space_barrier_top to top,
            R.id.smart_space_barrier_bottom to bottom,
            R.id.keyguard_slice_view to bottom,
        )) {
            constraintSet.create(id, ConstraintSet.HORIZONTAL_GUIDELINE)
            constraintSet.setGuidelineBegin(id, position)
        }
        val notificationMargin =
            resources.getDimensionPixelSize(R.dimen.keyguard_status_view_bottom_margin) +
                if (resources.getBoolean(R.bool.config_use_large_screen_shade_header)) {
                    largeScreenHeaderHelper.get().getLargeScreenHeaderHeight()
                } else {
                    0
                }
        clockInteractor.setNotificationStackDefaultTop((bottom + notificationMargin).toFloat())
    }
}
