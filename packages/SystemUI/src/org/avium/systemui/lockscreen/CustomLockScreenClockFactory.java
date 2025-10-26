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

package org.avium.systemui.lockscreen;

import android.content.Context;
import android.util.Log;
import org.avium.systemui.lockscreen.type.bigboom.BigBoomClockController;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.type.smallcuteclock.SmallCuteClockController;
import org.avium.systemui.lockscreen.type.easterncharacters.EasternCharactersClockController;
import org.avium.systemui.lockscreen.type.texttime.TextTimeClockController;
import org.avium.systemui.lockscreen.type.thinlongclock.ThinLongClockController;
import org.avium.systemui.lockscreen.type.moremorethin.MoreMoreThinClockController;
import org.avium.systemui.lockscreen.type.normaltime.NormalTimeClockController;
import org.avium.systemui.lockscreen.type.runrunclock.RunrunClockController;
import org.avium.systemui.lockscreen.type.classicclock.ClassicClockController;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController2;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController3;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController4;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController5;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController6;
import org.avium.systemui.lockscreen.type.guoguoclock.GuoguoClockController7;

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
            case 3:
                return new EasternCharactersClockController();
            case 4:
                return new TextTimeClockController();
            case 5:
                return new ThinLongClockController();
            case 6:
                return new MoreMoreThinClockController();
            case 8:
                return new NormalTimeClockController();
            case 9:
                return new RunrunClockController();
            case 10:
                return new ClassicClockController();
            case 11:
                return new GuoguoClockController();
            case 12:
                return new GuoguoClockController2();

            case 13:
                return new GuoguoClockController3();

            case 14:
                return new GuoguoClockController4();

            case 15:
                return new GuoguoClockController5();

            case 16:
                return new GuoguoClockController6();

            case 17:
                return new GuoguoClockController7();
            default:
                Log.w(TAG, "Unknown clock type: " + clockType);
                return new TextTimeClockController();
        }
    }
}