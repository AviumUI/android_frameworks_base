/*
 * Copyright (C) 2026 The AviumUI Project
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

package com.android.systemui.smartsidebar;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dump.DumpManager;

import org.avium.smartsidebar.ISmartSidebar;
import org.avium.smartsidebar.ISmartSidebarHost;
import org.avium.smartsidebar.SmartSidebarConfig;
import org.avium.smartsidebar.SmartSidebarContract;

import com.android.internal.logging.InstanceId;
import com.android.wm.shell.shared.split.SplitScreenConstants;
import com.android.wm.shell.splitscreen.SplitScreen;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import lineageos.providers.LineageSettings;

/**
 * SystemUI side bridge to the smart sidebar application.
 *
 * <p>Responsibilities:
 * <ul>
 *     <li>bind / unbind the {@code org.avium.smartsidebar.SidebarService};</li>
 *     <li>push the environment (allowed, foreground package, recents, user) to it;</li>
 *     <li>receive {@link #openFromGesture(boolean, float)} from the edge long swipe and forward it;
 *     </li>
 *     <li>perform the operations that only SystemUI can do (split screen).</li>
 * </ul>
 *
 * <p>Threading: public methods may be called from any thread, everything is posted to the main
 * thread and all Binder calls to the sidebar app are executed on a dedicated single thread so that
 * SystemUI is never blocked on the app.
 */
@SysUISingleton
public class SmartSidebarManager implements CoreStartable {

    private static final String TAG = "SmartSidebar";
    private static final boolean DEBUG = true;

    /** Safety net against the PhoneWindowManager fallback broadcast + gesture double trigger. */
    private static final long OPEN_DEBOUNCE_MS = 800;

    /** Delay before we try to re-bind a sidebar service that went away. */
    private static final long REBIND_DELAY_MS = 3000;

    private static final int MAX_RECENT_PACKAGES = 12;

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(r -> {
        final Thread thread = new Thread(r, "smart-sidebar-binder");
        thread.setDaemon(true);
        return thread;
    });

    private final ArrayDeque<String> mRecentPackages = new ArrayDeque<>();

    private ISmartSidebar mService;
    private boolean mBinding;
    private boolean mBound;
    private String mForegroundPackage;
    private long mLastOpenUptime;
    private boolean mStarted;
    private final Optional<SplitScreen> mSplitScreen;
    /** Task that currently owns the screen, used as the other half of a split. */
    private int mTopTaskId = ActivityTaskManager.INVALID_TASK_ID;
    private int mTopTaskUserId = UserHandle.USER_SYSTEM;

    /** Pending open request when the very first gesture arrives before the service is bound. */
    private boolean mPendingOpen;
    private boolean mPendingLeft;
    private float mPendingY;

