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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.combine
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.animation.LinearInterpolator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.setPadding
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.interruption.KeyguardNotificationVisibilityProvider
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Provider
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager

private const val TAG = "AVIUM_LOCKSCREEN"
private const val ICON_SIZE_DP = 30
private const val ICON_PADDING_DP = 8
private const val CAPSULE_PADDING_DP = 12
private const val CAPSULE_RADIUS_DP = 18
private const val MAX_ICONS = 3
private const val MARGIN_BOTTOM_DP = 120
private const val MARGIN_START_DP = 32
private const val PANEL_SIDE_DP = 16
private const val PANEL_TOP_PERCENT = 0.42f
private const val PANEL_RADIUS_DP = 28

@SysUISingleton
class NotificationIconsSection @Inject constructor(
    private val context: Context,
    private val notifPipeline: NotifPipeline,
    private val mediaCarouselInteractor: MediaCarouselInteractor,
    private val notificationStackController: NotificationStackScrollLayoutController,
    private val visibilityProvider: KeyguardNotificationVisibilityProvider,
    private val statusBarStateController: StatusBarStateController,
    private val keyguardViewManager: Provider<StatusBarKeyguardViewManager>,
) : KeyguardSection() {

    private var capsuleContainer: FrameLayout? = null
    private var floatingPanel: FrameLayout? = null
    private var drawerBackdrop: View? = null
    private var iconsContainer: LinearLayout? = null
    private var blurDrawable: BackgroundBlurDrawable? = null
    private var downX = 0f
    private var renderedNotifications: List<NotificationEntry> = emptyList()
    private val packageManager: PackageManager = context.packageManager

    private var notificationStack: View? = null
    private var originalNotificationParent: ViewGroup? = null
    private var originalNotificationLayoutParams: ViewGroup.LayoutParams? = null
    private var originalNotificationIndex = -1
    private var customContainerMode = false
    private var drawerProgress = 0f
    private var drawerAnimator: ValueAnimator? = null
    private var listening = false
    private var mediaBinding: DisposableHandle? = null
    private var hasMedia = false

    private val stateListener = object : StatusBarStateController.StateListener {
        override fun onStatePreChange(oldState: Int, newState: Int) {
            if (newState != StatusBarState.KEYGUARD) setCustomContainerMode(false)
        }

        override fun onStateChanged(newState: Int) {
            updateCustomContainerMode()
        }

        override fun onExpandedChanged(isExpanded: Boolean) {
            updateCustomContainerMode()
        }

        override fun onDozingChanged(isDozing: Boolean) {
            if (isDozing) closeDrawerImmediately()
            updateCustomContainerMode()
            updateCapsuleVisibility()
        }
    }

    private val visibilityListener = Consumer<String> { refreshIcons() }

    init {
        notifPipeline.addOnAfterRenderListListener { entries ->
            renderedNotifications = entries.mapNotNull { it.asListEntry()?.representativeEntry }
            refreshIcons()
        }
    }

    override fun addViews(parent: ConstraintLayout) {
        if (!CustomLockscreenSettings.isEnabled()) return

        drawerBackdrop = View(context).apply {
            id = View.generateViewId()
            visibility = View.INVISIBLE
            elevation = 7f * context.resources.displayMetrics.density
            setOnTouchListener(drawerTouchListener())
        }.also { parent.addView(it) }

        val panel = createFloatingPanel(context)
        floatingPanel = panel
        parent.addView(panel)

        val capsule = createCapsuleView(context)
        capsuleContainer = capsule
        parent.addView(capsule)

        updateCustomContainerMode()
        refreshIcons()
    }

    override fun removeViews(parent: ConstraintLayout) {
        mediaBinding?.dispose()
        mediaBinding = null
        if (listening) {
            visibilityProvider.removeOnStateChangedListener(visibilityListener)
            statusBarStateController.removeCallback(stateListener)
            listening = false
        }
        setCustomContainerMode(false)
        restoreNotificationStack()
        capsuleContainer?.let { parent.removeView(it) }
        floatingPanel?.let { parent.removeView(it) }
        drawerBackdrop?.let { parent.removeView(it) }
        drawerBackdrop = null
        capsuleContainer = null
        floatingPanel = null
        iconsContainer = null
        blurDrawable = null
    }

    override fun bindData(parent: ConstraintLayout) {
        mediaBinding?.dispose()
        mediaBinding = capsuleContainer?.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                combine(
                    mediaCarouselInteractor.hasActiveMedia,
                    mediaCarouselInteractor.allowMediaOnLockscreen,
                ) { active, allowed -> active && allowed }.collect {
                    hasMedia = it
                    refreshIcons()
                }
            }
        }
        if (!listening) {
            visibilityProvider.addOnStateChangedListener(visibilityListener)
            statusBarStateController.addCallback(stateListener)
            listening = true
        }
        updateCustomContainerMode()
        refreshIcons()
    }

    private fun updateCustomContainerMode() {
        val inLockscreen =
            statusBarStateController.state == StatusBarState.KEYGUARD &&
                !statusBarStateController.isDozing &&
                !statusBarStateController.isExpanded
        setCustomContainerMode(CustomLockscreenSettings.isEnabled() && inLockscreen)
    }

    private fun setCustomContainerMode(enabled: Boolean) {
        if (customContainerMode == enabled) return
        customContainerMode = enabled
        if (enabled) {
            moveNotificationStackToFloatingPanel()
        } else {
            closeDrawerImmediately()
            restoreNotificationStack()
        }
        updateCapsuleVisibility()
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        val density = context.resources.displayMetrics.density
        capsuleContainer?.let { capsule ->
            constraintSet.setVisibilityMode(capsule.id, ConstraintSet.VISIBILITY_MODE_IGNORE)
            constraintSet.constrainWidth(capsule.id, ConstraintSet.WRAP_CONTENT)
            constraintSet.constrainHeight(capsule.id, ConstraintSet.WRAP_CONTENT)
            constraintSet.connect(
                capsule.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM, (MARGIN_BOTTOM_DP * density).toInt()
            )
            constraintSet.connect(
                capsule.id, ConstraintSet.START, ConstraintSet.PARENT_ID,
                ConstraintSet.START, (MARGIN_START_DP * density).toInt()
            )
        }

        drawerBackdrop?.let { backdrop ->
            constraintSet.setVisibilityMode(backdrop.id, ConstraintSet.VISIBILITY_MODE_IGNORE)
            constraintSet.constrainWidth(backdrop.id, ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(backdrop.id, ConstraintSet.MATCH_CONSTRAINT)
            for (edge in intArrayOf(ConstraintSet.TOP, ConstraintSet.BOTTOM,
                ConstraintSet.START, ConstraintSet.END)) {
                constraintSet.connect(backdrop.id, edge, ConstraintSet.PARENT_ID, edge)
            }
        }

        floatingPanel?.let { panel ->
            constraintSet.setVisibilityMode(panel.id, ConstraintSet.VISIBILITY_MODE_IGNORE)
            constraintSet.constrainWidth(panel.id, ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(panel.id, ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.connect(
                panel.id, ConstraintSet.START, ConstraintSet.PARENT_ID,
                ConstraintSet.START, (PANEL_SIDE_DP * density).toInt()
            )
            constraintSet.connect(
                panel.id, ConstraintSet.END, ConstraintSet.PARENT_ID,
                ConstraintSet.END, (PANEL_SIDE_DP * density).toInt()
            )
            constraintSet.connect(
                panel.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                (context.resources.displayMetrics.heightPixels * PANEL_TOP_PERCENT).toInt()
            )
            constraintSet.connect(
                panel.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM, (PANEL_SIDE_DP * density).toInt()
            )
        }
    }

    private fun panelColor(): Int =
        if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES) 0xB8202A35.toInt() else 0xB8E8EAED.toInt()

    private fun createFloatingPanel(context: Context): FrameLayout {
        val density = context.resources.displayMetrics.density
        return object : FrameLayout(context) {
            private var blankSpaceGesture = false

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                val handled = super.dispatchTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                return handled
            }

            override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val stack = notificationStackController.view
                    blankSpaceGesture = event.y < 32f * density ||
                        stack.isBelowLastNotification(event.x - stack.x, event.y - stack.y)
                }
                // Card gestures remain owned by the native notification and media views.
                return blankSpaceGesture
            }
        }.apply {
            id = View.generateViewId()
            visibility = View.INVISIBLE
            alpha = 0f
            elevation = 8f * density
            clipChildren = true
            clipToPadding = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = PANEL_RADIUS_DP * density
                setColor(panelColor())
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setOnTouchListener(drawerTouchListener())
            addView(View(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = GradientDrawable().apply {
                    cornerRadius = 2f * density
                    setColor(if (context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                        0x99FFFFFF.toInt() else 0x99000000.toInt())
                }
            }, FrameLayout.LayoutParams(
                (32f * density).toInt(), (4f * density).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = (12f * density).toInt() })
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    val root = view.viewRootImpl ?: return
                    try {
                        val blur = root.createBackgroundBlurDrawable()
                        blur.setCornerRadius(PANEL_RADIUS_DP * density)
                        blur.setBlurRadius((24f * density).toInt())
                        blur.setColor(panelColor())
                        view.background = blur
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Notification drawer blur unavailable", e)
                    }
                }

                override fun onViewDetachedFromWindow(view: View) = Unit
            })
        }
    }

    private fun moveNotificationStackToFloatingPanel() {
        val panel = floatingPanel ?: return
        val stack = notificationStack ?: notificationStackController.view.also {
            notificationStack = it
        }
        if (stack.parent === panel) return

        val currentParent = stack.parent as? ViewGroup ?: return
        originalNotificationParent = currentParent
        originalNotificationIndex = currentParent.indexOfChild(stack)
        originalNotificationLayoutParams = stack.layoutParams
        currentParent.removeViewAt(originalNotificationIndex)
        panel.addView(
            stack,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { topMargin = (32f * context.resources.displayMetrics.density).toInt() }
        )
        if (!SceneContainerFlag.isEnabled) {
            notificationStackController.view.setLockscreenDrawer(true)
        }
        stack.visibility = View.VISIBLE
        panel.visibility = View.INVISIBLE
        panel.alpha = 0f
        drawerProgress = 0f
        panel.requestLayout()
    }

    private fun restoreNotificationStack() {
        val stack = notificationStack ?: return
        val parent = originalNotificationParent ?: return
        if (stack.parent !== parent) {
            (stack.parent as? ViewGroup)?.removeView(stack)
            val params = originalNotificationLayoutParams
                ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            val index = originalNotificationIndex.coerceIn(0, parent.childCount)
            parent.addView(stack, index, params)
        }
        if (!SceneContainerFlag.isEnabled) {
            notificationStackController.view.setLockscreenDrawer(false)
        }
        stack.visibility = View.VISIBLE
        notificationStack = null
        originalNotificationParent = null
        originalNotificationLayoutParams = null
        originalNotificationIndex = -1
        parent.requestLayout()
    }

    private fun openDrawer() {
        if (!customContainerMode) return
        val panel = floatingPanel ?: return
        moveNotificationStackToFloatingPanel()
        drawerAnimator?.removeAllListeners()
        drawerAnimator?.cancel()
        panel.visibility = View.VISIBLE
        notificationStackController.view.setLockscreenDrawerShowing(true)
        drawerBackdrop?.visibility = View.VISIBLE
        drawerAnimator = ValueAnimator.ofFloat(drawerProgress, 1f).apply {
            duration = 260L
            interpolator = LinearInterpolator()
            addUpdateListener {
                drawerProgress = it.animatedValue as Float
                panel.alpha = drawerProgress
                panel.translationY =
                    (1f - drawerProgress) * panel.height * 0.08f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    drawerAnimator = null
                }
            })
            start()
        }
    }

    private fun closeDrawer(onClosed: (() -> Unit)? = null) {
        val panel = floatingPanel ?: return
        drawerAnimator?.removeAllListeners()
        drawerAnimator?.cancel()
        drawerAnimator = ValueAnimator.ofFloat(drawerProgress, 0f).apply {
            duration = 220L
            interpolator = LinearInterpolator()
            addUpdateListener {
                drawerProgress = it.animatedValue as Float
                panel.alpha = drawerProgress
                panel.translationY =
                    (1f - drawerProgress) * panel.height * 0.08f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    panel.visibility = View.INVISIBLE
                    notificationStackController.view.setLockscreenDrawerShowing(false)
                    drawerBackdrop?.visibility = View.INVISIBLE
                    drawerAnimator = null
                    onClosed?.invoke()
                }
            })
            start()
        }
    }

    private fun closeDrawerImmediately() {
        drawerAnimator?.removeAllListeners()
        drawerAnimator?.cancel()
        drawerAnimator = null
        drawerProgress = 0f
        notificationStackController.view.setLockscreenDrawerShowing(false)
        drawerBackdrop?.visibility = View.INVISIBLE
        floatingPanel?.apply {
            alpha = 0f
            visibility = View.INVISIBLE
            translationY = 0f
        }
    }

    private fun drawerTouchListener(): View.OnTouchListener {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val threshold = 48f * context.resources.displayMetrics.density
        var startX = 0f
        var startY = 0f
        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> true
                MotionEvent.ACTION_UP -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    when {
                        kotlin.math.abs(dy) > threshold && kotlin.math.abs(dy) > kotlin.math.abs(dx) -> {
                            closeDrawer {
                                if (customContainerMode &&
                                    statusBarStateController.state == StatusBarState.KEYGUARD &&
                                    !statusBarStateController.isDozing) {
                                    if (dy < 0) {
                                        keyguardViewManager.get().showPrimaryBouncer(
                                            true, "custom lockscreen drawer swipe up"
                                        )
                                    }
                                    // A downward drag only dismisses this local drawer.

                                }
                            }
                        }
                        kotlin.math.abs(dx) < slop && kotlin.math.abs(dy) < slop -> closeDrawer()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun createCapsuleView(context: Context): FrameLayout {
        val density = context.resources.displayMetrics.density
        return FrameLayout(context).apply {
            id = View.generateViewId()
            background = fallbackBackground(density)
            val padding = (CAPSULE_PADDING_DP * density).toInt()
            setPadding(padding, padding, padding, padding)
            setOnClickListener {
                if (drawerProgress > 0.5f) closeDrawer() else openDrawer()
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        if (event.rawX - downX > 48f * density) {
                            view.performClick()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            isClickable = true
            isFocusable = true
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    installBlur(view, density)
                }

                override fun onViewDetachedFromWindow(view: View) {
                        blurDrawable = null
                }
            })
            val icons = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            iconsContainer = icons
            addView(icons)
        }
    }

    private fun fallbackBackground(density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = CAPSULE_RADIUS_DP * density
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            setColor(if (night) 0xCC202124.toInt() else 0xCCE8EAED.toInt())
        }

    private fun installBlur(view: View, density: Float) {
        val root = view.viewRootImpl ?: return
        try {
            val drawable = root.createBackgroundBlurDrawable()
            drawable.setCornerRadius(CAPSULE_RADIUS_DP * density)
            drawable.setBlurRadius((18 * density).toInt())
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            drawable.setColor(if (night) 0xB8202124.toInt() else 0xB8E8EAED.toInt())
            blurDrawable = drawable
            view.background = drawable
        } catch (e: RuntimeException) {
            Log.w(TAG, "Background blur is unavailable", e)
        }
    }

    private fun refreshIcons() {
        val icons = iconsContainer ?: return
        icons.removeAllViews()
        val shownPackages = mutableSetOf<String>()
        for (entry in renderedNotifications) {
            if (icons.childCount >= MAX_ICONS) break
            if (visibilityProvider.shouldHideNotification(entry)) continue
            val pkg = entry.sbn.packageName
            if (!shownPackages.add(pkg)) continue
            val icon = getNotificationIcon(entry) ?: continue
            val density = context.resources.displayMetrics.density
            icons.addView(ImageView(context).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(
                    (ICON_SIZE_DP * density).toInt(),
                    (ICON_SIZE_DP * density).toInt()
                ).apply { marginEnd = (ICON_PADDING_DP * density).toInt() }
                scaleType = ImageView.ScaleType.FIT_CENTER
            })
        }
        if (hasMedia && icons.childCount < MAX_ICONS) {
            val size = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
            icons.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_music_note)
                layoutParams = LinearLayout.LayoutParams(size, size)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    if (context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                        android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
                )
            })
        }
        updateCapsuleVisibility()
    }

    private fun getNotificationIcon(entry: NotificationEntry): Drawable? {
        val statusIcon = entry.icons.statusBarIcon?.drawable
        statusIcon?.constantState?.newDrawable()?.mutate()?.let { return it }
        val notifIcon = try {
            entry.sbn.notification.smallIcon?.loadDrawable(context)
        } catch (_: RuntimeException) {
            null
        }
        return notifIcon ?: getAppIcon(entry.sbn)
    }

    private fun getAppIcon(sbn: StatusBarNotification): Drawable? = try {
        packageManager.getApplicationIcon(
            packageManager.getApplicationInfo(sbn.packageName, 0)
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun updateCapsuleVisibility() {
        capsuleContainer?.visibility =
            if (customContainerMode && (iconsContainer?.childCount ?: 0) > 0) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

}
