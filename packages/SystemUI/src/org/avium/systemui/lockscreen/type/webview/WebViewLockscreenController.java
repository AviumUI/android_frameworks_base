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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.UserManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.res.R;
import org.avium.systemui.depthwallpaper.DepthWallpaperAttacher;
import org.avium.systemui.depthwallpaper.DepthWallpaperSetup;

import org.avium.systemui.lockscreen.ICustomLockScreenClock;

import java.io.File;

public class WebViewLockscreenController implements ICustomLockScreenClock,
        StatusBarStateController.StateListener {
    private static final String TAG = "WebViewLockscreen";
    private static final String ACTION_THEME_CHANGED = "org.avium.lockscreen.THEME_CHANGED";
    private static final String ACTION_APPLY_THEME = "org.avium.lockscreen.APPLY_THEME";
    private static final String EXTRA_ZIP_PATH = "zip_path";
    private static final String EXTRA_ZIP_URI = "zip_uri";
    private static final String EXTRA_THEME_NAME = "theme_name";
    private static final String INTERFACE_NAME = "AviumLockscreen";

    private Context mContext;
    private Context mReceiverContext;
    private Context mCredentialContext;
    private WebView mWebView;
    private FrameLayout mContainer;
    private TextView mUnlockPromptView;
    private Handler mMainHandler;
    private Handler mBgHandler;
    private HandlerThread mBgThread;
    private ThemeChangeReceiver mThemeReceiver;
    private UserUnlockReceiver mUserUnlockReceiver;
    private ThemeManagerHelper mThemeManager;
    private LockscreenWebViewClient mWebViewClient;
    private LockscreenJsInterface mJsInterface;
    private StatusBarStateController mStatusBarStateController;
    private boolean mIsLoaded = false;
    private boolean mWebViewInitialized = false;

    public WebViewLockscreenController(StatusBarStateController statusBarStateController) {
        mStatusBarStateController = statusBarStateController;
    }

    @Override
    public View getView(Context context) {
        mReceiverContext = context;
        mContext = context;
        mMainHandler = new Handler(Looper.getMainLooper());

        mBgThread = new HandlerThread("WebViewLockscreenBg");
        mBgThread.start();
        mBgHandler = new Handler(mBgThread.getLooper());

        mThemeManager = new ThemeManagerHelper(mContext.getFilesDir());

        createContainer();
        registerThemeReceiver();
        
        UserManager userManager = mContext.getSystemService(UserManager.class);
        if (userManager != null && userManager.isUserUnlocked()) {
            initializeWebView();
        } else {
            showUnlockPrompt();
        }

        DepthWallpaperSetup.INSTANCE.applyIfNeeded(context);

        if (mStatusBarStateController != null) {
            mStatusBarStateController.addCallback(this);
        }

        View wrapped = DepthWallpaperAttacher.INSTANCE.wrapIfNeeded(mContainer);
        return wrapped;
    }

    @Override
    public void onDozingChanged(boolean isDozing) {
        if (mWebView != null && mIsLoaded) {
            mMainHandler.post(() -> {
                if (mWebView != null) {
                    mWebView.evaluateJavascript(
                            "javascript:if(typeof onAodStateChanged === 'function') onAodStateChanged(" + isDozing + ");",
                            null
                    );
                }
            });
        }
    }

    private void createContainer() {
        mContainer = new FrameLayout(mContext);
        mContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    private void showUnlockPrompt() {
        mUnlockPromptView = new TextView(mContext);
        mUnlockPromptView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ));
        mUnlockPromptView.setText(mContext.getString(R.string.webview_lockscreen_unlock_required));
        mUnlockPromptView.setTextColor(0xFFFFFFFF);
        mUnlockPromptView.setTextSize(18);
        mUnlockPromptView.setTypeface(Typeface.DEFAULT_BOLD);
        mUnlockPromptView.setGravity(Gravity.CENTER);
        mContainer.addView(mUnlockPromptView);
    }

    private void hideUnlockPrompt() {
        if (mUnlockPromptView != null) {
            mContainer.removeView(mUnlockPromptView);
            mUnlockPromptView = null;
        }
    }

    private void initializeWebView() {
        if (mWebViewInitialized) {
            return;
        }
        
        hideUnlockPrompt();
        mCredentialContext = mContext.createCredentialProtectedStorageContext();
        setupWebView();
        loadCurrentTheme();
        mWebViewInitialized = true;
    }

    private void setupWebView() {
        mWebView = new WebView(mCredentialContext);
        mWebView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mWebView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        mWebView.setWebChromeClient(new WebChromeClient());
        mWebViewClient = new LockscreenWebViewClient(mThemeManager.getResourceCache());
        mWebViewClient.setOnPageFinishedCallback(() -> mIsLoaded = true);
        mWebView.setWebViewClient(mWebViewClient);
        mJsInterface = new LockscreenJsInterface();
        mJsInterface.setStatusBarStateController(mStatusBarStateController);
        mWebView.addJavascriptInterface(mJsInterface, INTERFACE_NAME);

        mWebView.setOnTouchListener((v, event) -> true);

        mContainer.addView(mWebView);
    }

    private void registerThemeReceiver() {
        mThemeReceiver = new ThemeChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_THEME_CHANGED);
        filter.addAction(ACTION_APPLY_THEME);
        mReceiverContext.registerReceiver(mThemeReceiver, filter, Context.RECEIVER_EXPORTED);

        mUserUnlockReceiver = new UserUnlockReceiver();
        IntentFilter unlockFilter = new IntentFilter();
        unlockFilter.addAction(Intent.ACTION_USER_UNLOCKED);
        mReceiverContext.registerReceiver(mUserUnlockReceiver, unlockFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void loadCurrentTheme() {
        mBgHandler.post(() -> {
            File indexFile = new File(mThemeManager.getCurrentThemeDir(), "index.html");
            if (indexFile.exists()) {
                mThemeManager.cacheThemeResources();
                mMainHandler.post(() -> {
                    if (mWebView != null) {
                        mWebView.loadUrl("file://" + indexFile.getAbsolutePath());
                    }
                });
            } else {
                mMainHandler.post(() -> loadErrorPage("No theme installed"));
            }
        });
    }

    private void handleThemeZip(String zipPath, String themeName) {
        if (mBgHandler == null) {
            return;
        }
        mBgHandler.post(() -> {
            boolean success = mThemeManager.processThemeZip(zipPath, themeName);
            if (success) {
                mJsInterface.setThemeName(themeName);
                reloadTheme();
            }
        });
    }

    private void reloadTheme() {
        mIsLoaded = false;
        mThemeManager.cacheThemeResources();
        loadCurrentTheme();
    }

    private void loadErrorPage(String message) {
        String html = "<html><body style='background:transparent;color:white;text-align:center;padding-top:50%;'>" +
                "<h2>Lockscreen Theme Error</h2>" +
                "<p>" + message + "</p>" +
                "</body></html>";
        mWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    @Override
    public void onTimeTick() {
        if (mWebView != null && mIsLoaded) {
            mMainHandler.post(() -> {
                if (mWebView != null) {
                    mWebView.evaluateJavascript("javascript:if(typeof onTimeTick === 'function') onTimeTick();", null);
                }
            });
        }
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {
        if (mWebView != null && mIsLoaded) {
            mMainHandler.post(() -> {
                if (mWebView != null) {
                    mWebView.evaluateJavascript(
                            "javascript:if(typeof onNotificationStateChanged === 'function') onNotificationStateChanged(" + hasNotifications + ");",
                            null
                    );
                }
            });
        }
    }

    @Override
    public void applyStyles() {
    }

    @Override
    public void onDestroy() {
        mIsLoaded = false;

        if (mStatusBarStateController != null) {
            mStatusBarStateController.removeCallback(this);
        }

        if (mThemeReceiver != null) {
            mReceiverContext.unregisterReceiver(mThemeReceiver);
            mThemeReceiver = null;
        }

        if (mUserUnlockReceiver != null) {
            mReceiverContext.unregisterReceiver(mUserUnlockReceiver);
            mUserUnlockReceiver = null;
        }

        if (mBgThread != null) {
            mBgThread.quitSafely();
            mBgThread = null;
        }
        mBgHandler = null;

        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.loadUrl("about:blank");
            mWebView.removeAllViews();
            mWebView.destroy();
            mWebView = null;
        }

        if (mContainer != null) {
            mContainer.removeAllViews();
            mContainer = null;
        }

        mMainHandler = null;
    }

    private class ThemeChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            String action = intent.getAction();
            if (ACTION_APPLY_THEME.equals(action)) {
                String zipPath = intent.getStringExtra(EXTRA_ZIP_PATH);
                String themeName = intent.getStringExtra(EXTRA_THEME_NAME);
                if (zipPath != null && themeName != null) {
                    handleThemeZip(zipPath, themeName);
                }
            } else if (ACTION_THEME_CHANGED.equals(action)) {
                reloadTheme();
            }
        }
    }

    private class UserUnlockReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            
            if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                mMainHandler.post(() -> {
                    if (!mWebViewInitialized) {
                        initializeWebView();
                    }
                });
            }
        }
    }
}
