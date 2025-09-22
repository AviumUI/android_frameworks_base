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
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.util.DigitalClockDisplayManager;
import org.avium.systemui.lockscreen.util.GlassClockManager;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;

import java.util.Locale;

public class RunrunClockController extends BaseLockscreenController {

    private static final int DIGIT_WIDTH_DP = 187;
    private static final int DIGIT_HEIGHT_DP = 184;

    private ImageView mHour1, mHour2, mMinute1, mMinute2;
    private TextView mDateView;
    private DigitalClockDisplayManager mDigitalClockDisplayManager;

    //Add blur
    private boolean mUseBlurEffect;
    private GlassClockManager mGlassClockManager;

    private final int[] mDigitResources = new int[]{
        R.drawable.runrun_clock_0, R.drawable.runrun_clock_1, R.drawable.runrun_clock_2,
        R.drawable.runrun_clock_3, R.drawable.runrun_clock_4, R.drawable.runrun_clock_5,
        R.drawable.runrun_clock_6, R.drawable.runrun_clock_7, R.drawable.runrun_clock_8,
        R.drawable.runrun_clock_9
    };

    @Override
    public View getView(Context context) {
        mContext = context;
        //Add blur
        mUseBlurEffect = "blur".equalsIgnoreCase(CustomLockscreenSettings.getClockColor().trim());

        createViews();
        setupLayout();

        //Add blur
        if (mUseBlurEffect) {
            mGlassClockManager.prepareWallpaper();
        }

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
        mContainer.addView(mDateView);

        //Add blur
        if (mUseBlurEffect) {
            mGlassClockManager = new GlassClockManager(mContext, 4, mDigitResources);
            View[] digitViews = mGlassClockManager.getDigitViews();
            for (View iv : digitViews) {
                iv.setId(View.generateViewId());
                iv.setLayoutParams(new ConstraintLayout.LayoutParams(dpToPx(DIGIT_WIDTH_DP), dpToPx(DIGIT_HEIGHT_DP)));
                iv.setAlpha(0.99f);
                mContainer.addView(iv);
            }
        } else {
            mHour1 = createImageView();
            mHour2 = createImageView();
            mMinute1 = createImageView();
            mMinute2 = createImageView();

            mContainer.addView(mHour1);
            mContainer.addView(mHour2);
            mContainer.addView(mMinute1);
            mContainer.addView(mMinute2);

            ImageView[] digitViews = {mHour1, mHour2, mMinute1, mMinute2};
            mDigitalClockDisplayManager = new DigitalClockDisplayManager(digitViews, mDigitResources);
        }
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

        //Add blur
        int h1Id, h2Id, m1Id, m2Id;
        if (mUseBlurEffect) {
            View[] digitViews = mGlassClockManager.getDigitViews();
            h1Id = digitViews[0].getId();
            h2Id = digitViews[1].getId();
            m1Id = digitViews[2].getId();
            m2Id = digitViews[3].getId();
        } else {
            h1Id = mHour1.getId();
            h2Id = mHour2.getId();
            m1Id = mMinute1.getId();
            m2Id = mMinute2.getId();
        }

        cs.centerHorizontally(mDateView.getId(), ConstraintSet.PARENT_ID);
        cs.connect(mDateView.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(48));

        cs.createHorizontalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
            new int[]{h1Id, h2Id},
            null,
            ConstraintSet.CHAIN_PACKED
        );
        cs.connect(h1Id, ConstraintSet.TOP, mDateView.getId(), ConstraintSet.BOTTOM, dpToPx(16));
        cs.connect(h2Id, ConstraintSet.TOP, h1Id, ConstraintSet.TOP);
        cs.setMargin(h2Id, ConstraintSet.START, -dpToPx(30));

        cs.createHorizontalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
            new int[]{m1Id, m2Id},
            null,
            ConstraintSet.CHAIN_PACKED
        );
        cs.connect(m1Id, ConstraintSet.TOP, h1Id, ConstraintSet.BOTTOM, 0);
        cs.connect(m2Id, ConstraintSet.TOP, m1Id, ConstraintSet.TOP);
        cs.setMargin(m2Id, ConstraintSet.START, -dpToPx(30));

        cs.createVerticalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.TOP,
            ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM,
            new int[]{h1Id, m1Id},
            null,
            ConstraintSet.CHAIN_PACKED
        );
        cs.setVerticalBias(h1Id, 0.5f);

        layoutManager.applyLayoutChanges();
    }

    @Override
    public void onTimeTick() {
        String timeString = LockscreenClockUtils.getCurrentTimeString("HHmm");
        //Add blur
        if (mUseBlurEffect) {
            mGlassClockManager.updateTime(timeString);
        } else {
            mDigitalClockDisplayManager.updateTimeDisplay(timeString);
        }

        mDateView.setText(LockscreenClockUtils.getCurrentTimeString("M/d\nE", Locale.CHINESE));
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        //Add blur
        if (!mUseBlurEffect) {
            int hourColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getHourColor());
            int minuteColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getMinuteColor());

            mDateView.setTextColor(hourColor);
            mHour1.setColorFilter(hourColor);
            mHour2.setColorFilter(hourColor);
            mMinute1.setColorFilter(minuteColor);
            mMinute2.setColorFilter(minuteColor);
        }
    }

    @Override
    protected void cleanup() {
        //Add blur
        if (mGlassClockManager != null) {
            mGlassClockManager.cleanup();
        }
        mContext = null;
        mContainer = null;
        mDigitalClockDisplayManager = null;
    }

    private int dpToPx(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }
}