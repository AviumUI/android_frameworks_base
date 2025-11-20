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
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;

import org.avium.systemui.depthwallpaper.DepthWallpaperSwitch;
import org.avium.systemui.depthwallpaper.DepthWallpaperSetup;

public class GlassClockManager {

    private static final String TAG = "GlassClockManager";
    private static final String CUSTOM_WALLPAPER_DIR = "/data/system/avium";
    private static final String CUSTOM_WALLPAPER_FILE = "wallpaper";
    private static final String CUSTOM_WALLPAPER_PATH = CUSTOM_WALLPAPER_DIR + "/" + CUSTOM_WALLPAPER_FILE;

    private final Context mContext;
    private Bitmap mBlurredWallpaperBitmap;
    private final DigitView[] mDigitViews;
    private final int[] mDigitResources;
    private DigitView mDotView;
    private int mDotResource;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    
    private FileObserver mFileObserver;
    private BroadcastReceiver mWallpaperReceiver;

    public GlassClockManager(Context context, int numDigits, int[] digitResources) {
        this.mContext = context;
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
                
                for (DigitView digitView : mDigitViews) {
                    digitView.invalidate();
                }
                if (mDotView != null) {
                    mDotView.invalidate();
                }
            });
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

        if (mBlurredWallpaperBitmap != null && !mBlurredWallpaperBitmap.isRecycled()) {
            mBlurredWallpaperBitmap.recycle();
        }
        mBlurredWallpaperBitmap = null;
        
        mMainHandler.removeCallbacksAndMessages(null);
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
            Bitmap wallpaper = GlassClockManager.this.mBlurredWallpaperBitmap;

            if (wallpaper == null || mDigitDrawable == null || mMaskBitmap == null || wallpaper.isRecycled()) {
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
            RectF viewRect = new RectF(0, 0, getWidth(), getHeight());

            canvas.drawBitmap(wallpaper, mDrawMatrix, mPaint);

            Paint maskPaint = new Paint();
            maskPaint.setColor(Color.WHITE);
            maskPaint.setAlpha(50);
            canvas.drawRect(viewRect, maskPaint);
            canvas.drawBitmap(mMaskBitmap, 0, 0, mXfermodePaint);

            canvas.restoreToCount(saveCount);
        }
    }
}