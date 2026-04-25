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

package org.avium.systemui.lockscreen.util;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewRootImpl;
import android.view.WindowManagerGlobal;
import android.window.ScreenCaptureInternal;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;

import com.android.systemui.Dependency;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.StatusBarState;

import org.avium.systemui.depthwallpaper.DepthWallpaperSwitch;
import org.avium.systemui.depthwallpaper.DepthWallpaperSetup;
import org.avium.systemui.depthwallpaper.OccludingMaskLayout;

public class GlassClockManager implements StatusBarStateController.StateListener {

    private static final String TAG = "GlassClockManager";
    private static final String CUSTOM_WALLPAPER_DIR = "/data/system/avium";
    private static final String CUSTOM_WALLPAPER_FILE = "wallpaper";
    private static final String CUSTOM_WALLPAPER_PATH = CUSTOM_WALLPAPER_DIR + "/" + CUSTOM_WALLPAPER_FILE;
    private static final int LIVE_BACKDROP_BLUR_RADIUS_DP = 28;
    private static final long LIVE_CAPTURE_INTERVAL_MS = 80;

    private final Context mContext;
    private Bitmap mBlurredWallpaperBitmap;
    private Bitmap mLiveBackdropBitmap;
    private final Rect mLiveBackdropBounds = new Rect();
    private final DigitView[] mDigitViews;
    private final int[] mDigitResources;
    private DigitView mDotView;
    private int mDotResource;
    private boolean mUseLiveBackdrop = true;
    private boolean mLiveCaptureInFlight;
    private boolean mSuppressDigitDraw;
    private boolean mDestroyed;
    private boolean mIsDozing;
    private boolean mIsOnKeyguard;
    private int mCaptureGeneration;
    private long mLastLiveCaptureUptime;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread mCaptureThread = new HandlerThread("GlassClockCapture");
    private final Handler mCaptureHandler;
    
    private StatusBarStateController mStatusBarStateController;
    private FileObserver mFileObserver;
    private BroadcastReceiver mWallpaperReceiver;

    public GlassClockManager(Context context, int numDigits, int[] digitResources) {
        this.mContext = context;
        mCaptureThread.start();
        mCaptureHandler = new Handler(mCaptureThread.getLooper());
        this.mDigitResources = digitResources;

        this.mDigitViews = new DigitView[numDigits];
        for (int i = 0; i < numDigits; i++) {
            mDigitViews[i] = new DigitView(context);
        }

        initListeners();
    }

    private void initListeners() {
        mFileObserver = new FileObserver(CUSTOM_WALLPAPER_DIR, FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
            @Override
            public void onEvent(int event, @Nullable String path) {
                if (path != null && path.equals(CUSTOM_WALLPAPER_FILE)) {
                    Log.d(TAG, "Detected custom wallpaper file change.");
                    if (DepthWallpaperSwitch.INSTANCE.isEnabled(mContext)) {
                        Log.d(TAG, "Depth feature enabled, enforcing system lock wallpaper sync...");
                        DepthWallpaperSetup.INSTANCE.applyIfNeeded(mContext);
                    }
                    mMainHandler.postDelayed(() -> {
                        prepareWallpaper();
                    }, 200);
                }
            }
        };
        mFileObserver.startWatching();

        mWallpaperReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                prepareWallpaper();
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
        mContext.registerReceiver(mWallpaperReceiver, filter);

        try {
            mStatusBarStateController = Dependency.get(StatusBarStateController.class);
            mStatusBarStateController.addCallback(this);
            mIsDozing = mStatusBarStateController.isDozing()
                    || mStatusBarStateController.getDozeAmount() > 0.01f;
            mIsOnKeyguard = mStatusBarStateController.getState() == StatusBarState.KEYGUARD;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to register doze listener", t);
        }
    }

    public void setDotResource(int dotResource) {
        this.mDotResource = dotResource;
        this.mDotView = new DigitView(mContext);
        this.mDotView.setDigitDrawable(ContextCompat.getDrawable(mContext, mDotResource));
    }

    public View[] getDigitViews() {
        return mDigitViews;
    }

    public View getDotView() {
        return mDotView;
    }

