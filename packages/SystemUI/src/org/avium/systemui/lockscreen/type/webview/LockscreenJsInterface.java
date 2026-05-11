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

package org.avium.systemui.lockscreen.type.webview;

import android.webkit.JavascriptInterface;
import android.os.SystemProperties;

import com.android.systemui.plugins.statusbar.StatusBarStateController;

public class LockscreenJsInterface {
    private String mThemeName;
    private StatusBarStateController mStatusBarStateController;

    public LockscreenJsInterface() {
    }

    public void setStatusBarStateController(StatusBarStateController controller) {
        mStatusBarStateController = controller;
    }

    public void setThemeName(String themeName) {
        mThemeName = themeName;
    }

    @JavascriptInterface
    public String getSystemProperty(String key, String defaultValue) {
        return SystemProperties.get(key, defaultValue);
    }

    @JavascriptInterface
    public int getSystemPropertyInt(String key, int defaultValue) {
        return SystemProperties.getInt(key, defaultValue);
    }

    @JavascriptInterface
    public boolean getSystemPropertyBoolean(String key, boolean defaultValue) {
        return SystemProperties.getBoolean(key, defaultValue);
    }

    @JavascriptInterface
    public String getCurrentTime() {
        return String.valueOf(System.currentTimeMillis());
    }

    @JavascriptInterface
    public String getThemeName() {
        return mThemeName;
    }

    @JavascriptInterface
    public boolean isAod() {
        if (mStatusBarStateController != null) {
            return mStatusBarStateController.isDozing();
        }
        return false;
    }
}
