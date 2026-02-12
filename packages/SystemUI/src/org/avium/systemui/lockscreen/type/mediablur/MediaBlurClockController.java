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

package org.avium.systemui.lockscreen.type.mediablur;

import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;

//import org.avium.aviumlockscreenstudio.R;
import com.android.systemui.res.R;
import org.avium.systemui.lockscreen.util.BaseLockscreenController;
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings;
import org.avium.systemui.lockscreen.util.GlassClockManager;
import org.avium.systemui.lockscreen.util.LockscreenClockUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaBlurClockController extends BaseLockscreenController implements MusicController.MusicStateListener, MediaSessionManager.OnActiveSessionsChangedListener {

    private static final String TAG = "MediaBlurClockCtrl";
    private static final boolean DEBUG_MODE = true;

    private static final String PREFS_NAME = "MediaBlurClockPrefs";
    private static final String KEY_BLURRED_WALLPAPER_ID = "blurred_wallpaper_id";

    private TextView mDateView;
    private TextView mTimeView;

    private FrameLayout mAlbumArtContainer;
    private ImageView mAlbumArtView;
    private TextView mSongTitleView;
    private TextView mSongArtistView;
    private View mPrevButton;
    private View mPlayPauseButton;
    private View mNextButton;
    private View mSeekBarBackground;
    private View mSeekBarProgress;

    private GlassClockManager mMediaControlsGlassManager;
    private MusicController mMusicController;
    private MediaSessionManager mMediaSessionManager;

    private WallpaperManager mWallpaperManager;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private final int[] mMediaIconResources = new int[] {
            R.drawable.avium_ic_media_previous,
            R.drawable.avium_ic_media_pause,
            R.drawable.avium_ic_media_next
    };

    @Override
    public View getView(Context context) {
        mContext = context;
        mWallpaperManager = WallpaperManager.getInstance(context);
        mMediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        createViews();
        setupLayout();
        if (!DEBUG_MODE) {
            mMediaControlsGlassManager.prepareWallpaper();
        }
        initializeListeners();
        registerMediaListener();
        applyBlurredWallpaperIfNeeded();
        initializeCommonViews();
        return mContainer;
    }

    private void applyBlurredWallpaperIfNeeded() {
        mExecutor.execute(() -> {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int lastBlurredId = prefs.getInt(KEY_BLURRED_WALLPAPER_ID, -1);
            int currentWallpaperId = mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK);

            if (currentWallpaperId > 0 && currentWallpaperId == lastBlurredId) {
                return;
            }

            Bitmap originalWallpaper = BlurUtils.getLockscreenWallpaper(mContext);
            if (originalWallpaper == null) {
                return;
            }
            
            Bitmap blurredWallpaper = BlurUtils.blur(mContext, originalWallpaper, 25f);
            if (blurredWallpaper == null) {
                originalWallpaper.recycle();
                return;
            }
            
            originalWallpaper.recycle();

            try {
                mWallpaperManager.setBitmap(blurredWallpaper, null, true, WallpaperManager.FLAG_LOCK);
                
                int newBlurredId = mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK);
                prefs.edit().putInt(KEY_BLURRED_WALLPAPER_ID, newBlurredId).apply();

            } catch (IOException e) {
                //ntd
            } finally {
                blurredWallpaper.recycle();
            }
        });
    }

    @Override
    protected void cleanup() {
        if (!DEBUG_MODE && mMediaControlsGlassManager != null) {
            mMediaControlsGlassManager.cleanup();
        }
        if (mMusicController != null) {
            mMusicController.cleanup();
        }
        unregisterMediaListener();
        mExecutor.shutdown();
        mContext = null;
        mContainer = null;
    }

    private void createViews() {
        mContainer = new ConstraintLayout(mContext);
        mContainer.setId(View.generateViewId());

        mDateView = createTextView(20f, false);
        mTimeView = createTextView(42f, true);
        mContainer.addView(mDateView);
        mContainer.addView(mTimeView);

        mAlbumArtContainer = new FrameLayout(mContext);
        mAlbumArtContainer.setId(View.generateViewId());
        mAlbumArtContainer.setBackgroundResource(R.drawable.media_album_art_background);
        mAlbumArtContainer.setClipToOutline(true);
        mAlbumArtContainer.setElevation(dpToPx(12));

        mAlbumArtView = new ImageView(mContext);
        mAlbumArtView.setId(View.generateViewId());
        mAlbumArtView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mAlbumArtView.setImageResource(R.drawable.default_album_art);
        mAlbumArtContainer.addView(mAlbumArtView, new FrameLayout.LayoutParams(
                dpToPx(260), dpToPx(260)));

        mSongTitleView = createTextView(22f, true);
        mSongTitleView.setText(mContext.getString(R.string.default_song_title));

        mSongArtistView = createTextView(14f, false);
        mSongArtistView.setText(mContext.getString(R.string.default_song_artist));
        mSongArtistView.setAlpha(0.8f);

        if (DEBUG_MODE) {
            mPrevButton = createIconNoBackground(R.drawable.avium_ic_media_previous);
            mPlayPauseButton = createIconNoBackground(R.drawable.avium_ic_media_pause);
            mNextButton = createIconNoBackground(R.drawable.avium_ic_media_next);
        } else {
            mMediaControlsGlassManager = new GlassClockManager(mContext, 3, mMediaIconResources);
            View[] mediaViews = mMediaControlsGlassManager.getDigitViews();
            
            for (int i = 0; i < mediaViews.length; i++) {
                if (mediaViews[i] instanceof GlassClockManager.DigitView) {
                    ((GlassClockManager.DigitView) mediaViews[i]).setDigitDrawable(
                            ContextCompat.getDrawable(mContext, mMediaIconResources[i])
                    );
                }
            }

            mPrevButton = mediaViews[0];
            mPlayPauseButton = mediaViews[1];
            mNextButton = mediaViews[2];
            for (View v : mediaViews) {
                v.setId(View.generateViewId());
                v.setAlpha(0.99f);
                v.setBackground(null);
                v.setPadding(0, 0, 0, 0);
            }
        }

        mSeekBarBackground = new View(mContext);
        mSeekBarBackground.setId(View.generateViewId());
        GradientDrawable seekBg = new GradientDrawable();
        seekBg.setCornerRadius(dpToPx(4));
        seekBg.setColor(Color.parseColor("#33FFFFFF"));
        mSeekBarBackground.setBackground(seekBg);

        mSeekBarProgress = new View(mContext);
        mSeekBarProgress.setId(View.generateViewId());
        GradientDrawable seekProgress = new GradientDrawable();
        seekProgress.setCornerRadius(dpToPx(4));
        seekProgress.setColor(Color.WHITE);
        mSeekBarProgress.setBackground(seekProgress);

        mContainer.addView(mAlbumArtContainer);
        mContainer.addView(mSongTitleView);
        mContainer.addView(mSongArtistView);
        mContainer.addView(mPrevButton);
        mContainer.addView(mPlayPauseButton);
        mContainer.addView(mNextButton);
        mContainer.addView(mSeekBarBackground);
        mContainer.addView(mSeekBarProgress);
    }

    private void initializeListeners() {
        mPrevButton.setOnClickListener(v -> {
            if (mMusicController != null) mMusicController.previousTrack();
        });
        mPlayPauseButton.setOnClickListener(v -> {
            if (mMusicController != null) mMusicController.playPause();
        });
        mNextButton.setOnClickListener(v -> {
            if (mMusicController != null) mMusicController.nextTrack();
        });
    }

    private void setupLayout() {
        ConstraintSet cs = new ConstraintSet();
        cs.clone((ConstraintLayout) mContainer);

        int dateId = mDateView.getId();
        int timeId = mTimeView.getId();
        int albumArtId = mAlbumArtContainer.getId();
        int titleId = mSongTitleView.getId();
        int artistId = mSongArtistView.getId();
        int playId = mPlayPauseButton.getId();
        int prevId = mPrevButton.getId();
        int nextId = mNextButton.getId();
        int seekBarBgId = mSeekBarBackground.getId();
        int seekBarProgressId = mSeekBarProgress.getId();

        cs.connect(dateId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dpToPx(80));
        cs.connect(dateId, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, dpToPx(36));
        cs.connect(timeId, ConstraintSet.TOP, dateId, ConstraintSet.BOTTOM, dpToPx(4));
        cs.connect(timeId, ConstraintSet.LEFT, dateId, ConstraintSet.LEFT);

        cs.connect(albumArtId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        cs.connect(albumArtId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        cs.connect(albumArtId, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT);
        cs.connect(albumArtId, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT);
        cs.constrainWidth(albumArtId, dpToPx(260));
        cs.constrainHeight(albumArtId, dpToPx(260));
        cs.setVerticalBias(albumArtId, 0.35f);

        cs.connect(titleId, ConstraintSet.TOP, albumArtId, ConstraintSet.BOTTOM, dpToPx(24));
        cs.centerHorizontally(titleId, ConstraintSet.PARENT_ID);

        cs.connect(artistId, ConstraintSet.TOP, titleId, ConstraintSet.BOTTOM, dpToPx(8));
        cs.centerHorizontally(artistId, ConstraintSet.PARENT_ID);

        cs.connect(playId, ConstraintSet.TOP, artistId, ConstraintSet.BOTTOM, dpToPx(20));
        cs.centerHorizontally(playId, ConstraintSet.PARENT_ID);
        cs.constrainWidth(playId, dpToPx(40));
        cs.constrainHeight(playId, dpToPx(40));

        cs.connect(prevId, ConstraintSet.TOP, playId, ConstraintSet.TOP);
        cs.connect(prevId, ConstraintSet.BOTTOM, playId, ConstraintSet.BOTTOM);
        cs.connect(prevId, ConstraintSet.RIGHT, playId, ConstraintSet.LEFT, dpToPx(24));
        cs.constrainWidth(prevId, dpToPx(40));
        cs.constrainHeight(prevId, dpToPx(40));

        cs.connect(nextId, ConstraintSet.TOP, playId, ConstraintSet.TOP);
        cs.connect(nextId, ConstraintSet.BOTTOM, playId, ConstraintSet.BOTTOM);
        cs.connect(nextId, ConstraintSet.LEFT, playId, ConstraintSet.RIGHT, dpToPx(24));
        cs.constrainWidth(nextId, dpToPx(40));
        cs.constrainHeight(nextId, dpToPx(40));

        cs.connect(seekBarBgId, ConstraintSet.TOP, playId, ConstraintSet.BOTTOM, dpToPx(24));
        cs.connect(seekBarBgId, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT);
        cs.connect(seekBarBgId, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT);
        cs.constrainHeight(seekBarBgId, dpToPx(3));
        cs.constrainWidth(seekBarBgId, dpToPx(260));
        cs.centerHorizontally(seekBarBgId, ConstraintSet.PARENT_ID);

        cs.connect(seekBarProgressId, ConstraintSet.TOP, seekBarBgId, ConstraintSet.TOP);
        cs.connect(seekBarProgressId, ConstraintSet.LEFT, seekBarBgId, ConstraintSet.LEFT);
        cs.constrainHeight(seekBarProgressId, dpToPx(3));
        cs.constrainWidth(seekBarProgressId, dpToPx(100));

        cs.applyTo((ConstraintLayout) mContainer);
    }
    
    private TextView createTextView(float sizeSp, boolean isBold) {
        TextView textView = new TextView(mContext);
        textView.setId(View.generateViewId());
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (isBold) {
            textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        } else {
            textView.setTypeface(Typeface.SANS_SERIF);
        }
        return textView;
    }

    private ImageView createIconNoBackground(int resourceId) {
        ImageView iv = new ImageView(mContext);
        iv.setId(View.generateViewId());
        iv.setImageResource(resourceId);
        iv.setColorFilter(Color.WHITE);
        iv.setAdjustViewBounds(false);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return iv;
    }

    @Override
    public void onTimeTick() {
        mDateView.setText(LockscreenClockUtils.getCurrentTimeString(mContext.getString(R.string.date_format)));
        mTimeView.setText(LockscreenClockUtils.getCurrentTimeString("HH:mm"));
    }

    @Override
    public void onNotificationStateChanged(boolean hasNotifications) {}

    @Override
    public void applyStyles() {
        int hourColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getHourColor());
        int minuteColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getMinuteColor());
        int dayColor = LockscreenClockUtils.parseColor(CustomLockscreenSettings.getDayColor());

        mDateView.setTextColor(dayColor);
        mTimeView.setTextColor(minuteColor);
    }


    private int dpToPx(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density);
    }

    @Override
    public void onMetadataChanged(String title, String artist, Bitmap albumArt) {
        mSongTitleView.setText(title != null ? title : mContext.getString(R.string.default_song_title));
        mSongArtistView.setText(artist != null ? artist : mContext.getString(R.string.default_song_artist));
        if (albumArt != null) {
            mAlbumArtView.setImageBitmap(albumArt);
        } else {
            mAlbumArtView.setImageResource(R.drawable.default_album_art);
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (DEBUG_MODE && mPlayPauseButton instanceof ImageView) {
            ((ImageView) mPlayPauseButton).setImageResource(isPlaying ? R.drawable.avium_ic_media_pause : R.drawable.avium_ic_media_play);
        } else if (!DEBUG_MODE && mMediaControlsGlassManager != null) {
            View[] views = mMediaControlsGlassManager.getDigitViews();
            if (views.length > 1 && views[1] instanceof GlassClockManager.DigitView) {
                int iconRes = isPlaying ? R.drawable.avium_ic_media_pause : R.drawable.avium_ic_media_play;
                mMediaIconResources[1] = iconRes;
                ((GlassClockManager.DigitView) views[1]).setDigitDrawable(ContextCompat.getDrawable(mContext, iconRes));
            }
        }
    }

    @Override
    public void onProgressChanged(long currentPosition, long duration) {
        if (duration <= 0) {
            ViewGroup.LayoutParams params = mSeekBarProgress.getLayoutParams();
            params.width = 0;
            mSeekBarProgress.setLayoutParams(params);
            return;
        }

        float progress = (float) currentPosition / duration;
        int totalWidth = mSeekBarBackground.getWidth();

        ViewGroup.LayoutParams params = mSeekBarProgress.getLayoutParams();
        params.width = (int) (totalWidth * progress);
        mSeekBarProgress.setLayoutParams(params);
    }

    private void registerMediaListener() {
        if (mMediaSessionManager != null) {
            ComponentName componentName = null;
            mMediaSessionManager.addOnActiveSessionsChangedListener(this, componentName);
            onActiveSessionsChanged(mMediaSessionManager.getActiveSessions(componentName));
        }
    }

    private void unregisterMediaListener() {
        if (mMediaSessionManager != null) {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(this);
        }
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        if (mMusicController != null) {
            mMusicController.cleanup();
            mMusicController = null;
        }
        if (controllers != null && !controllers.isEmpty()) {
            if (mSeekBarBackground != null) {
                mSeekBarBackground.setVisibility(View.VISIBLE);
            }
            if (mSeekBarProgress != null) {
                mSeekBarProgress.setVisibility(View.VISIBLE);
            }
            mMusicController = new MusicController(controllers.get(0), this);
        } else {
            if (mSeekBarBackground != null) {
                mSeekBarBackground.setVisibility(View.GONE);
            }
            if (mSeekBarProgress != null) {
                mSeekBarProgress.setVisibility(View.GONE);
            }
            onMetadataChanged(null, null, null);
            onPlaybackStateChanged(false);
            onProgressChanged(0, 0);
        }
    }
}
