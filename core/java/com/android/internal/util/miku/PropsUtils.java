/*
 * Copyright (C) 2020 The Pixel Experience Project
 *
 * Copyright (C) 2021-2022 Miku UI
 * 
 * Copyright (C) 2025 Avium UI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.internal.util.miku;

import android.os.Build;
import android.util.Log;

import java.util.Arrays;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PropsUtils {

    private static final String TAG = PropsUtils.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final Map<String, Object> propsToChangeMeizu;
    private static final String[] meizuPropToChangeBase = {
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
            "com.kugou.android",
            "com.kugou.android.lite",
            "cmccwm.mobilemusic",
            "cn.kuwo.player",
            "com.meizu.media.music"
    };

    private static final String[] meizuPropToChange;

    static {
        if (DEBUG) {
            meizuPropToChange = Arrays.copyOf(meizuPropToChangeBase, meizuPropToChangeBase.length + 1);
            meizuPropToChange[meizuPropToChangeBase.length] = "com.finalwire.aida64";
        } else {
            meizuPropToChange = meizuPropToChangeBase.clone();
        }

        propsToChangeMeizu = new HashMap<>();
        propsToChangeMeizu.put("BRAND", "meizu");
        propsToChangeMeizu.put("MANUFACTURER", "Meizu");
        propsToChangeMeizu.put("DEVICE", "m1892");
        propsToChangeMeizu.put("DISPLAY", "Flyme");
        propsToChangeMeizu.put("PRODUCT", "meizu_16thPlus_CN");
        propsToChangeMeizu.put("MODEL", "meizu 16th Plus");
        propsToChangeMeizu.put("FINGERPRINT", "meizu/qssi/qssi:10/QKQ1.191222.002/1595524937:user/release-keys");
        propsToChangeMeizu.put("TYPE", "user");
    }

    public static void setProps(String packageName) {
        if (packageName == null){
            return;
        }
	// Set Props for StatusBar Lyric
        if(Arrays.asList(meizuPropToChange).contains(packageName)){
            if (DEBUG) Log.d(TAG, "Defining props for: " + packageName);
            for (Map.Entry<String, Object> prop : propsToChangeMeizu.entrySet()) {
                String key = prop.getKey();
                Object value = prop.getValue();
                if (DEBUG) Log.d(TAG, "Defining " + key + " prop for: " + packageName);
                setPropValue(key, value);
            }
        }
        // Set proper indexing fingerprint
        /*
        if (packageName.equals("com.google.android.settings.intelligence")){
            setPropValue("FINGERPRINT", Build.DATE);
        }
        */
    }

    private static void setPropValue(String key, Object value){
        try {
            if (DEBUG) Log.d(TAG, "Defining prop " + key + " to " + value.toString());
            Field field = Build.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
            field.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static boolean isCallerSafetyNet() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(elem -> elem.getClassName().contains("DroidGuard"));
    }

    public static void onEngineGetCertificateChain() {
        // Check stack for SafetyNet
        if (isCallerSafetyNet()) {
            throw new UnsupportedOperationException();
        }
    }
}
