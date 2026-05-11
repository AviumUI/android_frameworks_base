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

package org.avium.systemui.lockscreen.sections

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.setPadding
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.shade.ShadeController
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings
import javax.inject.Inject

private const val TAG = "AVIUM_LOCKSCREEN"

private const val ICON_SIZE_DP = 30
private const val ICON_PADDING_DP = 8
private const val CAPSULE_PADDING_DP = 12
private const val CAPSULE_RADIUS_DP = 18
private const val MAX_ICONS = 3
private const val CAPSULE_BACKGROUND_COLOR = 0xE6E0E0E0.toInt()
private const val MARGIN_BOTTOM_DP = 120
private const val MARGIN_START_DP = 32

@SysUISingleton
class NotificationIconsSection @Inject constructor(
    private val context: Context,
    private val notifPipeline: NotifPipeline,
    private val shadeController: ShadeController
) : KeyguardSection(), NotifCollectionListener {

    private var capsuleContainer: FrameLayout? = null
    private var iconsContainer: LinearLayout? = null
    private val activeNotifications = mutableSetOf<String>()
    private val packageManager: PackageManager = context.packageManager

    init {
        notifPipeline.addCollectionListener(this)
    }

    override fun addViews(parent: ConstraintLayout) {
        if (!CustomLockscreenSettings.isEnabled()) {
            return
        }

        val capsule = createCapsuleView(context)
        capsuleContainer = capsule
        parent.addView(capsule)
        
        refreshIcons()
    }

    override fun removeViews(parent: ConstraintLayout) {
        capsuleContainer?.let { parent.removeView(it) }
        capsuleContainer = null
        iconsContainer = null
    }

    override fun bindData(parent: ConstraintLayout) {
        //ntd
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        val capsule = capsuleContainer ?: return
        val density = context.resources.displayMetrics.density

        constraintSet.constrainWidth(capsule.id, ConstraintSet.WRAP_CONTENT)
        constraintSet.constrainHeight(capsule.id, ConstraintSet.WRAP_CONTENT)

        val marginBottom = (MARGIN_BOTTOM_DP * density).toInt()
        val marginStart = (MARGIN_START_DP * density).toInt()
        
        constraintSet.connect(capsule.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, marginBottom)
        constraintSet.connect(capsule.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, marginStart)
    }

    private fun createCapsuleView(context: Context): FrameLayout {
        val density = context.resources.displayMetrics.density
        
        val capsule = FrameLayout(context).apply {
            id = View.generateViewId()
            
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CAPSULE_RADIUS_DP * density
                setColor(CAPSULE_BACKGROUND_COLOR)
            }
            
            val padding = (CAPSULE_PADDING_DP * density).toInt()
            setPadding(padding, padding, padding, padding)
            
            setOnClickListener {
                openNotificationShade()
            }
            
            isClickable = true
            isFocusable = true
        }

        val iconsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        iconsContainer = iconsLayout
        capsule.addView(iconsLayout)

        return capsule
    }

    private fun addNotificationIcon(entry: NotificationEntry) {
        val iconsLayout = iconsContainer ?: return
        val packageName = entry.sbn.packageName
        
        if (packageName.startsWith("android") || packageName.startsWith("com.android.systemui")) {
            return
        }
        
        if (activeNotifications.contains(packageName)) {
            return
        }

        val icon = getAppIcon(entry.sbn) ?: return
        
        activeNotifications.add(packageName)

        val density = context.resources.displayMetrics.density
        val iconSize = (ICON_SIZE_DP * density).toInt()
        val iconPadding = (ICON_PADDING_DP * density).toInt()

        val imageView = ImageView(context).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = iconPadding
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        iconsLayout.addView(imageView)
        
        if (iconsLayout.childCount > MAX_ICONS) {
            iconsLayout.removeViewAt(0)
            val oldestPackage = activeNotifications.firstOrNull()
            oldestPackage?.let { activeNotifications.remove(it) }
        }

        updateCapsuleVisibility()
    }

    private fun removeNotificationIcon(entry: NotificationEntry) {
        val packageName = entry.sbn.packageName
        activeNotifications.remove(packageName)
        refreshIcons()
    }

    private fun refreshIcons() {
        val iconsLayout = iconsContainer ?: return
        iconsLayout.removeAllViews()
        activeNotifications.clear()
        
        val entries = notifPipeline.getAllNotifs()
        val shownPackages = mutableSetOf<String>()
        
        for (entry in entries) {
            val packageName = entry.sbn.packageName
            if (packageName.startsWith("android") || packageName.startsWith("com.android.systemui")) {
                continue
            }
            
            if (shownPackages.contains(packageName)) continue
            
            val icon = getAppIcon(entry.sbn) ?: continue
            shownPackages.add(packageName)
            
            val density = context.resources.displayMetrics.density
            val iconSize = (ICON_SIZE_DP * density).toInt()
            val iconPadding = (ICON_PADDING_DP * density).toInt()

            val imageView = ImageView(context).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = iconPadding
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            iconsLayout.addView(imageView)
            
            if (iconsLayout.childCount >= MAX_ICONS) break
        }
        
        activeNotifications.addAll(shownPackages)
        updateCapsuleVisibility()
    }

    private fun getAppIcon(sbn: StatusBarNotification): Drawable? {
        return try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationIcon(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun updateCapsuleVisibility() {
        val iconsLayout = iconsContainer ?: return
        capsuleContainer?.visibility = if (iconsLayout.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun openNotificationShade() {
        try {
            shadeController.animateExpandShade()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onEntryAdded(entry: NotificationEntry) {
        if (CustomLockscreenSettings.isEnabled()) {
            addNotificationIcon(entry)
        }
    }

    override fun onEntryUpdated(entry: NotificationEntry) {
        if (CustomLockscreenSettings.isEnabled()) {
            refreshIcons()
        }
    }

    override fun onEntryRemoved(entry: NotificationEntry, reason: Int) {
        if (CustomLockscreenSettings.isEnabled()) {
            removeNotificationIcon(entry)
        }
    }
}
