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

package org.avium.systemui.lockscreen.type.runrunclock;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.systemui.res.R;
import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.DigitalClockDisplayManager;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;

import java.util.Locale;

public class RunrunClockController extends BaseLockscreenController {

    private static final int DIGIT_WIDTH_DP = 187;
    private static final int DIGIT_HEIGHT_DP = 184;

    private ImageView mHour1, mHour2, mMinute1, mMinute2;
    private TextView mDateView;
    private DigitalClockDisplayManager mDigitalClockDisplayManager;

    private final int[] mDigitResources = new int[]{
        R.drawable.runrun_clock_0, R.drawable.runrun_clock_1, R.drawable.runrun_clock_2,
        R.drawable.runrun_clock_3, R.drawable.runrun_clock_4, R.drawable.runrun_clock_5,
        R.drawable.runrun_clock_6, R.drawable.runrun_clock_7, R.drawable.runrun_clock_8,
        R.drawable.runrun_clock_9
    };

    @Override
    public View getView(Context context) {
        mContext = context;
        createViews();
        setupLayout();
        initializeCommonViews();
        return mContainer;
    }

    private void createViews() {
        mContainer = new ConstraintLayout(mContext);
        mContainer.setId(View.generateViewId());

        mDateView = new TextView(mContext);
        mDateView.setId(View.generateViewId());
        mDateView.setTextColor(Color.WHITE);
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        mDateView.setGravity(Gravity.CENTER);
        mDateView.setLineSpacing(0f, 1.1f);

        mHour1 = createImageView();
        mHour2 = createImageView();
        mMinute1 = createImageView();
        mMinute2 = createImageView();

        mContainer.addView(mDateView);
        mContainer.addView(mHour1);
        mContainer.addView(mHour2);
        mContainer.addView(mMinute1);
        mContainer.addView(mMinute2);

        ImageView[] digitViews = {mHour1, mHour2, mMinute1, mMinute2};
        mDigitalClockDisplayManager = new DigitalClockDisplayManager(digitViews, mDigitResources);
    }

    private ImageView createImageView() {
        ImageView iv = new ImageView(mContext);
        iv.setId(View.generateViewId());
        iv.setLayoutParams(new ConstraintLayout.LayoutParams(dpToPx(DIGIT_WIDTH_DP), dpToPx(DIGIT_HEIGHT_DP)));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    private void setupLayout() {
        LockscreenLayoutManager layoutManager = new LockscreenLayoutManager(mContainer);
        ConstraintSet cs = layoutManager.getConstraintSet();
        cs.clone(mContainer);

        cs.centerHorizontally(mDateView.getId(), ConstraintSet.PARENT_ID);
        cs.connect(mDateView.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(48));
        cs.createHorizontalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
            new int[]{mHour1.getId(), mHour2.getId()},
            null,
            ConstraintSet.CHAIN_PACKED
        );
        cs.connect(mHour1.getId(), ConstraintSet.TOP, mDateView.getId(), ConstraintSet.BOTTOM, dpToPx(16));
        cs.connect(mHour2.getId(), ConstraintSet.TOP, mHour1.getId(), ConstraintSet.TOP);
        cs.setMargin(mHour2.getId(), ConstraintSet.START, -dpToPx(30));

        cs.createHorizontalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
            new int[]{mMinute1.getId(), mMinute2.getId()},
            null,
            ConstraintSet.CHAIN_PACKED
        );
        cs.connect(mMinute1.getId(), ConstraintSet.TOP, mHour1.getId(), ConstraintSet.BOTTOM, 0);
        cs.connect(mMinute2.getId(), ConstraintSet.TOP, mMinute1.getId(), ConstraintSet.TOP);
        cs.setMargin(mMinute2.getId(), ConstraintSet.START, -dpToPx(30));
        cs.createVerticalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.TOP,
            ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM,
            new int[]{mHour1.getId(), mMinute1.getId()},
            null,
            ConstraintSet.CHAIN_PACKED
        );
        cs.setVerticalBias(mHour1.getId(), 0.5f);

        layoutManager.applyLayoutChanges();
    }



    @Override
    public void onTimeTick() {
        String timeString = LockscreenClockUtils.getCurrentTimeString("HHmm");
        mDigitalClockDisplayManager.updateTimeDisplay(timeString);
        
        mDateView.setText(LockscreenClockUtils.getCurrentTimeString("M/d\nE", Locale.CHINESE));
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        int hourColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getHourColor());
        int minuteColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getMinuteColor());
        
        mDateView.setTextColor(hourColor);
        mHour1.setColorFilter(hourColor);
        mHour2.setColorFilter(hourColor);
        mMinute1.setColorFilter(minuteColor);
        mMinute2.setColorFilter(minuteColor);
    }

    @Override
    protected void cleanup() {
        mContext = null;
        mContainer = null;
        mDigitalClockDisplayManager = null;
    }

    private int dpToPx(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }
}