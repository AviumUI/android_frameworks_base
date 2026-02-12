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

package org.avium.systemui.lockscreen.type.texttime;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.res.ResourcesCompat;

import com.android.systemui.res.R;
import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;
import org.avium.systemui.depthwallpaper.DepthWallpaperAttacher;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TextTimeClockController extends BaseLockscreenController {

    private TextView mIntroView, mHourView, mMinuteView;
    private TextView mMonthView, mDayView, mWeekView;
    private Typeface mClockTypeface;
    
    private String[] mNumberWords;
    private String[] mTensWords;
    private String[] mChineseDigits;

    @Override
    public View getView(Context context) {
        mContext = context;
        loadStringResources();
        loadCustomFont();
        createViews();
        setupLayout();
        initializeCommonViews();
        View wrapped = DepthWallpaperAttacher.INSTANCE.wrapIfNeeded(mContainer);
        return wrapped;
    }

    private void loadStringResources() {
        mNumberWords = mContext.getResources().getStringArray(R.array.number_words);
        mTensWords = mContext.getResources().getStringArray(R.array.tens_words);
        mChineseDigits = mContext.getResources().getStringArray(R.array.number_words);
    }

    private void loadCustomFont() {
        try {
            mClockTypeface = ResourcesCompat.getFont(mContext, R.font.fly_flower_song);
        } catch (Resources.NotFoundException e) {
            mClockTypeface = Typeface.DEFAULT;
        }
    }

    private void createViews() {
        mContainer = new ConstraintLayout(mContext);
        mContainer.setId(View.generateViewId());
        
        mIntroView = createTextView(50);
        mHourView = createTextView(50);
        mMinuteView = createTextView(50);
        mMonthView = createTextView(22);
        mDayView = createTextView(22);
        mWeekView = createTextView(22);

        mMonthView.setIncludeFontPadding(false);
        mDayView.setIncludeFontPadding(false);
        mWeekView.setIncludeFontPadding(false);
        mMonthView.setGravity(Gravity.CENTER);
        mDayView.setGravity(Gravity.CENTER);
        mWeekView.setGravity(Gravity.CENTER);
        mMonthView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mDayView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mWeekView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    }

    private TextView createTextView(float sizeSp) {
        TextView textView = new TextView(mContext);
        textView.setId(View.generateViewId());
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (mClockTypeface != null) {
            textView.setTypeface(mClockTypeface);
        }
        return textView;
    }

    private void setupLayout() {
        LinearLayout textContainer = new LinearLayout(mContext);
        textContainer.setId(View.generateViewId());
        textContainer.setOrientation(LinearLayout.VERTICAL);

        int topMarginPx = (int) (8 * mContext.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, topMarginPx, 0, 0);

        textContainer.addView(mIntroView);
        textContainer.addView(mHourView, params);
        textContainer.addView(mMinuteView, params);

        mContainer.addView(textContainer);

        LinearLayout dateRow = new LinearLayout(mContext);
        dateRow.setId(View.generateViewId());
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        float density = mContext.getResources().getDisplayMetrics().density;
        int betweenPx = (int) (6 * density);

        LinearLayout.LayoutParams childLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        childLp.setMargins(0, 0, betweenPx, 0);

        dateRow.addView(mMonthView, childLp);
        dateRow.addView(mDayView);
        LinearLayout.LayoutParams weekLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        weekLp.setMargins(betweenPx, 0, 0, 0);
        dateRow.addView(mWeekView, weekLp);

        mContainer.addView(dateRow);

        LockscreenLayoutManager layoutManager = new LockscreenLayoutManager(mContainer);
        ConstraintSet cs = layoutManager.getConstraintSet();
        
        int leftMarginPx = (int) (60 * density);
        int bottomMarginPx = (int) (60 * density);
        int rightMarginPx = (int) (30 * density);
        int containerId = textContainer.getId();

        cs.connect(containerId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        cs.connect(containerId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        cs.connect(containerId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, leftMarginPx);
        cs.constrainWidth(containerId, ConstraintSet.WRAP_CONTENT);
        cs.constrainHeight(containerId, ConstraintSet.WRAP_CONTENT);
        cs.setVerticalBias(containerId, 0.25f);

        cs.connect(dateRow.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, bottomMarginPx);
        cs.connect(dateRow.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, rightMarginPx);
        cs.constrainWidth(dateRow.getId(), ConstraintSet.WRAP_CONTENT);
        cs.constrainHeight(dateRow.getId(), ConstraintSet.WRAP_CONTENT);

        layoutManager.applyLayoutChanges();
    }

    @Override
    public void onTimeTick() {
        boolean isChinese = Locale.getDefault().getLanguage().equals(Locale.CHINESE.getLanguage());
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        if (isChinese) {
            mIntroView.setText(mContext.getString(R.string.texttime_intro));
            mHourView.setText(convertToChineseNumber(hour) + mContext.getString(R.string.texttime_hour_unit));
            mMinuteView.setText(convertToChineseNumber(minute) + mContext.getString(R.string.texttime_minute_unit));
        } else {
            mIntroView.setText(mContext.getString(R.string.texttime_intro));
            mHourView.setText(convertToEnglishWords(hour));
            mMinuteView.setText(minute == 0 ? mContext.getString(R.string.texttime_oclock) : convertToEnglishWords(minute));
        }

        String monthSuffix = mContext.getString(R.string.texttime_month_suffix);
        String daySuffix = mContext.getString(R.string.texttime_day_suffix);
        String weekFormat = mContext.getString(R.string.texttime_week_format);

        String monthText, dayText;
        if (isChinese) {
            monthText = convertToChineseNumber(month) + monthSuffix;
            dayText = convertToChineseNumber(day) + daySuffix;
        } else {
            SimpleDateFormat sdfMonth = new SimpleDateFormat("MMMM", Locale.ENGLISH);
            monthText = sdfMonth.format(calendar.getTime());
            dayText = String.valueOf(day);
        }

        SimpleDateFormat sdfWeek = new SimpleDateFormat(weekFormat, Locale.getDefault());
        String weekStr = sdfWeek.format(calendar.getTime());

        mMonthView.setText(verticalize(monthText));
        mDayView.setText(verticalize(dayText));
        mWeekView.setText(verticalize(weekStr));
    }

    private String verticalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append(s.charAt(i));
            if (i != s.length() - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private String convertToEnglishWords(int number) {
        if (number == 0) return mContext.getString(R.string.texttime_midnight); 
        if (number < 20) return mNumberWords[number];
        int tens = number / 10;
        int ones = number % 10;
        if (ones == 0) return mTensWords[tens];
        return mTensWords[tens] + " " + mNumberWords[ones];
    }
    
    private String convertToChineseNumber(int n) {
        if (n == 0) return mChineseDigits[0];
        if (n <= 10) return mChineseDigits[n];
        if (n < 20) return mChineseDigits[10] + mChineseDigits[n % 10];
        String tens = mChineseDigits[n / 10] + mChineseDigits[10];
        String ones = (n % 10 == 0) ? "" : mChineseDigits[n % 10];
        return tens + ones;
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        int introColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getHourColor());
        int timeColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getMinuteColor());
        
        mIntroView.setTextColor(introColor);
        mHourView.setTextColor(timeColor);
        mMinuteView.setTextColor(timeColor);

        mDayView.setTextColor(timeColor);
        mMonthView.setTextColor(timeColor);
        
        mWeekView.setTextColor(timeColor);
    }

    @Override
    protected void cleanup() {
        mContext = null;
        mContainer = null;
        mIntroView = null;
        mHourView = null;
        mMinuteView = null;
        mMonthView = null;
        mDayView = null;
        mWeekView = null;
    }
}
