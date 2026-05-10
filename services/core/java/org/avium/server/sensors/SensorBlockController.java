/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.avium.server.sensors;

import static android.provider.Settings.System.SHAKE_SENSORS_BLACKLIST_CONFIG;

import static org.avium.content.ContextExt.SENSOR_BLOCK_MANAGER_SERVICE;
import static org.avium.hardware.SensorBlockManager.APP_FIRST_SCREEN_MS;
import static org.avium.hardware.SensorBlockManager.SHAKE_SENSORS_ALLOW;
import static org.avium.hardware.SensorBlockManager.SHAKE_SENSORS_BLOCK_ALWAYS;
import static org.avium.hardware.SensorBlockManager.SHAKE_SENSORS_BLOCK_FIRST_SCREEN;
import static org.avium.hardware.SensorBlockManager.isShakeSensorsConfigValid;
import static org.avium.os.DebugConstants.DEBUG_SENSOR;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.wm.ActivityTaskManagerInternal;

import org.avium.hardware.ISensorBlockService;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

public class SensorBlockController extends SystemService {

    private static final String TAG = "SensorBlockController";

    private static final int MSG_DELAYED_LOAD = 1;
    private static final int MSG_TOP_PACKAGE_CHANGED = 2;
    private static final int MSG_SETTINGS_CHANGED = 3;

    private static SensorBlockController sInstance;

    private final Object mLock = new Object();
    private final HashMap<String, Integer> mShakeSensorsConfig = new HashMap<>();

    private String mTopPackageName;
    private long mTopPackageChangedTime = -1;
    private int mCurrentUserId = UserHandle.USER_SYSTEM;

    private final Handler mHandler;