    public synchronized void prepareWallpaper() {
        if (mUseLiveBackdrop) {
            return;
        }

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
        Bitmap wallpaperBitmap = null;
        try {
            if (DepthWallpaperSwitch.INSTANCE.isEnabled(mContext)) {
                File file = new File(CUSTOM_WALLPAPER_PATH);
                if (file.exists() && file.canRead()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    wallpaperBitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                    if (wallpaperBitmap != null) {
                        Log.d(TAG, "Loaded custom depth wallpaper for glass clock.");
                    }
                }
            }
        } catch (Exception e) {
           //ntd
        }
        if (wallpaperBitmap == null) {
            try (ParcelFileDescriptor pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_LOCK)) {
                if (pfd != null) {
                    wallpaperBitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                }
            } catch (IOException | SecurityException e) {
                // do nothing 
            }
        }

        if (wallpaperBitmap == null) {
            try (ParcelFileDescriptor pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)) {
                if (pfd != null) {
                    wallpaperBitmap = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                }
            } catch (IOException | SecurityException e) {
                //do nothing
            }
        }

        if (wallpaperBitmap == null) {
            Drawable wallpaperDrawable = wallpaperManager.getDrawable();
            if (wallpaperDrawable != null) {
                if (wallpaperDrawable instanceof BitmapDrawable) {
                    wallpaperBitmap = ((BitmapDrawable) wallpaperDrawable).getBitmap();
                } else {
                    int width = wallpaperDrawable.getIntrinsicWidth();
                    int height = wallpaperDrawable.getIntrinsicHeight();
                    if (width <= 0 || height <= 0) {
                        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
                        width = displayMetrics.widthPixels;
                        height = displayMetrics.heightPixels;
                    }

                    try {
                        wallpaperBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(wallpaperBitmap);
                        wallpaperDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        wallpaperDrawable.draw(canvas);
                    } catch (Exception e) {
                        //do nothing
                    }
                }
            }
        }

        if (wallpaperBitmap != null) {
            Bitmap newBlurred = blurBitmap(wallpaperBitmap, 25f);
            
            final Bitmap finalBlurred = newBlurred;
            mMainHandler.post(() -> {
                if (mBlurredWallpaperBitmap != null && !mBlurredWallpaperBitmap.isRecycled()) {
                    mBlurredWallpaperBitmap.recycle();
                }
                mBlurredWallpaperBitmap = finalBlurred;
                invalidateDigitViews();
            });
        }
    }

    private void invalidateDigitViews() {
        for (DigitView digitView : mDigitViews) {
            digitView.invalidate();
        }
        if (mDotView != null) {
            mDotView.invalidate();
        }
    }

    public void updateTime(String timeString) {
        if (timeString == null || timeString.length() != 4 || mDigitViews.length != 4) return;

        int h1 = Character.getNumericValue(timeString.charAt(0));
        int h2 = Character.getNumericValue(timeString.charAt(1));
        int m1 = Character.getNumericValue(timeString.charAt(2));
        int m2 = Character.getNumericValue(timeString.charAt(3));

        mDigitViews[0].setDigitDrawable(ContextCompat.getDrawable(mContext, mDigitResources[h1]));
        mDigitViews[1].setDigitDrawable(ContextCompat.getDrawable(mContext, mDigitResources[h2]));
        mDigitViews[2].setDigitDrawable(ContextCompat.getDrawable(mContext, mDigitResources[m1]));
        mDigitViews[3].setDigitDrawable(ContextCompat.getDrawable(mContext, mDigitResources[m2]));
    }

    public void cleanup() {
        if (mWallpaperReceiver != null) {
            try {
                mContext.unregisterReceiver(mWallpaperReceiver);
            } catch (Exception e) { }
            mWallpaperReceiver = null;
        }

        if (mFileObserver != null) {
            mFileObserver.stopWatching();
            mFileObserver = null;
        }

        if (mStatusBarStateController != null) {
            mStatusBarStateController.removeCallback(this);
            mStatusBarStateController = null;
        }

        mDestroyed = true;
        mCaptureGeneration++;
        mCaptureHandler.removeCallbacksAndMessages(null);
        mCaptureThread.quitSafely();

        if (mBlurredWallpaperBitmap != null && !mBlurredWallpaperBitmap.isRecycled()) {
            mBlurredWallpaperBitmap.recycle();
        }
        mBlurredWallpaperBitmap = null;
        if (mLiveBackdropBitmap != null && !mLiveBackdropBitmap.isRecycled()) {
            mLiveBackdropBitmap.recycle();
        }
        mLiveBackdropBitmap = null;
        
        mMainHandler.removeCallbacksAndMessages(null);
    }

    private void requestLiveBackdropCapture() {
        if (!mUseLiveBackdrop || mDestroyed || !mIsOnKeyguard || mIsDozing
                || mLiveCaptureInFlight) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - mLastLiveCaptureUptime < LIVE_CAPTURE_INTERVAL_MS) {
            return;
        }

        int padding = dpToPx(LIVE_BACKDROP_BLUR_RADIUS_DP);
        Rect captureBounds = getClockBoundsOnScreen(padding);
        if (captureBounds == null || captureBounds.isEmpty()) {
            return;
        }

        mLastLiveCaptureUptime = now;
        mLiveCaptureInFlight = true;
        int captureGeneration = mCaptureGeneration;

        View depthRoot = findDepthBackdropRoot();
        if (depthRoot != null) {
            Bitmap source = renderViewBackdrop(depthRoot, captureBounds);
            blurLiveBackdropAsync(source, captureBounds, captureGeneration);
            return;
        }

        int displayId = getDisplayId();
        SurfaceControl excludeLayer = copyRootSurfaceControl();
        mCaptureHandler.post(() -> {
            Bitmap source = captureDisplayBackdrop(displayId, captureBounds, excludeLayer);
            blurLiveBackdropAsync(source, captureBounds, captureGeneration);
        });
    }

    @Nullable
    private Rect getClockBoundsOnScreen(int padding) {
        Rect out = new Rect();
        boolean hasBounds = false;

        for (DigitView digitView : mDigitViews) {
            hasBounds |= unionViewBounds(out, digitView);
        }
        hasBounds |= unionViewBounds(out, mDotView);

        if (!hasBounds) {
            return null;
        }

        out.inset(-padding, -padding);
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        if (!out.intersect(0, 0, dm.widthPixels, dm.heightPixels)) {
            return null;
        }
        return out;
    }

    private boolean unionViewBounds(Rect out, @Nullable View view) {
        if (view == null || !view.isAttachedToWindow() || view.getWidth() <= 0
                || view.getHeight() <= 0 || view.getVisibility() != View.VISIBLE) {
            return false;
        }

        int[] location = new int[2];
        view.getLocationOnScreen(location);
        Rect viewBounds = new Rect(location[0], location[1],
                location[0] + view.getWidth(), location[1] + view.getHeight());
        if (out.isEmpty()) {
            out.set(viewBounds);
        } else {
            out.union(viewBounds);
        }
        return true;
    }

    @Nullable
    private View findDepthBackdropRoot() {
        if (!DepthWallpaperSwitch.INSTANCE.isEnabled(mContext)) {
            return null;
        }

        View view = firstAttachedDigitView();
        while (view != null) {
            if (view instanceof OccludingMaskLayout) {
                return view;
            }
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    @Nullable
    private DigitView firstAttachedDigitView() {
        for (DigitView digitView : mDigitViews) {
            if (digitView.isAttachedToWindow()) {
                return digitView;
            }
        }
        return mDotView != null && mDotView.isAttachedToWindow() ? mDotView : null;
    }

    @Nullable
    private Bitmap renderViewBackdrop(View root, Rect captureBounds) {
        if (captureBounds.width() <= 0 || captureBounds.height() <= 0) {
            return null;
        }

        try {
            Bitmap bitmap = Bitmap.createBitmap(captureBounds.width(), captureBounds.height(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            int[] rootLocation = new int[2];
            root.getLocationOnScreen(rootLocation);
            canvas.translate(rootLocation[0] - captureBounds.left,
                    rootLocation[1] - captureBounds.top);
            mSuppressDigitDraw = true;
            root.draw(canvas);
            return bitmap;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to render live backdrop from view hierarchy", t);
            return null;
        } finally {
            mSuppressDigitDraw = false;
        }
    }

    private int getDisplayId() {
        DigitView digitView = firstAttachedDigitView();
        Display display = digitView != null ? digitView.getDisplay() : null;
        return display != null ? display.getDisplayId() : Display.DEFAULT_DISPLAY;
    }

    @Nullable
    private SurfaceControl copyRootSurfaceControl() {
        DigitView digitView = firstAttachedDigitView();
        if (digitView == null) {
            return null;
        }

        ViewRootImpl viewRoot = digitView.getViewRootImpl();
        if (viewRoot == null || !viewRoot.getSurfaceControl().isValid()) {
            return null;
        }

        return new SurfaceControl(viewRoot.getSurfaceControl(), TAG);
    }

    @Nullable
    private Bitmap captureDisplayBackdrop(int displayId, Rect captureBounds,
            @Nullable SurfaceControl excludeLayer) {
        ScreenCaptureInternal.CaptureArgs captureArgs = null;
        try {
            ScreenCaptureInternal.CaptureArgs.Builder builder =
                    new ScreenCaptureInternal.CaptureArgs.Builder();
            builder.setSourceCrop(captureBounds);
            builder.setPreserveDisplayColors(true);
            if (excludeLayer != null && excludeLayer.isValid()) {
                builder.setExcludeLayers(new SurfaceControl[] { excludeLayer });
            }
            captureArgs = builder.build();

            ScreenCaptureInternal.SynchronousScreenCaptureListener listener =
                    ScreenCaptureInternal.createSyncCaptureListener();
            WindowManagerGlobal.getWindowManagerService().captureDisplay(
                    displayId, captureArgs, listener);
            ScreenCaptureInternal.ScreenshotHardwareBuffer buffer = listener.getBuffer();
            if (buffer == null || buffer.containsSecureLayers()) {
                return null;
            }

            Bitmap bitmap = buffer.asBitmap();
            if (bitmap == null) {
                return null;
            }
            return bitmap.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to capture live backdrop", e);
            return null;
        } catch (Throwable t) {
            Log.w(TAG, "Unexpected live backdrop capture failure", t);
            return null;
        } finally {
            if (captureArgs != null) {
                captureArgs.release();
            } else if (excludeLayer != null) {
                excludeLayer.release();
            }
        }
    }

    private void blurLiveBackdropAsync(@Nullable Bitmap source, Rect captureBounds,
            int captureGeneration) {
        if (source == null || source.isRecycled()) {
            mMainHandler.post(() -> {
                if (captureGeneration == mCaptureGeneration) {
                    mLiveCaptureInFlight = false;
                }
            });
            return;
        }

        mCaptureHandler.post(() -> {
            Bitmap blurred = blurBitmap(source, 25f);
            if (blurred != source && !source.isRecycled()) {
                source.recycle();
            }

            Rect finalBounds = new Rect(captureBounds);
            mMainHandler.post(() -> {
                boolean currentGeneration = captureGeneration == mCaptureGeneration;
                if (currentGeneration) {
                    mLiveCaptureInFlight = false;
                }
                if (mDestroyed || !mIsOnKeyguard || mIsDozing || !currentGeneration
                        || blurred == null || blurred.isRecycled()) {
                    if (blurred != null && !blurred.isRecycled()) {
                        blurred.recycle();
                    }
                    return;
                }
                if (mLiveBackdropBitmap != null && !mLiveBackdropBitmap.isRecycled()) {
                    mLiveBackdropBitmap.recycle();
                }
                mLiveBackdropBitmap = blurred;
                mLiveBackdropBounds.set(finalBounds);
                invalidateDigitViews();
            });
        });
    }

    @Override
    public void onDozeAmountChanged(float linear, float eased) {
        handleDozeChanged(eased > 0.01f || (mStatusBarStateController != null
                && mStatusBarStateController.isDozing()));
    }

    @Override
    public void onUpcomingStateChanged(int upcomingState) {
        if (upcomingState != StatusBarState.KEYGUARD) {
            handleKeyguardChanged(false);
        }
    }

    @Override
    public void onStateChanged(int newState) {
        handleKeyguardChanged(newState == StatusBarState.KEYGUARD);
    }

    @Override
    public void onDozingChanged(boolean isDozing) {
        handleDozeChanged(isDozing || (mStatusBarStateController != null
                && mStatusBarStateController.getDozeAmount() > 0.01f));
    }

    private void handleKeyguardChanged(boolean isOnKeyguard) {
        if (mIsOnKeyguard == isOnKeyguard) {
            return;
        }

        mIsOnKeyguard = isOnKeyguard;
        mCaptureGeneration++;
        mLiveCaptureInFlight = false;
        mLastLiveCaptureUptime = 0L;

        if (mIsOnKeyguard && !mIsDozing) {
            mMainHandler.postDelayed(() -> {
                requestLiveBackdropCapture();
                invalidateDigitViews();
            }, LIVE_CAPTURE_INTERVAL_MS);
        }
    }

    private void handleDozeChanged(boolean isDozing) {
        if (mIsDozing == isDozing) {
            return;
        }

        mIsDozing = isDozing;
        mCaptureGeneration++;
        mLiveCaptureInFlight = false;
        mLastLiveCaptureUptime = 0L;

        if (!mIsDozing && mIsOnKeyguard) {
            mMainHandler.postDelayed(() -> {
                requestLiveBackdropCapture();
                invalidateDigitViews();
            }, LIVE_CAPTURE_INTERVAL_MS);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density + 0.5f);
    }

    private Bitmap blurBitmap(Bitmap input, float radius) {
        if (input == null || input.isRecycled()) {
            return null;
        }

        try {
            float scaleFactor = 5.0f / radius;
            int newWidth = Math.max(1, (int)(input.getWidth() * scaleFactor));
            int newHeight = Math.max(1, (int)(input.getHeight() * scaleFactor));
            Bitmap smallBitmap = Bitmap.createScaledBitmap(input, newWidth, newHeight, true);
            RenderScript rs = RenderScript.create(mContext);
            Allocation input_alloc = Allocation.createFromBitmap(rs, smallBitmap);
            Allocation output_alloc = Allocation.createTyped(rs, input_alloc.getType());
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            script.setInput(input_alloc);
            script.setRadius(25.0f);
            script.forEach(output_alloc);
            Bitmap blurred = Bitmap.createBitmap(smallBitmap.getWidth(), smallBitmap.getHeight(), smallBitmap.getConfig());
            output_alloc.copyTo(blurred);
            input_alloc.destroy();
            output_alloc.destroy();
            script.destroy();
            rs.destroy();
            return blurred; 
        } catch (Exception e) {
            return input;
        }
    }

    public class DigitView extends View {
        private Drawable mDigitDrawable;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mXfermodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix mDrawMatrix = new Matrix();

        private Bitmap mMaskBitmap;
        private Canvas mMaskCanvas;

        public DigitView(Context context) {
            super(context);
            mXfermodePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            mPaint.setFilterBitmap(true);
        }

        public void setDigitDrawable(@Nullable Drawable digitDrawable) {
            this.mDigitDrawable = digitDrawable;
            updateMask();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0 && h > 0) {
                if (mMaskBitmap != null && !mMaskBitmap.isRecycled()) {
                    mMaskBitmap.recycle();
                }
                mMaskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                mMaskCanvas = new Canvas(mMaskBitmap);
                updateMask();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            requestLiveBackdropCapture();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
        }

        private void updateMask() {
            if (mMaskCanvas != null && mDigitDrawable != null) {
                mMaskCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
                mDigitDrawable.setBounds(0, 0, getWidth(), getHeight());
                mDigitDrawable.draw(mMaskCanvas);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mSuppressDigitDraw) {
                return;
            }
            if (mDigitDrawable == null || mMaskBitmap == null) {
                return;
            }

            if (mUseLiveBackdrop && drawLiveBackdrop(canvas)) {
                return;
            }

            Bitmap wallpaper = GlassClockManager.this.mBlurredWallpaperBitmap;

            if (wallpaper == null || wallpaper.isRecycled()) {
                return;
            }

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenW = dm.widthPixels;
            int screenH = dm.heightPixels;

            int bmpW = wallpaper.getWidth();
            int bmpH = wallpaper.getHeight();

            float scale = Math.max((float) screenW / bmpW, (float) screenH / bmpH);
            float dx = (screenW - bmpW * scale) * 0.5f;
            float dy = (screenH - bmpH * scale) * 0.5f;

            int[] location = new int[2];
            getLocationOnScreen(location);
            int vx = location[0];
            int vy = location[1];

            mDrawMatrix.reset();
            mDrawMatrix.postScale(scale, scale);
            mDrawMatrix.postTranslate(dx, dy);
            mDrawMatrix.postTranslate(-vx, -vy);

            int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            canvas.drawBitmap(wallpaper, mDrawMatrix, mPaint);
            canvas.drawBitmap(mMaskBitmap, 0, 0, mXfermodePaint);

            canvas.restoreToCount(saveCount);
        }

        private boolean drawLiveBackdrop(Canvas canvas) {
            requestLiveBackdropCapture();

            Bitmap backdrop = mLiveBackdropBitmap;
            if (backdrop == null || backdrop.isRecycled() || mLiveBackdropBounds.isEmpty()) {
                return false;
            }

            int[] location = new int[2];
            getLocationOnScreen(location);

            float scaleX = (float) mLiveBackdropBounds.width() / backdrop.getWidth();
            float scaleY = (float) mLiveBackdropBounds.height() / backdrop.getHeight();

            mDrawMatrix.reset();
            mDrawMatrix.postScale(scaleX, scaleY);
            mDrawMatrix.postTranslate(mLiveBackdropBounds.left - location[0],
                    mLiveBackdropBounds.top - location[1]);

            int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            canvas.drawBitmap(backdrop, mDrawMatrix, mPaint);
            canvas.drawBitmap(mMaskBitmap, 0, 0, mXfermodePaint);
            canvas.restoreToCount(saveCount);
            return true;
        }
    }
}
