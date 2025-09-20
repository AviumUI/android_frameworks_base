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

package org.avium.systemui.lockscreen.type.easterncharacters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.res.ResourcesCompat;

import com.android.systemui.res.R;

import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;

import java.util.Date;
import java.util.Locale;

public class EasternCharactersClockController extends BaseLockscreenController {

    private TextView mHourChar1, mHourChar2, mDateInfoView, mMinuteChar1, mMinuteChar2;
    private Typeface mClockTypeface;
    private static final String[] CHINESE_DIGITS = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
    private static final String[] CHINESE_HOUR_UNITS = {"", "十", "廿"};


    @Override
    public View getView(Context context) {
        mContext = context;
        loadCustomFont();
        createViews();
        setupLayout();
        initializeCommonViews();
        return mContainer;
    }

    private void loadCustomFont() {
        try {
            mClockTypeface = ResourcesCompat.getFont(mContext, R.font.mingchao);
        } catch (Resources.NotFoundException e) {
            mClockTypeface = Typeface.DEFAULT;
        }
    }

    private void createViews() {
        mContainer = new ConstraintLayout(mContext);
        mContainer.setId(View.generateViewId());

        mHourChar1 = createTextView(100, true);
        mHourChar2 = createTextView(100, true);
        mMinuteChar1 = createTextView(100, true);
        mMinuteChar2 = createTextView(100, true);
        
        mDateInfoView = createTextView(18, false);
        mDateInfoView.setLineSpacing(0, 1.2f);
        
        mContainer.addView(mHourChar1);
        mContainer.addView(mHourChar2);
        mContainer.addView(mDateInfoView);
        mContainer.addView(mMinuteChar1);
        mContainer.addView(mMinuteChar2);
    }

    private TextView createTextView(float sizeSp, boolean isClock) {
        TextView textView = new TextView(mContext);
        textView.setId(View.generateViewId());
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        textView.setGravity(Gravity.CENTER);
        if (isClock && mClockTypeface != null) {
            textView.setTypeface(mClockTypeface);
        }
        return textView;
    }

    private void setupLayout() {
        LockscreenLayoutManager layoutManager = new LockscreenLayoutManager(mContainer);
        ConstraintSet cs = layoutManager.getConstraintSet();

        int[] viewIds = {
            mHourChar1.getId(), mHourChar2.getId(),
            mDateInfoView.getId(),
            mMinuteChar1.getId(), mMinuteChar2.getId()
        };

        cs.createVerticalChain(
            ConstraintSet.PARENT_ID, ConstraintSet.TOP,
            ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM,
            viewIds,
            null,
            ConstraintSet.CHAIN_PACKED
        );
        
        for (int id : viewIds) {
            cs.centerHorizontally(id, ConstraintSet.PARENT_ID);
        }

        cs.setVerticalBias(mDateInfoView.getId(), 0.2f);

        cs.setMargin(mHourChar2.getId(), ConstraintSet.TOP, 16);
        cs.setMargin(mDateInfoView.getId(), ConstraintSet.TOP, 48);
        cs.setMargin(mMinuteChar1.getId(), ConstraintSet.TOP, 48);
        cs.setMargin(mMinuteChar2.getId(), ConstraintSet.TOP, 16);

        layoutManager.applyLayoutChanges();
    }

    @Override
    public void onTimeTick() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY) % 12;
        if (hour == 0) hour = 12;
        int minute = calendar.get(Calendar.MINUTE);
        
        updateClockCharacters(hour, minute);
        updateDateInfo();
    }

    private void updateClockCharacters(int hour, int minute) {
        String[] hourChars;
        String[] minuteChars;

        if (minute == 0) {
            hourChars = new String[]{convertToChineseHour(hour), ""};
            minuteChars = new String[]{"時", ""};
        } else {
            String hourText = convertToChineseHour(hour);
            if (hourText.length() == 1) {
                hourChars = new String[]{hourText, "時"};
            } else {
                hourChars = new String[]{String.valueOf(hourText.charAt(0)), String.valueOf(hourText.charAt(1))};
            }
            minuteChars = convertToChineseMinute(minute);
        }

        updateTextView(mHourChar1, hourChars[0]);
        updateTextView(mHourChar2, hourChars[1]);
        updateTextView(mMinuteChar1, minuteChars[0]);
        updateTextView(mMinuteChar2, minuteChars[1]);
    }

    private void updateDateInfo() {
        Date now = new Date();
        SimpleDateFormat weekDayFormat = new SimpleDateFormat("EEEEa", Locale.CHINESE);
        SimpleDateFormat dateFormat = new SimpleDateFormat("M月d日 H:mm", Locale.CHINESE);
        
        String weekDayStr = weekDayFormat.format(now).replace("星期", "周");
        String dateStr = dateFormat.format(now);
        
        mDateInfoView.setText(String.format("%s\n%s", weekDayStr, dateStr));
    }
    
    private void updateTextView(TextView view, String text) {
        if (TextUtils.isEmpty(text)) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }

    private String convertToChineseHour(int hour) {
        if (hour >= 1 && hour <= 10) {
            return CHINESE_DIGITS[hour];
        } else if (hour == 11) {
            return "十一";
        } else if (hour == 12) {
            return "十二";
        }
        return "";
    }

    private String[] convertToChineseMinute(int minute) {
        if (minute < 10) {
            return new String[]{CHINESE_DIGITS[minute], ""};
        } else if (minute == 10) {
            return new String[]{"十", "分"};
        } else if (minute < 20) {
            return new String[]{"十", CHINESE_DIGITS[minute % 10]};
        } else {
            int tens = minute / 10;
            int ones = minute % 10;
            String tensChar = CHINESE_DIGITS[tens];
            if (ones == 0) {
                return new String[]{tensChar, "十"};
            } else {
                return new String[]{tensChar, CHINESE_DIGITS[ones]};
            }
        }
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        int hourColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getHourColor());
        int minuteColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getMinuteColor());

        mHourChar1.setTextColor(hourColor);
        mHourChar2.setTextColor(hourColor);
        mMinuteChar1.setTextColor(minuteColor);
        mMinuteChar2.setTextColor(minuteColor);
    }

    @Override
    protected void cleanup() {
        mContext = null;
        mContainer = null;
        mHourChar1 = null;
        mHourChar2 = null;
        mDateInfoView = null;
        mMinuteChar1 = null;
        mMinuteChar2 = null;
    }
}