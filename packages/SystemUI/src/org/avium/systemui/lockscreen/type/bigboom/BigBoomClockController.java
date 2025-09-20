package org.avium.systemui.lockscreen.type.bigboom;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.systemui.res.R;
import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.DigitalClockDisplayManager;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;
import org.avium.systemui.lockscreen.util.LockscreenLayoutManager;

public class BigBoomClockController extends BaseLockscreenController {

    private ImageView mHour1, mHour2, mMinute1, mMinute2;
    private View mCenterDisc;
    private DigitalClockDisplayManager mDisplayManager;
    private LockscreenLayoutManager mLayoutManager;

    private final int[] mDigitRes = {
        R.drawable.clock_bigboom_00, R.drawable.clock_bigboom_01, R.drawable.clock_bigboom_02,
        R.drawable.clock_bigboom_03, R.drawable.clock_bigboom_04, R.drawable.clock_bigboom_05,
        R.drawable.clock_bigboom_06, R.drawable.clock_bigboom_07, R.drawable.clock_bigboom_08,
        R.drawable.clock_bigboom_09
    };

    @Override
    public View getView(Context context) {
        mContext = context;
        initializeViews();
        setupDisplayManager();
        setupLayoutManager();
        initializeCommonViews();
        return mContainer;
    }

    private void initializeViews() {
        mContainer = (ConstraintLayout) LayoutInflater.from(mContext)
                .inflate(R.layout.big_boom_lock_clock, null, false);
        mHour1 = mContainer.findViewById(R.id.iv_hour1);
        mHour2 = mContainer.findViewById(R.id.iv_hour2);
        mMinute1 = mContainer.findViewById(R.id.iv_minute1);
        mMinute2 = mContainer.findViewById(R.id.iv_minute2);
        mDateView = mContainer.findViewById(R.id.tv_date);
        mCenterDisc = mContainer.findViewById(R.id.center_disc);
    }

    private void setupDisplayManager() {
        ImageView[] digitViews = {mHour1, mHour2, mMinute1, mMinute2};
        mDisplayManager = new DigitalClockDisplayManager(digitViews, mDigitRes);
    }

    private void setupLayoutManager() {
        mLayoutManager = new LockscreenLayoutManager(mContainer);
        configureLayout();
    }

    private void configureLayout() {
        int[] hourViews = {R.id.iv_hour1, R.id.iv_hour2};
        int[] minuteViews = {R.id.iv_minute1, R.id.iv_minute2};
        
        mLayoutManager.setupHorizontalChain(hourViews, 64);
        mLayoutManager.setupBottomChain(minuteViews, 256);
    }

    @Override
    public void onTimeTick() {
        if (mHour1 == null) {
            return;
        }
        
        String timeText = LockscreenClockUtils.getCurrentTimeString("HHmm");
        mDisplayManager.updateTimeDisplay(timeText);
        updateDateDisplay();
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {
        if (mContainer == null) {
            return;
        }
        
        mLayoutManager.applyLayoutChanges();
        mLayoutManager.setViewVisibility(mCenterDisc, View.VISIBLE);
        mLayoutManager.setViewVisibility(mDateView, View.VISIBLE);
    }

    @Override
    public void applyStyles() {
        if (mHour1 == null) {
            return;
        }

        ImageView[] hourViews = {mHour1, mHour2};
        ImageView[] minuteViews = {mMinute1, mMinute2};
        mDisplayManager.applyColorAndEffects(hourViews, minuteViews);
    }

    @Override
    protected void cleanup() {
        mContainer = null;
        mContext = null;
        mHour1 = mHour2 = mMinute1 = mMinute2 = null;
        mDateView = null;
        mCenterDisc = null;
        mDisplayManager = null;
        mLayoutManager = null;
    }
}