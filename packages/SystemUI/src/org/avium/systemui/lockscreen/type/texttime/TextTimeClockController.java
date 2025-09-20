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
import android.util.Log;
import android.util.TypedValue;
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

import java.util.Calendar;
import java.util.Locale;

public class TextTimeClockController extends BaseLockscreenController {

    private TextView mIntroView, mHourView, mMinuteView;
    private Typeface mClockTypeface;
    
    private static final String[] ENGLISH_WORDS = {
        "Twelve", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] ENGLISH_TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty"};
    private static final String[] CHINESE_DIGITS = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

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
            mClockTypeface = ResourcesCompat.getFont(mContext, R.font.FlyFlowerSong);
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

        LockscreenLayoutManager layoutManager = new LockscreenLayoutManager(mContainer);
        ConstraintSet cs = layoutManager.getConstraintSet();
        
        int leftMarginPx = (int) (60 * mContext.getResources().getDisplayMetrics().density);
        int containerId = textContainer.getId();

        cs.connect(containerId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        cs.connect(containerId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        cs.connect(containerId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, leftMarginPx);
        cs.constrainWidth(containerId, ConstraintSet.WRAP_CONTENT);
        cs.constrainHeight(containerId, ConstraintSet.WRAP_CONTENT);
        cs.setVerticalBias(containerId, 0.25f);

        layoutManager.applyLayoutChanges();
    }

    @Override
    public void onTimeTick() {
        boolean isChinese = Locale.getDefault().getLanguage().equals(Locale.CHINESE.getLanguage());
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        if (isChinese) {
            mIntroView.setText("现在是");
            mHourView.setText(convertToChineseNumber(hour) + "时");
            mMinuteView.setText(convertToChineseNumber(minute) + "分");
        } else {
            mIntroView.setText("It's");
            mHourView.setText(convertToEnglishWords(hour));
            mMinuteView.setText(minute == 0 ? "O'clock" : convertToEnglishWords(minute));
        }
    }

    private String convertToEnglishWords(int number) {
        if (number == 0) return "Midnight"; 
        if (number < 20) return ENGLISH_WORDS[number];
        int tens = number / 10;
        int ones = number % 10;
        if (ones == 0) return ENGLISH_TENS[tens];
        return ENGLISH_TENS[tens] + " " + ENGLISH_WORDS[ones];
    }
    
    private String convertToChineseNumber(int n) {
        if (n == 0) return "零";
        if (n <= 10) return CHINESE_DIGITS[n];
        if (n < 20) return "十" + CHINESE_DIGITS[n % 10];
        String tens = CHINESE_DIGITS[n / 10] + "十";
        String ones = (n % 10 == 0) ? "" : CHINESE_DIGITS[n % 10];
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
    }

    @Override
    protected void cleanup() {
        mContext = null;
        mContainer = null;
        mIntroView = null;
        mHourView = null;
        mMinuteView = null;
    }
}