    private final ISmartSidebarHost mHost = new ISmartSidebarHost.Stub() {
        @Override
        public void onPanelShown(boolean left) {
            if (DEBUG) Log.d(TAG, "panel shown, left=" + left);
        }

        @Override
        public void onPanelHidden() {
            if (DEBUG) Log.d(TAG, "panel hidden");
        }

        @Override
        public void onRequestMiniWindow(ComponentName component, Rect bounds, int userId) {
            mMainHandler.post(() -> launchMiniWindow(component, bounds, userId));
        }

        @Override
        public void onRequestSplit(ComponentName component, int side) {
            mMainHandler.post(() -> requestSplit(component, side));
        }

        @Override
        public void onError(int code, String message) {
            Log.w(TAG, "error " + code + ": " + message);
        }
    };

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, android.os.IBinder binder) {
            if (DEBUG) Log.d(TAG, "sidebar service connected: " + name);
            mBinding = false;
            mBound = true;
            mService = ISmartSidebar.Stub.asInterface(binder);
            callService(service -> service.attachHost(mHost));
            pushEnvironment();
            if (mPendingOpen) {
                mPendingOpen = false;
                final boolean left = mPendingLeft;
                final float y = mPendingY;
                callService(service -> service.openFromGesture(left, y));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "sidebar service disconnected: " + name);
            mService = null;
            mBound = false;
            mBinding = false;
            // The system usually reconnects automatically because the binding is still alive,
            // but not when the app was force stopped: retry on our own later, lazily.
            mMainHandler.removeCallbacks(mRebindRunnable);
            mMainHandler.postDelayed(mRebindRunnable, REBIND_DELAY_MS);
        }
    };

    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
            final ComponentName top = taskInfo.topActivity != null
                    ? taskInfo.topActivity : taskInfo.baseActivity;
            if (top == null) {
                return;
            }
            final int taskId = taskInfo.taskId;
            final boolean fullscreen = taskInfo.getWindowingMode()
                    == android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
            final int userId = taskInfo.userId;
            mMainHandler.post(() -> {
                if (fullscreen) {
                    mTopTaskId = taskId;
                    mTopTaskUserId = userId;
                }
                onForegroundChanged(top.getPackageName());
            });
        }
    };

    private final Runnable mRebindRunnable = () -> {
        if (isEnabled() && mService == null) {
            if (DEBUG) Log.d(TAG, "rebinding sidebar service");
            ensureBound();
        }
    };

    private final BroadcastReceiver mFallbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!SmartSidebarContract.ACTION_OPEN_FROM_GESTURE.equals(intent.getAction())) {
                return;
            }
            final boolean left = intent.getBooleanExtra(SmartSidebarContract.EXTRA_LEFT, false);
            final float y = intent.getFloatExtra(SmartSidebarContract.EXTRA_Y, 0f);
            if (DEBUG) Log.d(TAG, "fallback open request, left=" + left);
            openFromGesture(left, y);
        }
    };

    private final BroadcastReceiver mUserSwitchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "user switched, rebinding");
            mMainHandler.post(() -> {
                unbind();
                pushEnvironment();
                ensureBound();
            });
        }
    };

    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pushEnvironment();
        }
    };

    @Inject
    public SmartSidebarManager(
            Context context,
            DumpManager dumpManager,
            Optional<SplitScreen> splitScreen) {
        mContext = context;
        mSplitScreen = splitScreen;
        dumpManager.registerCriticalDumpable(TAG, this);
    }

    @Override
    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;
        registerTaskStackListener();
        registerReceivers();
        if (isEnabled()) {
            ensureBound();
        }
        if (DEBUG) Log.d(TAG, "SmartSidebarManager started");
    }

    // ------------------------------------------------------------------------------------
    // Called by EdgeBackGestureHandler
    // ------------------------------------------------------------------------------------

    /**
     * Opens the panel on the given side. Safe to call from any thread; the panel is opened on the
     * side the gesture started from.
     */
    public void openFromGesture(boolean left, float y) {
        if (!isEnabled()) {
            if (DEBUG) Log.d(TAG, "openFromGesture: disabled");
            return;
        }
        mMainHandler.post(() -> {
            final long now = SystemClock.uptimeMillis();
            if (now - mLastOpenUptime < OPEN_DEBOUNCE_MS) {
                if (DEBUG) Log.d(TAG, "openFromGesture: debounced");
                return;
            }
            mLastOpenUptime = now;
            if (!isAllowedNow()) {
                if (DEBUG) Log.d(TAG, "openFromGesture: not allowed now");
                return;
            }
            ensureBound();
            if (mService == null) {
                mPendingOpen = true;
                mPendingLeft = left;
                mPendingY = y;
                if (DEBUG) Log.d(TAG, "openFromGesture: pending, service not bound yet");
                return;
            }
            callService(service -> service.openFromGesture(left, y));
        });
    }

    public boolean isEnabled() {
        return SmartSidebarConfig.isEnabled();
    }

    /** Whether the given edge is allowed to open the panel for the current user. */
    public boolean allowsEdge(boolean left) {
        final ContentResolver cr = mContext.getContentResolver();
        final int userId = currentUserId();
        return left ? SmartSidebarConfig.allowsLeftEdge(cr, userId)
                : SmartSidebarConfig.allowsRightEdge(cr, userId);
    }

    // ------------------------------------------------------------------------------------
    // Environment
    // ------------------------------------------------------------------------------------

    private void onForegroundChanged(String packageName) {
        mForegroundPackage = packageName;
        mRecentPackages.remove(packageName);
        mRecentPackages.addFirst(packageName);
        while (mRecentPackages.size() > MAX_RECENT_PACKAGES) {
            mRecentPackages.removeLast();
        }
        pushEnvironment();
    }

    private void pushEnvironment() {
        if (!mStarted) {
            return;
        }
        final boolean allowed = isAllowedNow();
        final String foreground = mForegroundPackage;
        final String[] recents = mRecentPackages.toArray(new String[0]);
        final int userId = currentUserId();
        if (DEBUG) {
            Log.d(TAG, "pushEnvironment: allowed=" + allowed + " fg=" + foreground
                    + " user=" + userId);
        }
        if (mService == null) {
            return;
        }
        callService(service -> service.updateEnvironment(allowed, foreground, recents, userId));
    }

    private boolean isAllowedNow() {
        if (!isEnabled()) {
            return false;
        }
        final ContentResolver cr = mContext.getContentResolver();
        final int userId = currentUserId();
        final boolean landscape = mContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (landscape && !SmartSidebarConfig.isLandscapeEnabled(cr, userId)) {
            return false;
        }
        final boolean locked = mContext.getSystemService(KeyguardManager.class)
                .isKeyguardLocked();
        if (locked) {
            return false;
        }
        return true;
    }

    private int currentUserId() {
        return ActivityManager.getCurrentUser();
    }

    // ------------------------------------------------------------------------------------
    // Binding
    // ------------------------------------------------------------------------------------

    private void ensureBound() {
        if (mService != null || mBinding) {
            return;
        }
        mBinding = true;
        final Intent intent = new Intent().setComponent(SmartSidebarContract.SERVICE_SIDEBAR);
        try {
            if (DEBUG) Log.d(TAG, "binding sidebar service for user " + currentUserId());
            mContext.bindServiceAsUser(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                    UserHandle.of(currentUserId()));
        } catch (Exception e) {
            Log.e(TAG, "Unable to bind sidebar service", e);
            mBinding = false;
            mBound = false;
            mService = null;
        }
    }

    private void unbind() {
        if (!mBound && !mBinding) {
            return;
        }
        try {
            mContext.unbindService(mConnection);
        } catch (Exception e) {
            Log.w(TAG, "unbindService failed", e);
        }
        mBound = false;
        mBinding = false;
        mService = null;
    }

    // ------------------------------------------------------------------------------------
    // Requests that only SystemUI can perform
    // ------------------------------------------------------------------------------------

    private void launchMiniWindow(ComponentName component, Rect bounds, int userId) {
        // TODO(Phase 4): use ActivityOptions + LauncherApps from SystemUI when the app cannot.
        Log.w(TAG, "onRequestMiniWindow not implemented yet: " + component);
    }

    private void requestSplit(ComponentName component, int side) {
        if (component == null) {
            reportError(SmartSidebarContract.ERROR_APP_NOT_FOUND, "split: null component");
            return;
        }
        if (!mSplitScreen.isPresent()) {
            reportError(SmartSidebarContract.ERROR_SPLIT_UNSUPPORTED, "no SplitScreen");
            return;
        }
        if (mTopTaskId == ActivityTaskManager.INVALID_TASK_ID) {
            reportError(SmartSidebarContract.ERROR_SPLIT_UNSUPPORTED, "no fullscreen task");
            return;
        }

        final int userId = mTopTaskUserId;
        final int position = side == SmartSidebarContract.SPLIT_SIDE_LEFT
                ? SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
                : SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            final PendingIntent pendingIntent = PendingIntent.getActivityAsUser(
                    mContext, 0, intent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT,
                    null, UserHandle.of(userId));
            if (DEBUG) {
                Log.d(TAG, "requestSplit: " + component + " side=" + side
                        + " task=" + mTopTaskId + " user=" + userId);
            }
            mSplitScreen.get().startIntentAndTask(
                    pendingIntent,
                    userId,
                    ActivityOptions.makeBasic().toBundle(),
                    mTopTaskId,
                    null,
                    position,
                    SplitScreenConstants.SNAP_TO_2_50_50,
                    null,
                    InstanceId.fakeInstanceId(0));
        } catch (Exception e) {
            Log.e(TAG, "Unable to start split", e);
            reportError(SmartSidebarContract.ERROR_SPLIT_FAILED, String.valueOf(e));
        }
    }

    private void reportError(int code, String message) {
        Log.w(TAG, "error " + code + ": " + message);
        final ISmartSidebarHost host = mHost;
        mExecutor.execute(() -> {
            try {
                host.onError(code, message);
            } catch (Exception e) {
                Log.w(TAG, "Unable to report error", e);
            }
        });
    }

    // ------------------------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------------------------

    private void registerTaskStackListener() {
        try {
            ActivityTaskManager.getService().registerTaskStackListener(mTaskStackListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register task stack listener", e);
        }
    }

    private void registerReceivers() {
        final IntentFilter fallbackFilter = new IntentFilter(
                SmartSidebarContract.ACTION_OPEN_FROM_GESTURE);
        try {
            mContext.registerReceiver(mFallbackReceiver, fallbackFilter,
                    android.Manifest.permission.STATUS_BAR_SERVICE, mMainHandler,
                    Context.RECEIVER_EXPORTED);
        } catch (Exception e) {
            Log.e(TAG, "Unable to register fallback receiver", e);
        }
        try {
            mContext.registerReceiver(mUserSwitchReceiver,
                    new IntentFilter(Intent.ACTION_USER_SWITCHED));
            mContext.registerReceiver(mScreenStateReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_OFF));
            mContext.registerReceiver(mScreenStateReceiver,
                    new IntentFilter(Intent.ACTION_SCREEN_ON));
            mContext.registerReceiver(mScreenStateReceiver,
                    new IntentFilter(Intent.ACTION_USER_PRESENT));
        } catch (Exception e) {
            Log.e(TAG, "Unable to register receivers", e);
        }
    }

    private interface ServiceCall {
        void run(ISmartSidebar service) throws RemoteException;
    }

    private void callService(ServiceCall call) {
        final ISmartSidebar service = mService;
        if (service == null) {
            return;
        }
        mExecutor.execute(() -> {
            try {
                call.run(service);
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "Sidebar call failed", e);
            }
        });
    }

    /** Whether the edge long swipe is currently configured to open the sidebar. */
    public boolean isSidebarActionSelected() {
        final int action = LineageSettings.System.getIntForUser(
                mContext.getContentResolver(),
                LineageSettings.System.KEY_EDGE_LONG_SWIPE_ACTION,
                org.lineageos.internal.util.DeviceKeysConstants.Action.NOTHING.ordinal(),
                UserHandle.USER_CURRENT);
        return action == org.lineageos.internal.util.DeviceKeysConstants.Action.SIDEBAR.ordinal();
    }

    @Override
    public void dump(PrintWriter pw, String[] args) {
        pw.println("SmartSidebarManager:");
        pw.println("  enabled=" + isEnabled());
        pw.println("  allowedNow=" + isAllowedNow());
        pw.println("  sidebarAction=" + isSidebarActionSelected());
        pw.println("  bound=" + mBound + " service=" + (mService != null));
        pw.println("  foreground=" + mForegroundPackage);
        pw.println("  recents=" + new ArrayList<>(mRecentPackages));
        pw.println("  userId=" + currentUserId());
        pw.println("  pendingOpen=" + mPendingOpen);
        pw.println("  splitAvailable=" + mSplitScreen.isPresent());
        pw.println("  topTaskId=" + mTopTaskId + " (user " + mTopTaskUserId + ")");
    }
}
