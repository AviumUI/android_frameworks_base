/*
 * Copyright (C) 2022 Project Kaleidoscope
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

package com.android.systemui.statusbar.phone;

import android.app.Notification;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.util.Log;
import android.os.Bundle;

import com.android.internal.statusbar.StatusBarIcon;
import com.android.internal.util.ContrastColorUtil;
import com.android.systemui.Dependency;
import com.android.systemui.res.R;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.statusbar.NotificationListener;
import com.android.systemui.statusbar.StatusBarIconView;

import java.util.ArrayList;

public abstract class LyricViewController implements
    DarkIconDispatcher.DarkReceiver,
    NotificationListener.NotificationHandler {

    private static final String EXTRA_TICKER_ICON = "ticker_icon";
    private static final String EXTRA_TICKER_ICON_SWITCH = "ticker_icon_switch";

    private static final int HIDE_LYRIC_DELAY = 1200;

    private final Context mContext;
    private final ImageSwitcher mIconSwitcher;
    private final TextSwitcher mTextSwitcher;
    private final View mLyricContainer;

    private final ContrastColorUtil mNotificationColorUtil;

    private boolean mEnabled;
    private boolean mStarted;

    private String mCurrentNotificationPackage = null;
    private int mCurrentNotificationId;

    private ColorStateList mTintColorStateList;

    public LyricViewController(Context context, View statusBar) {
        mContext = context;
        mLyricContainer = statusBar.findViewById(R.id.lyric_container);
        mIconSwitcher = statusBar.findViewById(R.id.lyric_icon);
        mTextSwitcher = statusBar.findViewById(R.id.lyric_text);

        mNotificationColorUtil = ContrastColorUtil.getInstance(mContext);

        Animation animationIn = AnimationUtils.loadAnimation(mContext,
                com.android.internal.R.anim.push_up_in);
        Animation animationOut = AnimationUtils.loadAnimation(mContext,
                com.android.internal.R.anim.push_up_out);

        mTextSwitcher.setInAnimation(animationIn);
        mTextSwitcher.setOutAnimation(animationOut);
        mIconSwitcher.setInAnimation(animationIn);
        mIconSwitcher.setOutAnimation(animationOut);

        mLyricContainer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hideLyricView(true);
                v.postDelayed(() -> showLyricView(true), HIDE_LYRIC_DELAY);
            }
            return false;
        });

        Dependency.get(DarkIconDispatcher.class).addDarkReceiver(this);
        Dependency.get(NotificationListener.class).addNotificationHandler(this);
    }

    public void setEnabled(boolean enabled) {
        Log.d("AviumLyric", "setEnabled: 歌词功能开关变更，旧值=" + mEnabled + ", 新值=" + enabled);
        mEnabled = enabled;
        if (!mEnabled && mStarted) {
            Log.d("AviumLyric", "setEnabled: 功能关闭且歌词运行中，停止歌词");
            stopLyric();
        }
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.d("AviumLyric", "onNotificationPosted: =================== 新通知 ===================");
        Log.d("AviumLyric", "onNotificationPosted: 接收通知，包名=" + sbn.getPackageName() +
                ", 通知ID=" + sbn.getId() + ", 用户=" + sbn.getUser() +
                ", PostTime=" + sbn.getPostTime());

        if (!mEnabled) {
            Log.d("AviumLyric", "onNotificationPosted: 歌词功能未启用（mEnabled=false），跳过处理");
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) {
            Log.e("AviumLyric", "onNotificationPosted: 获取到的 Notification 对象为空！");
            return;
        }

        Log.d("AviumLyric", "onNotificationPosted: --- 开始打印通知详细内容 ---");
        Log.d("AviumLyric", "tickerText: " + notification.tickerText);
        Log.d("AviumLyric", "通知 flags=0x" + Integer.toHexString(notification.flags) +
                ", FLAG_ALWAYS_SHOW_TICKER=" + ((notification.flags & Notification.FLAG_ALWAYS_SHOW_TICKER) != 0) +
                ", FLAG_ONLY_UPDATE_TICKER=" + ((notification.flags & Notification.FLAG_ONLY_UPDATE_TICKER) != 0));
        Bundle extras = notification.extras;
        if (extras != null) {
            Log.d("AviumLyric", "Extras Bundle 内容如下:");
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                String valueString = (value != null) ? value.toString() : "null";
                if (valueString.length() > 200) { 
                    valueString = valueString.substring(0, 200) + "...";
                }
                Log.d("AviumLyric", "  > key: " + key + ", value: " + valueString);
            }
        } else {
            Log.d("AviumLyric", "Extras Bundle 为空。");
        }

        if (extras != null) {
            String title = extras.getString(Notification.EXTRA_TITLE, "N/A");
            String text = extras.getString(Notification.EXTRA_TEXT, "N/A");
            String subText = extras.getString(Notification.EXTRA_SUB_TEXT, "N/A");
            Log.d("AviumLyric", "常用字段解析: Title='" + title + "', Text='" + text + "', SubText='" + subText + "'");
        }
        Log.d("AviumLyric", "onNotificationPosted: --- 通知详细内容打印完毕 ---");


        boolean isLyric = (notification.flags & Notification.FLAG_ALWAYS_SHOW_TICKER) != 0;

        boolean isCurrentNotification = mCurrentNotificationId == sbn.getId() &&
                TextUtils.equals(sbn.getPackageName(), mCurrentNotificationPackage);
        if (!isLyric) {
            Log.d("AviumLyric", "onNotificationPosted: 非歌词通知（isLyric=false），跳过");
            if (isCurrentNotification) {
                Log.d("AviumLyric", "onNotificationPosted: 非歌词通知是当前歌词通知，停止歌词显示");
                stopLyric();
            }
        } else {
            mCurrentNotificationPackage = sbn.getPackageName();
            mCurrentNotificationId = sbn.getId();
            Log.d("AviumLyric", "onNotificationPosted: 确认歌词通知，更新当前标识：包名=" + mCurrentNotificationPackage +
                    ", 通知ID=" + mCurrentNotificationId);

            if (notification.tickerText == null) {
                Log.e("AviumLyric", "onNotificationPosted: 歌词文本（tickerText）为空，停止歌词显示");
                stopLyric();
                return;
            }
            Log.d("AviumLyric", "onNotificationPosted: 歌词文本=" + notification.tickerText);

            if (!isCurrentNotification || !mStarted ||
                    extras.getBoolean(EXTRA_TICKER_ICON_SWITCH, false)) {
                int iconId = extras.getInt(EXTRA_TICKER_ICON, -1);
                String slot = sbn.getPackageName() + "/0x" + Integer.toHexString(sbn.getId());
                StatusBarIconView statusBarIconView = new StatusBarIconView(mContext, slot, sbn);
                Drawable icon = iconId == -1 ? notification.getSmallIcon().loadDrawable(mContext) :
                        statusBarIconView.getIcon(mContext, sbn.getPackageContext(mContext),
                                new StatusBarIcon(sbn.getPackageName(), sbn.getUser(),
                                        iconId, notification.iconLevel, 0, null, StatusBarIcon.Type.NotifSmallIcon));
                mIconSwitcher.setImageDrawable(icon);
                updateIconTint();
            }
            Log.d("AviumLyric", "onNotificationPosted: 启动歌词显示，设置歌词文本");
            startLyric();
            mTextSwitcher.setText(notification.tickerText);
        }
    }

    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        boolean isCurrentNotification = mCurrentNotificationId == sbn.getId() &&
                TextUtils.equals(sbn.getPackageName(), mCurrentNotificationPackage);
        if (isCurrentNotification) {
            stopLyric();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        onNotificationRemoved(sbn, rankingMap);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
    }

    @Override
    public void onNotificationsInitialized() {
    }

    public void startLyric() {
        if (!mStarted) {
            Log.d("AviumLyric", "startLyric: 启动歌词显示（mStarted=true）");
            mStarted = true;
            showLyricView(true);
        } else {
            Log.d("AviumLyric", "startLyric: 歌词已在运行（mStarted=true）");
        }
    }

    public void stopLyric() {
        if (mStarted) {
            Log.d("AviumLyric", "stopLyric: 停止歌词显示（mStarted=false）");
            mStarted = false;
            hideLyricView(true);
            mCurrentNotificationPackage = null;
            mCurrentNotificationId = 0;
        } else {
            Log.d("AviumLyric", "stopLyric: 歌词已停止（mStarted=false)");
        }
    }

    public abstract void showLyricView(boolean animate);

    public abstract void hideLyricView(boolean animate);

    public boolean isLyricStarted() {
        return mStarted;
    }

    public View getView() {
        return mLyricContainer;
    }

    private void updateIconTint() {
        Drawable drawable = ((ImageView)mIconSwitcher.getCurrentView()).getDrawable();
        boolean isGrayscale = mNotificationColorUtil.isGrayscaleIcon(drawable);
        if (isGrayscale) {
            ((ImageView) mIconSwitcher.getCurrentView()).setImageTintList(mTintColorStateList);
            ((ImageView) mIconSwitcher.getNextView()).setImageTintList(mTintColorStateList);
        } else {
            ((ImageView) mIconSwitcher.getCurrentView()).setImageTintList(null);
            ((ImageView) mIconSwitcher.getNextView()).setImageTintList(null);
        }
    }

    @Override
    public void onDarkChanged(ArrayList<Rect> areas, float darkIntensity, int tint) {
        int tintColor = DarkIconDispatcher.getTint(areas, mLyricContainer, tint);
        if (DarkIconDispatcher.isInAreas(areas, mLyricContainer)) {
            tintColor = DarkIconDispatcher.getInverseTint(areas, mLyricContainer, tint);
        }

        ((TextView) mTextSwitcher.getCurrentView()).setTextColor(tintColor);
        ((TextView) mTextSwitcher.getNextView()).setTextColor(tintColor);

        mTintColorStateList = ColorStateList.valueOf(tintColor);
        updateIconTint();
    }
}
