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

package org.avium.systemui.lockscreen.type.moremorethin;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.systemui.res.R;
//import org.avium.aviumlockscreenstudio.R;
import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.util.DigitalClockDisplayManager;
import org.avium.systemui.lockscreen.util.GlassClockManager; 
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;
import org.avium.systemui.depthwallpaper.DepthWallpaperAttacher;
import org.avium.systemui.depthwallpaper.DepthWallpaperSetup;


import java.util.Locale;

public class MoreMoreThinClockController extends BaseLockscreenController {

    private static final int DIGIT_WIDTH_DP = 117;
    private static final int DIGIT_HEIGHT_DP = 450;

    private ImageView mHour1, mHour2, mMinute1, mMinute2;
    private ImageView[] mDigitViews;
    private TextView mDateView;
    private DigitalClockDisplayManager mDigitalClockDisplayManager;

    private boolean mUseBlurEffect;
    private GlassClockManager mGlassClockManager;
    
    private LockscreenLayoutManager mLayoutManager;

    private final int[] mDigitResources = new int[]{
        R.drawable.moremorethin_0, R.drawable.moremorethin_1, R.drawable.moremorethin_2,
        R.drawable.moremorethin_3, R.drawable.moremorethin_4, R.drawable.moremorethin_5,
        R.drawable.moremorethin_6, R.drawable.moremorethin_7, R.drawable.moremorethin_8,
        R.drawable.moremorethin_9
    };

    @Override
    public View getView(Context context) {
        mContext = context;

        mUseBlurEffect = "blur".equalsIgnoreCase(CustomLockscreenSettings.getClockColor().trim());

        createViews();
        mLayoutManager = new LockscreenLayoutManager(mContainer);
        setupLayout();
    
        if (mUseBlurEffect) {
            mGlassClockManager.prepareWallpaper();
        }
        initializeCommonViews();
        DepthWallpaperSetup.INSTANCE.applyIfNeeded(context);
        View wrapped = DepthWallpaperAttacher.INSTANCE.wrapIfNeeded(mContainer);
        return wrapped;
    }

    private void createViews() {
        mContainer = new ConstraintLayout(mContext);
        mContainer.setId(View.generateViewId());

        mDateView = new TextView(mContext);
        mDateView.setId(View.generateViewId());
        mDateView.setTextColor(Color.WHITE);
        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        mDateView.setAlpha(0.8f);
        mContainer.addView(mDateView);

        if (mUseBlurEffect) {
            mGlassClockManager = new GlassClockManager(mContext, 4, mDigitResources);
            View[] digitViews = mGlassClockManager.getDigitViews();
            for (View iv : digitViews) {
                iv.setId(View.generateViewId());
                iv.setLayoutParams(new ConstraintLayout.LayoutParams(dpToPx(DIGIT_WIDTH_DP), dpToPx(DIGIT_HEIGHT_DP)));
//                iv.setAlpha(0.95f);
                mContainer.addView(iv);
            }
        } else {
            mHour1 = createImageView();
            mHour2 = createImageView();
            mMinute1 = createImageView();
            mMinute2 = createImageView();

            mDigitViews = new ImageView[]{mHour1, mHour2, mMinute1, mMinute2};
            
            for (ImageView iv : mDigitViews) {
                mContainer.addView(iv);
            }
            mDigitalClockDisplayManager = new DigitalClockDisplayManager(mDigitViews, mDigitResources);
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
        ConstraintSet cs = mLayoutManager.getConstraintSet();
        View[] digitViews;
        if (mUseBlurEffect) {
            digitViews = mGlassClockManager.getDigitViews();
        } else {
            digitViews = mDigitViews;
        }

        int[] clockViewIds = {digitViews[0].getId(), digitViews[1].getId(), digitViews[2].getId(), digitViews[3].getId()};

        cs.createHorizontalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.LEFT,
            ConstraintSet.PARENT_ID, ConstraintSet.RIGHT,
            clockViewIds, null, ConstraintSet.CHAIN_PACKED
        );
        
        int horizontalMarginPx = -dpToPx(20);
        cs.setMargin(clockViewIds[1], ConstraintSet.START, horizontalMarginPx);
        cs.setMargin(clockViewIds[2], ConstraintSet.START, horizontalMarginPx);
        cs.setMargin(clockViewIds[3], ConstraintSet.START, horizontalMarginPx);

        for (int id : clockViewIds) {
            cs.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            cs.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            cs.setVerticalBias(id, 0.13f); 
        }

        cs.connect(mDateView.getId(), ConstraintSet.TOP, clockViewIds[0], ConstraintSet.BOTTOM, dpToPx(8));
        cs.connect(mDateView.getId(), ConstraintSet.START, clockViewIds[0], ConstraintSet.START);
        cs.connect(mDateView.getId(), ConstraintSet.END, clockViewIds[1], ConstraintSet.END);

        mLayoutManager.applyLayoutChanges();
    }

    @Override
    public void onTimeTick() {
        String timeString = LockscreenClockUtils.getCurrentTimeString("HHmm");
        if (mUseBlurEffect) {
            mGlassClockManager.updateTime(timeString);
        } else {
            mDigitalClockDisplayManager.updateTimeDisplay(timeString);
        }
        mDateView.setText(LockscreenClockUtils.getDateWithWeekdayString(mContext));
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        if (!mUseBlurEffect) {
            int hourColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getHourColor());
            int minuteColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getMinuteColor());
            int dotColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getDotColor());
            int dayColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getDayColor());

            mDateView.setTextColor(dayColor);
            mDateView.setAlpha(0.8f);

            ImageView[] hourViews = {mHour1, mHour2};
            ImageView[] minuteViews = {mMinute1, mMinute2};
            mDigitalClockDisplayManager.applyColorAndEffects(hourViews, minuteViews);
        }
    }

    @Override
    protected void cleanup() {
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
