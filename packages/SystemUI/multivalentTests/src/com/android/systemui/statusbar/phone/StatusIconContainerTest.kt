/*
 * Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.statusbar.phone

import android.view.View
import android.view.ViewGroup
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarIconView.STATE_HIDDEN
import com.android.systemui.statusbar.StatusBarIconView.STATE_ICON
import com.android.systemui.statusbar.pipeline.shared.ui.binder.ModernStatusBarViewBinding
import com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class StatusIconContainerTest : SysuiTestCase() {
    private lateinit var container: StatusIconContainer

    @Before
    fun setUp() {
        context.orCreateTestableResources.addOverride(R.dimen.status_bar_system_icon_spacing, 4)
        container = StatusIconContainer(context)
    }

    @Test
    fun paddedChildren_paddingIsCountedOnlyOnce() {
        addIcon("one")
        addIcon("two")
        measure(100)
        assertThat(container.measuredWidth).isEqualTo(44)
    }

    @Test
    fun narrowContainer_compressesGapsBeforeHidingIcons() {
        val first = addIcon("one")
        val second = addIcon("two")
        measure(41)
        container.layout(0, 0, container.measuredWidth, 24)
        assertThat(first.getVisibleState()).isEqualTo(STATE_ICON)
        assertThat(second.getVisibleState()).isEqualTo(STATE_ICON)
        assertThat(first.x + first.width).isAtMost(second.x)
        assertThat(second.x + second.width).isAtMost(41f)
    }

    @Test
    fun blockedIcon_doesNotReserveSpace() {
        addIcon("hidden").setIconBlocked(true)
        addIcon("visible")
        measure(100)
        assertThat(container.measuredWidth).isEqualTo(20)
    }

    @Test
    fun noSpace_hidesIconsAndOverflowDot() {
        val icon = addIcon("one")
        measure(0)
        container.layout(0, 0, 0, 24)
        assertThat(icon.getVisibleState()).isEqualTo(STATE_HIDDEN)
    }

    @Test
    fun narrowRtlContainer_doesNotOverlap() {
        container.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val first = addIcon("one")
        val second = addIcon("two")
        measure(41)
        container.layout(0, 0, container.measuredWidth, 24)
        assertThat(second.x + second.width).isAtMost(first.x)
        assertThat(second.x).isAtLeast(0f)
        assertThat(first.x + first.width).isAtMost(41f)
    }

    private fun addIcon(slot: String): ModernStatusBarView {
        val binding = mock<ModernStatusBarViewBinding>()
        whenever(binding.getShouldIconBeVisible()).thenReturn(true)
        return ModernStatusBarView(context, null).also {
            it.initView(slot) { binding }
            it.setPadding(3, 0, 3, 0)
            container.addView(it, ViewGroup.LayoutParams(20, 24))
        }
    }

    private fun measure(width: Int) {
        container.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(24, View.MeasureSpec.EXACTLY),
        )
    }
}
