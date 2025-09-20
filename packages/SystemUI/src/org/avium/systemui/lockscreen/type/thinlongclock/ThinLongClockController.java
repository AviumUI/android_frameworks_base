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

package org.avium.systemui.lockscreen.type.thinlongclock;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.systemui.res.R;
import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.DigitalClockDisplayManager;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;

public class ThinLongClockController extends BaseLockscreenController {

    private static final int DIGIT_WIDTH_DP = 94;
    private static final int DIGIT_HEIGHT_DP = 340;

    private ImageView mHour1, mHour2, mMinute1, mMinute2;
    private ImageView[] mDigitViews;
    private DigitalClockDisplayManager mDigitalClockDisplayManager;
    private LockscreenLayoutManager mLayoutManager;

    private final int[] mDigitResources = new int[]{
        R.drawable.thin_baa_0, R.drawable.thin_baa_1, R.drawable.thin_baa_2,
        R.drawable.thin_baa_3, R.drawable.thin_baa_4, R.drawable.thin_baa_5,
        R.drawable.thin_baa_6, R.drawable.thin_baa_7, R.drawable.thin_baa_8,
        R.drawable.thin_baa_9
    };

    @Override
    public View getView(Context context) {
        mContext = context;
        createViews();
        mLayoutManager = new LockscreenLayoutManager(mContainer);
        mDigitalClockDisplayManager = new DigitalClockDisplayManager(mDigitViews, mDigitResources);
        setupLayout();
        initializeCommonViews();
        return mContainer;
    }

    private void createViews() {
        mContainer = new ConstraintLayout(mContext);
        mContainer.setId(View.generateViewId());

        mHour1 = new ImageView(mContext);
        mHour2 = new ImageView(mContext);
        mMinute1 = new ImageView(mContext);
        mMinute2 = new ImageView(mContext);

        mDigitViews = new ImageView[]{mHour1, mHour2, mMinute1, mMinute2};
        
        int digitWidthPx = dpToPx(DIGIT_WIDTH_DP);
        int digitHeightPx = dpToPx(DIGIT_HEIGHT_DP);

        for (ImageView iv : mDigitViews) {
            iv.setId(View.generateViewId());
            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(digitWidthPx, digitHeightPx);
            iv.setLayoutParams(params);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mContainer.addView(iv);
        }
    }

    private void setupLayout() {
        ConstraintSet cs = mLayoutManager.getConstraintSet();
        int[] clockViewIds = {mHour1.getId(), mHour2.getId(), mMinute1.getId(), mMinute2.getId()};
        
        cs.createHorizontalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
            clockViewIds, null, ConstraintSet.CHAIN_PACKED
        );

        for (int id : clockViewIds) {
            cs.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            cs.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            cs.setVerticalBias(id, 0.13f);
        }
        
        mLayoutManager.applyLayoutChanges();
    }

    @Override
    public void onTimeTick() {
        String timeString = LockscreenClockUtils.getCurrentTimeString("HHmm");
        mDigitalClockDisplayManager.updateTimeDisplay(timeString);
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        ImageView[] hourViews = {mHour1, mHour2};
        ImageView[] minuteViews = {mMinute1, mMinute2};
        mDigitalClockDisplayManager.applyColorAndEffects(hourViews, minuteViews);
    }

    @Override
    protected void cleanup() {
        mContext = null;
        mContainer = null;
        mHour1 = null;
        mHour2 = null;
        mMinute1 = null;
        mMinute2 = null;
        mDigitViews = null;
        mDigitalClockDisplayManager = null;
        mLayoutManager = null;
    }

    private int dpToPx(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }
}