    private final IBinderService mBinderService = new IBinderService();
    private ActivityTaskManagerInternal mActivityTaskManagerInternal;

    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskMovedToFront(RunningTaskInfo taskInfo) {
            final String packageName = getPackageName(taskInfo);
            if (!TextUtils.isEmpty(packageName)) {
                updateTopPackage(packageName);
            }
        }
    };

    private final ContentObserver mSettingsObserver;

    private final BroadcastReceiver mPackageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return;
            }
            if (intent.getData() == null) {
                return;
            }
            final String packageName = intent.getData().getSchemeSpecificPart();
            if (packageName == null) {
                return;
            }
            onPackageRemoved(packageName);
        }
    };

    private final BroadcastReceiver mUserReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);
            if (userId != UserHandle.USER_NULL) {
                onUserSwitching(userId);
            }
        }
    };

    public SensorBlockController(Context context) {
        super(context);
        mHandler = new SensorBlockHandler();
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                mHandler.obtainMessage(MSG_SETTINGS_CHANGED, mCurrentUserId, 0).sendToTarget();
            }

            @Override
            public void onChange(boolean selfChange, Collection<Uri> uris, int flags, int userId) {
                mHandler.obtainMessage(MSG_SETTINGS_CHANGED, userId, 0).sendToTarget();
            }
        };
    }

    public static SensorBlockController getInstance() {
        return sInstance;
    }

    @Override
    public void onStart() {
        if (DEBUG_SENSOR) Slog.d(TAG, "onStart");
        sInstance = this;
        publishBinderService(SENSOR_BLOCK_MANAGER_SERVICE, mBinderService);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            if (DEBUG_SENSOR) Slog.d(TAG, "onBootPhase: PHASE_SYSTEM_SERVICES_READY");
            final IntentFilter pkgFilter = new IntentFilter();
            pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            pkgFilter.addDataScheme("package");
            getContext().registerReceiverAsUser(mPackageReceiver, UserHandle.ALL,
                    pkgFilter, null, null);

            final IntentFilter userFilter = new IntentFilter();
            userFilter.addAction(Intent.ACTION_USER_SWITCHED);
            getContext().registerReceiverAsUser(mUserReceiver, UserHandle.ALL,
                    userFilter, null, null);
            observeSettings();
            registerTaskStackListener();
        } else if (phase == PHASE_BOOT_COMPLETED) {
            if (DEBUG_SENSOR) Slog.d(TAG, "onBootPhase: PHASE_BOOT_COMPLETED");
            onBootCompleted();
        }
    }

    void onBootCompleted() {
        mHandler.sendEmptyMessage(MSG_DELAYED_LOAD);
    }

    void onUserSwitching(int userId) {
        if (DEBUG_SENSOR) Slog.d(TAG, "onUserSwitching: " + userId);
        mCurrentUserId = userId;
        loadConfigForUser(userId);
    }

    void onPackageRemoved(String packageName) {
        synchronized (mLock) {
            if (mShakeSensorsConfig.containsKey(packageName)) {
                if (DEBUG_SENSOR) Slog.d(TAG, "onPackageRemoved: " + packageName);
                mShakeSensorsConfig.remove(packageName);
                saveConfigLocked();
            }
        }
    }

    public void updateTopPackage(String packageName) {
        mHandler.obtainMessage(MSG_TOP_PACKAGE_CHANGED, packageName).sendToTarget();
    }

    private void handleTopPackageChanged(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        synchronized (mLock) {
            if (packageName.equals(mTopPackageName)) {
                return;
            }
            if (DEBUG_SENSOR) Slog.d(TAG, "updateTopPackage: " + packageName);
            mTopPackageName = packageName;
            mTopPackageChangedTime = SystemClock.uptimeMillis();
        }
    }

    private void observeSettings() {
        final ContentResolver resolver = getContext().getContentResolver();
        resolver.registerContentObserver(Settings.System.getUriFor(
                SHAKE_SENSORS_BLACKLIST_CONFIG), false, mSettingsObserver,
                UserHandle.USER_ALL);
    }

    private void registerTaskStackListener() {
        mActivityTaskManagerInternal = LocalServices.getService(ActivityTaskManagerInternal.class);
        if (mActivityTaskManagerInternal == null) {
            Slog.w(TAG, "Unable to register task stack listener. ActivityTaskManagerInternal is null");
            return;
        }
        try {
            mActivityTaskManagerInternal.registerTaskStackListener(mTaskStackListener);
        } catch (Exception e) {
            Slog.w(TAG, "Unable to register task stack listener", e);
        }
    }

    private String getPackageName(RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }
        if (taskInfo.topActivity != null) {
            return taskInfo.topActivity.getPackageName();
        }
        if (taskInfo.baseActivity != null) {
            return taskInfo.baseActivity.getPackageName();
        }
        return null;
    }

    private void loadConfigForUser(int userId) {
        if (userId == UserHandle.USER_CURRENT || userId == UserHandle.USER_CURRENT_OR_SELF) {
            userId = ActivityManager.getCurrentUser();
        }
        if (DEBUG_SENSOR) Slog.d(TAG, "loadConfigForUser: " + userId);
        final String raw = Settings.System.getStringForUser(
                getContext().getContentResolver(),
                SHAKE_SENSORS_BLACKLIST_CONFIG, userId);
        synchronized (mLock) {
            mShakeSensorsConfig.clear();
            if (!TextUtils.isEmpty(raw)) {
                final String[] configs = raw.split(";");
                for (String config : configs) {
                    final String[] entry = config.split("[:,]", 2);
                    if (entry.length != 2) {
                        continue;
                    }
                    final String packageName = entry[0];
                    if (TextUtils.isEmpty(packageName)) {
                        continue;
                    }
                    final int value;
                    try {
                        value = Integer.parseInt(entry[1]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (!isShakeSensorsConfigValid(value)) {
                        continue;
                    }
                    mShakeSensorsConfig.put(packageName, value);
                }
            }
        }
    }

    private void saveConfigLocked() {
        final long token = Binder.clearCallingIdentity();
        try {
            final StringBuilder sb = new StringBuilder();
            final Set<String> keys = mShakeSensorsConfig.keySet();
            for (String key : keys) {
                final int value = mShakeSensorsConfig.get(key);
                if (sb.length() > 0) {
                    sb.append(";");
                }
                sb.append(key).append(":").append(value);
            }
            Settings.System.putStringForUser(getContext().getContentResolver(),
                    SHAKE_SENSORS_BLACKLIST_CONFIG, sb.toString(),
                    mCurrentUserId);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    int getShakeSensorsConfigForPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return SHAKE_SENSORS_ALLOW;
        }
        synchronized (mLock) {
            final Integer config = mShakeSensorsConfig.get(packageName);
            return config != null ? config : SHAKE_SENSORS_ALLOW;
        }
    }

    void setShakeSensorsConfigForPackage(String packageName, int config) {
        if (TextUtils.isEmpty(packageName)) {
            Slog.e(TAG, "setShakeSensorsConfigForPackage: empty package name");
            return;
        }
        if (!isShakeSensorsConfigValid(config)) {
            Slog.e(TAG, "setShakeSensorsConfigForPackage: invalid config " + config
                    + " for " + packageName);
            return;
        }
        synchronized (mLock) {
            if (config == SHAKE_SENSORS_ALLOW) {
                mShakeSensorsConfig.remove(packageName);
            } else {
                mShakeSensorsConfig.put(packageName, config);
            }
            saveConfigLocked();
        }
    }

    boolean shouldBlockShakeSensorsNow(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        final int config;
        synchronized (mLock) {
            final Integer c = mShakeSensorsConfig.get(packageName);
            config = c != null ? c : SHAKE_SENSORS_ALLOW;
        }

        if (config == SHAKE_SENSORS_BLOCK_ALWAYS) {
            return true;
        }

        if (config == SHAKE_SENSORS_BLOCK_FIRST_SCREEN) {
            synchronized (mLock) {
                if (!packageName.equals(mTopPackageName)) {
                    return false;
                }
                final long now = SystemClock.uptimeMillis();
                final long elapsed = now - mTopPackageChangedTime;
                return elapsed <= APP_FIRST_SCREEN_MS;
            }
        }

        return false;
    }

    private class SensorBlockHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DELAYED_LOAD:
                    mCurrentUserId = ActivityManager.getCurrentUser();
                    loadConfigForUser(mCurrentUserId);
                    break;
                case MSG_TOP_PACKAGE_CHANGED:
                    handleTopPackageChanged((String) msg.obj);
                    break;
                case MSG_SETTINGS_CHANGED:
                    final int userId = msg.arg1;
                    if (userId == mCurrentUserId || userId == UserHandle.USER_ALL) {
                        loadConfigForUser(mCurrentUserId);
                    }
                    break;
            }
        }
    }

    private class IBinderService extends ISensorBlockService.Stub {
        @Override
        public int getShakeSensorsConfigForPackage(String packageName) {
            return SensorBlockController.this.getShakeSensorsConfigForPackage(packageName);
        }

        @Override
        public void setShakeSensorsConfigForPackage(String packageName, int config) {
            SensorBlockController.this.setShakeSensorsConfigForPackage(packageName, config);
        }

        @Override
        public boolean shouldBlockShakeSensorsNow(String packageName) {
            return SensorBlockController.this.shouldBlockShakeSensorsNow(packageName);
        }
    }
}
