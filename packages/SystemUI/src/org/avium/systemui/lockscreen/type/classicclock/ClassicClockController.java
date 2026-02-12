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

package org.avium.systemui.lockscreen.type.classicclock;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;
import com.android.systemui.res.R;
import org.avium.systemui.depthwallpaper.DepthWallpaperAttacher;
import org.avium.systemui.depthwallpaper.DepthWallpaperSetup;

import java.util.Locale;

public class ClassicClockController extends BaseLockscreenController {

    private TextView mGregorianDateView, mLunarDateView, mTimeView;

    @Override
    public View getView(Context context) {
        mContext = context;
        createViews();
        setupLayout();
        initializeCommonViews();
        DepthWallpaperSetup.INSTANCE.applyIfNeeded(context);
        View wrapped = DepthWallpaperAttacher.INSTANCE.wrapIfNeeded(mContainer);
        return wrapped;
    }

    private void createViews() {
        mContainer = new ConstraintLayout(mContext);
        mContainer.setId(View.generateViewId());

        mGregorianDateView = createTextView(18);
        mLunarDateView = createTextView(18);
        mTimeView = createTextView(100);

        mContainer.addView(mGregorianDateView);
        mContainer.addView(mLunarDateView);
        mContainer.addView(mTimeView);
    }

    private TextView createTextView(float sizeSp) {
        TextView textView = new TextView(mContext);
        textView.setId(View.generateViewId());
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        return textView;
    }

    private void setupLayout() {
        LockscreenLayoutManager layoutManager = new LockscreenLayoutManager(mContainer);
        ConstraintSet cs = layoutManager.getConstraintSet();

        int[] viewIds = {mGregorianDateView.getId(), mLunarDateView.getId(), mTimeView.getId()};
        
        cs.createVerticalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.TOP,
            ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM,
            viewIds, null, ConstraintSet.CHAIN_PACKED
        );

        cs.setVerticalBias(viewIds[0], 0.15f);

        for (int id : viewIds) {
            cs.centerHorizontally(id, ConstraintSet.PARENT_ID);
        }

        int marginPx = (int) (2 * mContext.getResources().getDisplayMetrics().density);
        cs.setMargin(mLunarDateView.getId(), ConstraintSet.TOP, marginPx);
        cs.setMargin(mTimeView.getId(), ConstraintSet.TOP, marginPx);
        
        layoutManager.applyLayoutChanges();
    }

    @Override
    public void onTimeTick() {
        mGregorianDateView.setText(LockscreenClockUtils.getCurrentTimeString(mContext.getString(R.string.classic_date_format), Locale.CHINESE));
        if (CustomLockscreenSettings.shouldShowLunar()) {
            mLunarDateView.setText(LockscreenClockUtils.getLunarDateString());
            mLunarDateView.setVisibility(View.VISIBLE);
        } else {
            mLunarDateView.setVisibility(View.GONE);
        }
        mTimeView.setText(LockscreenClockUtils.getCurrentTimeString(mContext.getString(R.string.classic_time_format)));
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        int hourColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getHourColor());
        int minuteColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getMinuteColor());
        int dayColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getDayColor());
        
        mGregorianDateView.setTextColor(dayColor);
        mLunarDateView.setTextColor(dayColor);
        mTimeView.setTextColor(minuteColor);
    }

    @Override
    protected void cleanup() {
        mContext = null;
        mContainer = null;
        mGregorianDateView = null;
        mLunarDateView = null;
        mTimeView = null;
    }
}
