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
//import org.avium.aviumlockscreenstudio.R;
import com.android.systemui.res.R;

import java.util.Locale;

public class ClassicClockController extends BaseLockscreenController {

    private TextView mGregorianDateView, mLunarDateView, mTimeView;

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
        mLunarDateView.setText(LockscreenClockUtils.getLunarDateString());
        mTimeView.setText(LockscreenClockUtils.getCurrentTimeString(mContext.getString(R.string.classic_time_format)));
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        int dateColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getHourColor());
        int timeColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getMinuteColor());
        
        mGregorianDateView.setTextColor(dateColor);
        mLunarDateView.setTextColor(dateColor);
        mTimeView.setTextColor(timeColor);
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