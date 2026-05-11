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

package org.avium.systemui.lockscreen.type.guoguoclock;

import com.android.systemui.res.R;

public class GuoguoClockController extends BaseGuoguoClockController {

    private final int[] mDigitResources = new int[]{
            R.drawable.avium_guo_type1_0, R.drawable.avium_guo_type1_1, R.drawable.avium_guo_type1_2,
            R.drawable.avium_guo_type1_3, R.drawable.avium_guo_type1_4, R.drawable.avium_guo_type1_5,
            R.drawable.avium_guo_type1_6, R.drawable.avium_guo_type1_7, R.drawable.avium_guo_type1_8,
            R.drawable.avium_guo_type1_9
    };
    private final int mDotResource = R.drawable.avium_guo_type1_dot;

    @Override
    protected int[] getDigitResources() {
        return mDigitResources;
    }

    @Override
    protected int getDotResource() {
        return mDotResource;
    }
}
