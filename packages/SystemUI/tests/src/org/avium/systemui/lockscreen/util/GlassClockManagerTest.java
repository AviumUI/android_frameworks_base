/*
 * Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.avium.systemui.lockscreen.util;

import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper.RunWithLooper;
import androidx.test.filters.SmallTest;
import com.android.systemui.SysuiTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
public class GlassClockManagerTest extends SysuiTestCase {
    private GlassClockManager mManager;
    private GlassClockManager.DigitView mDigit;

    @Before
    public void setUp() {
        mManager = new GlassClockManager(mContext, 1, new int[0]);
        mDigit = (GlassClockManager.DigitView) mManager.getDigitViews()[0];
        mDigit.setDigitDrawable(new ColorDrawable(Color.WHITE));
        mDigit.layout(0, 0, 24, 24);
    }

    @After
    public void tearDown() {
        mManager.cleanup();
    }

    @Test
    public void noCaptureYet_drawsReadableDigit() {
        assertReadableDigit();
    }

    @Test
    public void dozingWithoutCapture_drawsReadableDigit() {
        mManager.onDozingChanged(true);
        assertReadableDigit();
    }

    @Test
    public void resizedBeforeCapture_rebuildsMask() {
        mDigit.layout(0, 0, 48, 48);
        assertReadableDigit();
    }

    private void assertReadableDigit() {
        Bitmap output = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        mDigit.draw(new Canvas(output));
        assertEquals(Color.WHITE, output.getPixel(12, 12));
        output.recycle();
    }
}
