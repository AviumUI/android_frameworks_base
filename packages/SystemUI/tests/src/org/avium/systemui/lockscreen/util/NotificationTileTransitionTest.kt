/* Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.avium.systemui.lockscreen.util

import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.qs.QSExpansionPathInterpolator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
@RunWithLooper
class NotificationTileTransitionTest : SysuiTestCase() {
    private lateinit var source: View
    private lateinit var panel: FrameLayout
    private lateinit var card: View
    private lateinit var transition: NotificationTileTransition

    @Before
    fun setUp() {
        source = View(context).apply { layout(0, 0, 54, 54) }
        panel = FrameLayout(context).apply {
            layout(0, 0, 400, 600)
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        card = View(context)
        panel.addView(card)
        transition = NotificationTileTransition(QSExpansionPathInterpolator())
        transition.prepare(source, panel, 18f, 28f)
    }

    @After
    fun cleanUp() {
        transition.reset()
    }

    @Test
    fun endpoints_revealFullPanelWithoutScalingCards() {
        transition.setPosition(0f)
        assertEquals(Rect(0, 0, 54, 54), panel.clipBounds)
        assertEquals(0f, card.alpha, 0f)
        assertEquals(View.VISIBLE, source.visibility)
        transition.setPosition(1f)
        assertEquals(Rect(0, 0, 400, 600), panel.clipBounds)
        assertEquals(1f, card.alpha, 0f)
        assertEquals(1f, panel.scaleX, 0f)
        assertEquals(1f, panel.scaleY, 0f)
        assertEquals(View.VISIBLE, source.visibility)
    }

    @Test
    fun reverseDrag_returnsToSameIntermediateGeometry() {
        transition.beginInteraction()
        transition.setPosition(0.35f)
        val bounds = Rect(panel.clipBounds!!)
        val alpha = card.alpha
        transition.setPosition(0.8f)
        transition.setPosition(0.35f)
        assertEquals(bounds, panel.clipBounds)
        assertEquals(alpha, card.alpha, 0f)
        assertTrue(transition.isRunning)
    }

    @Test
    fun dragReachesEndpoint_keepsTouchTargetAvailableForReverseDrag() {
        transition.beginInteraction()
        transition.setPosition(1f)
        assertEquals(View.VISIBLE, source.visibility)
        assertTrue(transition.isRunning)
        transition.setPosition(0.25f)
        assertEquals(View.VISIBLE, source.visibility)
        assertTrue(source.alpha > 0f)
        assertTrue(panel.clipBounds!!.height() < panel.height)
    }

    @Test
    fun interruptedTransition_restoresSurfaceAndTile() {
        transition.beginInteraction()
        transition.setPosition(0.6f)
        transition.reset()
        assertFalse(transition.isRunning)
        assertNull(panel.clipBounds)
        assertSame(ViewOutlineProvider.BACKGROUND, panel.outlineProvider)
        assertEquals(0f, panel.translationX, 0f)
        assertEquals(0f, panel.translationY, 0f)
        assertEquals(1f, card.alpha, 0f)
        assertEquals(View.VISIBLE, source.visibility)
        assertEquals(1f, source.alpha, 0f)
        assertEquals(0f, source.translationZ, 0f)
    }
}
