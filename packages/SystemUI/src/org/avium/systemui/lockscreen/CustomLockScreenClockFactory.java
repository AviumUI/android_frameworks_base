package org.avium.systemui.lockscreen;

import android.content.Context;
import android.util.Log;
import org.avium.systemui.lockscreen.type.bigboom.BigBoomClockController;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.type.smallcuteclock.SmallCuteClockController;

public class CustomLockScreenClockFactory {

    private static final String TAG = "AVIUM_LOCKSCREEN";

    public static ICustomLockScreenClock create(Context context) {
        if (!CustomLockscreenSettings.isEnabled()) {
            return null;
        }

        int clockType = CustomLockscreenSettings.getClockType();
        
        switch (clockType) {
            case 1:
                return new BigBoomClockController();
            case 2:
                return new SmallCuteClockController();
            default:
                Log.w(TAG, "Unknown clock type: " + clockType);
                return null;
        }
    }
}