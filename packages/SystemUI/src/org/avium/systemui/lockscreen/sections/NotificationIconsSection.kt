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
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.setPadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.systemui.animation.view.LaunchableFrameLayout
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.view.layout.sections.AodBurnInLayer
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.MediaPipelineRepository
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSExpansionPathInterpolator
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.LockscreenShadeTransitionController
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.interruption.KeyguardNotificationVisibilityProvider
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.ConfigurationController
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.combine
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings
import org.avium.systemui.lockscreen.util.NotificationTileTransition

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
class NotificationIconsSection
@Inject
constructor(
    private val context: Context,
    private val configurationController: ConfigurationController,
    private val batteryController: BatteryController,
    private val mediaPipelineRepository: MediaPipelineRepository,
    private val notifPipeline: NotifPipeline,
    private val mediaCarouselInteractor: MediaCarouselInteractor,
    private val notificationStackController: NotificationStackScrollLayoutController,
    private val visibilityProvider: KeyguardNotificationVisibilityProvider,
    private val statusBarStateController: StatusBarStateController,
    private val keyguardViewManager: Provider<StatusBarKeyguardViewManager>,
    private val shadeTransitionController: Provider<LockscreenShadeTransitionController>,
    qsExpansionPathInterpolator: QSExpansionPathInterpolator,
) : KeyguardSection() {

    private var capsuleContainer: LaunchableFrameLayout? = null
    private var floatingPanel: LaunchableFrameLayout? = null
    private var drawerBackdrop: View? = null
    private var iconsContainer: LinearLayout? = null
    private var blurDrawable: BackgroundBlurDrawable? = null
    private var panelBlurDrawable: BackgroundBlurDrawable? = null
    private val blurPreDrawListener =
        ViewTreeObserver.OnPreDrawListener {
            updateBlurSurfaces()
            true
        }
    private var ambientAppearance: Boolean? = null
    private var renderedNotifications: List<NotificationEntry> = emptyList()
    private val packageManager: PackageManager = context.packageManager

    private var notificationStack: View? = null
    private var originalNotificationParent: ViewGroup? = null
    private var originalNotificationLayoutParams: ViewGroup.LayoutParams? = null
    private var originalNotificationIndex = -1
    private var customContainerMode = false
    private var drawerProgress = 0f
    private var drawerGestureGeneration = 0
    private val drawerTransition = NotificationTileTransition(qsExpansionPathInterpolator)
    private var iconsRefreshPending = false
    private var listening = false
    private var mediaBinding: DisposableHandle? = null
    private var hasMedia = false
    private var mediaPlayer: MediaData? = null
    private var shadeProgress = 0f

    private val shadeCallback =
        object : LockscreenShadeTransitionController.Callback {
            override fun setTransitionToFullShadeAmount(
                pxAmount: Float,
                animate: Boolean,
                delay: Long,
            ) {
                if (pxAmount > 0f && (drawerProgress > 0f || drawerTransition.isRunning)) {
                    closeDrawerImmediately()
                }
                shadeProgress =
                    (pxAmount / (240f * context.resources.displayMetrics.density)).coerceIn(0f, 1f)
                updateCapsuleAppearance()
            }
        }

    private val stateListener =
        object : StatusBarStateController.StateListener {
            override fun onStatePreChange(oldState: Int, newState: Int) {
                if (newState != StatusBarState.KEYGUARD) setCustomContainerMode(false)
            }

            override fun onStateChanged(newState: Int) {
                if (newState == StatusBarState.KEYGUARD) {
                    shadeProgress = 0f
                    updateCapsuleAppearance()
                }
                updateCustomContainerMode()
            }

            override fun onDozingChanged(isDozing: Boolean) {
                if (isDozing) {
                    shadeProgress = 0f
                    closeDrawerImmediately()
                }
                updateCustomContainerMode()
                refreshIcons()
            }
        }

    private val batteryListener =
        object : BatteryController.BatteryStateChangeCallback {
            override fun onPowerSaveChanged(isPowerSave: Boolean) {
                updateTheme()
            }
        }

    private val configurationListener =
        object : ConfigurationController.ConfigurationListener {
            override fun onUiModeChanged() = updateTheme()

            override fun onThemeChanged() = updateTheme()
        }

    private fun updateTheme() {
        ambientAppearance = null
        updateCapsuleSurface()
        val panel = floatingPanel
        when (val background = panel?.background) {
            is GradientDrawable -> background.setColor(panelColor())
            is LayerDrawable ->
                for (index in 0 until background.numberOfLayers) {
                    (background.getDrawable(index) as? GradientDrawable)?.setColor(panelColor())
                }
        }
        (panel?.getChildAt(0)?.background as? GradientDrawable)?.setColor(
            if (
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            )
                0x99FFFFFF.toInt()
            else 0x99000000.toInt()
        )
        refreshIcons()
        updateBlurSurfaces()
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

        parent.viewTreeObserver.addOnPreDrawListener(blurPreDrawListener)
        drawerBackdrop =
            View(context)
                .apply {
                    id = View.generateViewId()
                    visibility = View.INVISIBLE
                    elevation = 7f * context.resources.displayMetrics.density
                    setOnTouchListener(drawerTouchListener())
                }
                .also { parent.addView(it) }

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
            batteryController.removeCallback(batteryListener)
            configurationController.removeCallback(configurationListener)
            visibilityProvider.removeOnStateChangedListener(visibilityListener)
            statusBarStateController.removeCallback(stateListener)
            shadeTransitionController.get().removeCallback(shadeCallback)
            listening = false
        }
        setCustomContainerMode(false)
        parent.viewTreeObserver.removeOnPreDrawListener(blurPreDrawListener)
        blurDrawable?.setBlurRadius(0)
        panelBlurDrawable?.setBlurRadius(0)
        panelBlurDrawable = null
        restoreNotificationStack()
        capsuleContainer?.let {
            parent.findViewById<AodBurnInLayer>(R.id.burn_in_layer)?.removeView(it)
            parent.removeView(it)
        }
        floatingPanel?.let { parent.removeView(it) }
        drawerBackdrop?.let { parent.removeView(it) }
        drawerBackdrop = null
        capsuleContainer = null
        floatingPanel = null
        iconsContainer = null
        blurDrawable = null
        ambientAppearance = null
        shadeProgress = 0f
        hasMedia = false
        mediaPlayer = null
        iconsRefreshPending = false
    }

    override fun bindData(parent: ConstraintLayout) {
        mediaBinding?.dispose()
        mediaBinding =
            capsuleContainer?.repeatWhenAttached {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    combine(
                            mediaPipelineRepository.currentUserEntries,
                            mediaCarouselInteractor.allowMediaOnLockscreen,
                        ) { entries, allowed ->
                            if (allowed)
                                entries.values
                                    .sortedWith(
                                        compareByDescending<MediaData> { it.isPlaying == true }
                                            .thenByDescending { it.active }
                                            .thenByDescending { it.lastActive }
                                    )
                                    .firstOrNull()
                            else null
                        }
                        .collect {
                            mediaPlayer = it
                            hasMedia = it != null
                            refreshIcons()
                        }
                }
            }
        if (!listening) {
            batteryController.addCallback(batteryListener)
            configurationController.addCallback(configurationListener)
            visibilityProvider.addOnStateChangedListener(visibilityListener)
            statusBarStateController.addCallback(stateListener)
            shadeTransitionController.get().addCallback(shadeCallback)
            listening = true
        }
        capsuleContainer?.let {
            parent.findViewById<AodBurnInLayer>(R.id.burn_in_layer)?.addView(it)
        }
        updateCustomContainerMode()
        refreshIcons()
    }

    private fun updateCustomContainerMode() {
        // Keep the stack inside the closed drawer during AOD and pulses. Reparenting it
        // back to keyguard on doze entry exposes cards over the full-screen custom clock.
        // This section also renders the custom ambient notification tile.
        // isExpanded changes as a drag begins. Reparenting in that callback cancels
        // the gesture and can feed expansion changes back into the shade controller.
        // Transfer ownership only on the stable KEYGUARD -> SHADE_LOCKED transition.
        val inLockscreen = statusBarStateController.state == StatusBarState.KEYGUARD
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
                capsule.id,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                (MARGIN_BOTTOM_DP * density).toInt(),
            )
            constraintSet.connect(
                capsule.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                (MARGIN_START_DP * density).toInt(),
            )
        }

        drawerBackdrop?.let { backdrop ->
            constraintSet.setVisibilityMode(backdrop.id, ConstraintSet.VISIBILITY_MODE_IGNORE)
            constraintSet.constrainWidth(backdrop.id, ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(backdrop.id, ConstraintSet.MATCH_CONSTRAINT)
            for (edge in
                intArrayOf(
                    ConstraintSet.TOP,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.START,
                    ConstraintSet.END,
                )) {
                constraintSet.connect(backdrop.id, edge, ConstraintSet.PARENT_ID, edge)
            }
        }

        floatingPanel?.let { panel ->
            constraintSet.setVisibilityMode(panel.id, ConstraintSet.VISIBILITY_MODE_IGNORE)
            constraintSet.constrainWidth(panel.id, ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(panel.id, ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.connect(
                panel.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                (PANEL_SIDE_DP * density).toInt(),
            )
            constraintSet.connect(
                panel.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
                (PANEL_SIDE_DP * density).toInt(),
            )
            constraintSet.connect(
                panel.id,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                (context.resources.displayMetrics.heightPixels * PANEL_TOP_PERCENT).toInt(),
            )
            constraintSet.connect(
                panel.id,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                (PANEL_SIDE_DP * density).toInt(),
            )
        }
    }

    private fun panelColor(): Int {
        val night =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val color = if (night) 0xB8202A35.toInt() else 0xB8E8EAED.toInt()
        return if (batteryController.isPowerSave) color or 0xFF000000.toInt() else color
    }

    private fun createFloatingPanel(context: Context): LaunchableFrameLayout {
        val density = context.resources.displayMetrics.density
        return object : LaunchableFrameLayout(context) {
                private var blankSpaceGesture = false

                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    val handled = super.dispatchTouchEvent(event)
                    if (
                        event.actionMasked == MotionEvent.ACTION_UP ||
                            event.actionMasked == MotionEvent.ACTION_CANCEL
                    ) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    return handled
                }

                override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        val stack = notificationStackController.view
                        blankSpaceGesture =
                            event.y < 32f * density ||
                                stack.isBelowLastNotification(event.x - stack.x, event.y - stack.y)
                    }
                    // Card gestures remain owned by the native notification and media views.
                    return blankSpaceGesture
                }
            }
            .apply {
                id = View.generateViewId()
                visibility = View.INVISIBLE
                alpha = 0f
                elevation = 8f * density
                clipChildren = true
                clipToPadding = true
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = PANEL_RADIUS_DP * density
                        setColor(panelColor())
                    }
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                setOnTouchListener(drawerTouchListener())
                addView(
                    View(context).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        background =
                            GradientDrawable().apply {
                                cornerRadius = 2f * density
                                setColor(
                                    if (
                                        context.resources.configuration.uiMode and
                                            Configuration.UI_MODE_NIGHT_MASK ==
                                            Configuration.UI_MODE_NIGHT_YES
                                    )
                                        0x99FFFFFF.toInt()
                                    else 0x99000000.toInt()
                                )
                            }
                    },
                    FrameLayout.LayoutParams(
                            (32f * density).toInt(),
                            (4f * density).toInt(),
                            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                        )
                        .apply { topMargin = (12f * density).toInt() },
                )
                addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(view: View) {
                            val root = view.viewRootImpl ?: return
                            try {
                                val blur = root.createBackgroundBlurDrawable()
                                blur.setCornerRadius(PANEL_RADIUS_DP * density)
                                // Start inactive; updateBlurSurfaces supplies the animated
                                // region bounds before enabling the blur.
                                blur.setBlurRadius(0)
                                panelBlurDrawable?.setBlurRadius(0)
                                panelBlurDrawable = blur
                                blur.setColor(android.graphics.Color.TRANSPARENT)
                                view.background =
                                    object :
                                        LayerDrawable(
                                            arrayOf(
                                                blur,
                                                GradientDrawable().apply {
                                                    cornerRadius = PANEL_RADIUS_DP * density
                                                    setColor(panelColor())
                                                },
                                            )
                                        ) {
                                        override fun onBoundsChange(bounds: Rect) {
                                            super.onBoundsChange(bounds)
                                            // View may resize its background during draw,
                                            // after our pre-draw region update.
                                            blur.setBounds(view.clipBounds ?: bounds)
                                        }
                                    }
                            } catch (e: RuntimeException) {
                                Log.w(TAG, "Notification drawer blur unavailable", e)
                            }
                        }

                        override fun onViewDetachedFromWindow(view: View) {
                            panelBlurDrawable?.setBlurRadius(0)
                            panelBlurDrawable = null
                        }
                    }
                )
            }
    }

    private fun moveNotificationStackToFloatingPanel() {
        val panel = floatingPanel ?: return
        val stack =
            notificationStack ?: notificationStackController.view.also { notificationStack = it }
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
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                .apply { topMargin = (32f * context.resources.displayMetrics.density).toInt() },
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
            val params =
                originalNotificationLayoutParams
                    ?: ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
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

    private fun prepareDrawer(): Boolean {
        if (!customContainerMode || statusBarStateController.isDozing || shadeProgress > 0f) {
            return false
        }
        val panel = floatingPanel ?: return false
        val capsule = capsuleContainer ?: return false
        if (!panel.isLaidOut || !capsule.isLaidOut) return false
        moveNotificationStackToFloatingPanel()
        if (drawerProgress == 0f && !drawerTransition.isRunning) {
            // The burn-in Layer reapplies translations on every pre-draw. Let this
            // transition own the tile position until it returns to ambient placement.
            (capsule.parent as? ConstraintLayout)
                ?.findViewById<AodBurnInLayer>(R.id.burn_in_layer)
                ?.removeView(capsule)
            val density = context.resources.displayMetrics.density
            drawerTransition.prepare(
                capsule,
                panel,
                CAPSULE_RADIUS_DP * density,
                PANEL_RADIUS_DP * density,
            )
            setDrawerProgress(0f)
        }
        panel.visibility = View.VISIBLE
        notificationStackController.view.setLockscreenDrawerShowing(true)
        drawerBackdrop?.visibility = View.VISIBLE
        return true
    }

    private fun setDrawerProgress(progress: Float) {
        drawerProgress = progress.coerceIn(0f, 1f)
        drawerTransition.setPosition(drawerProgress)
        drawerBackdrop?.alpha = drawerProgress
        updateBlurSurfaces()
    }

    private fun animateDrawerTo(target: Float, onFinished: (() -> Unit)? = null) {
        drawerTransition.animateTo(drawerProgress, target, ::setDrawerProgress) {
            setDrawerProgress(target)
            if (target == 0f) {
                closeDrawerImmediately()
            } else {
                capsuleContainer?.visibility = View.INVISIBLE
                updateCapsuleAppearance()
            }
            onFinished?.invoke()
        }
    }

    private fun openDrawer() {
        if (!prepareDrawer()) return
        animateDrawerTo(1f)
    }

    private fun closeDrawer(onClosed: (() -> Unit)? = null) {
        if (drawerProgress == 0f && !drawerTransition.isRunning) {
            onClosed?.invoke()
            return
        }
        animateDrawerTo(0f, onClosed)
    }

    private fun closeDrawerImmediately() {
        drawerGestureGeneration++
        drawerTransition.reset()
        drawerProgress = 0f
        capsuleContainer?.let { capsule ->
            if (capsule.isAttachedToWindow) {
                (capsule.parent as? ConstraintLayout)
                    ?.findViewById<AodBurnInLayer>(R.id.burn_in_layer)
                    ?.addView(capsule)
            }
        }
        notificationStackController.view.setLockscreenDrawerShowing(false)
        drawerBackdrop?.visibility = View.INVISIBLE
        floatingPanel?.apply {
            alpha = 0f
            visibility = View.INVISIBLE
        }
        refreshPendingIcons()
        updateCapsuleAppearance()
    }

    private fun dragRange(): Float =
        ((floatingPanel?.height ?: 0) * 0.6f).coerceAtLeast(
            120f * context.resources.displayMetrics.density
        )

    private fun refreshPendingIcons() {
        if (!iconsRefreshPending) return
        iconsRefreshPending = false
        refreshIcons()
    }

    private fun updateCapsuleAppearance() {
        val capsule = capsuleContainer ?: return
        updateBlurSurfaces()
        if (drawerTransition.isRunning) return
        if (statusBarStateController.isDozing) {
            capsule.alpha = 1f
            capsule.isClickable = false
            capsule.isFocusable = false
            capsule.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            return
        }
        val progress = maxOf(drawerProgress, shadeProgress)
        capsule.alpha = (1f - 2f * progress).coerceAtLeast(0f)
        capsule.isClickable = progress == 0f
        capsule.isFocusable = progress == 0f
        capsule.importantForAccessibility =
            if (progress == 0f) {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            } else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun addRawMovement(tracker: VelocityTracker?, event: MotionEvent) {
        if (tracker == null) return
        // Both animated surfaces move under the pointer; velocity must use screen space.
        val raw = MotionEvent.obtain(event)
        raw.setLocation(event.rawX, event.rawY)
        tracker.addMovement(raw)
        raw.recycle()
    }

    private fun drawerTouchListener(): View.OnTouchListener {
        val config = ViewConfiguration.get(context)
        val slop = config.scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var startProgress = 0f
        var dragging = false
        var velocity: VelocityTracker? = null
        var gestureGeneration = -1
        return View.OnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                gestureGeneration = drawerGestureGeneration
            }
            if (
                !customContainerMode ||
                    statusBarStateController.isDozing ||
                    gestureGeneration != drawerGestureGeneration
            ) {
                view.parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                velocity?.recycle()
                velocity = null
                return@OnTouchListener gestureGeneration != drawerGestureGeneration
            }
            addRawMovement(velocity, event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startProgress = drawerProgress
                    dragging = false
                    velocity?.recycle()
                    velocity = VelocityTracker.obtain().also { addRawMovement(it, event) }
                    drawerTransition.beginInteraction()
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (
                        !dragging &&
                            kotlin.math.abs(dy) > slop &&
                            kotlin.math.abs(dy) > kotlin.math.abs(event.rawX - startX)
                    ) {
                        dragging = true
                    }
                    if (dragging) setDrawerProgress(startProgress - dy / dragRange())
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    val dy = event.rawY - startY
                    val dx = event.rawX - startX
                    velocity?.computeCurrentVelocity(1000)
                    val speed = velocity?.yVelocity ?: 0f
                    velocity?.recycle()
                    velocity = null
                    when {
                        event.actionMasked == MotionEvent.ACTION_CANCEL -> animateDrawerTo(1f)
                        dragging && dy < -48f * context.resources.displayMetrics.density -> {
                            closeDrawer {
                                if (
                                    customContainerMode &&
                                        statusBarStateController.state == StatusBarState.KEYGUARD &&
                                        !statusBarStateController.isDozing
                                ) {
                                    keyguardViewManager
                                        .get()
                                        .showPrimaryBouncer(
                                            true,
                                            "custom lockscreen drawer swipe up",
                                        )
                                }
                            }
                        }
                        dragging -> {
                            val open =
                                if (kotlin.math.abs(speed) > config.scaledMinimumFlingVelocity) {
                                    speed < 0f
                                } else drawerProgress > 0.5f
                            animateDrawerTo(if (open) 1f else 0f)
                        }
                        kotlin.math.abs(dx) < slop && kotlin.math.abs(dy) < slop -> closeDrawer()
                        else -> animateDrawerTo(1f)
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun createCapsuleView(context: Context): LaunchableFrameLayout {
        val density = context.resources.displayMetrics.density
        return LaunchableFrameLayout(context).apply {
            id = View.generateViewId()
            background = fallbackBackground(density)
            val padding = (CAPSULE_PADDING_DP * density).toInt()
            setPadding(padding, padding, padding, padding)
            setOnClickListener { if (drawerProgress > 0.5f) closeDrawer() else openDrawer() }
            var startX = 0f
            var startY = 0f
            var startProgress = 0f
            var dragging = false
            var velocity: VelocityTracker? = null
            var gestureGeneration = -1
            val config = ViewConfiguration.get(context)
            val slop = config.scaledTouchSlop
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    gestureGeneration = drawerGestureGeneration
                }
                if (
                    statusBarStateController.isDozing ||
                        !customContainerMode ||
                        gestureGeneration != drawerGestureGeneration
                ) {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    dragging = false
                    velocity?.recycle()
                    velocity = null
                    return@setOnTouchListener gestureGeneration != drawerGestureGeneration
                }
                addRawMovement(velocity, event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        startProgress = drawerProgress
                        if (drawerTransition.isRunning) drawerTransition.beginInteraction()
                        dragging = false
                        velocity?.recycle()
                        velocity = VelocityTracker.obtain().also { addRawMovement(it, event) }
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val distance = startY - event.rawY
                        if (
                            !dragging &&
                                distance > slop &&
                                distance > kotlin.math.abs(event.rawX - startX) &&
                                prepareDrawer()
                        ) {
                            dragging = true
                            drawerTransition.beginInteraction()
                        }
                        if (dragging) setDrawerProgress(startProgress + distance / dragRange())
                        true
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        velocity?.computeCurrentVelocity(1000)
                        val speed = velocity?.yVelocity ?: 0f
                        velocity?.recycle()
                        velocity = null
                        if (dragging) {
                            val open =
                                event.actionMasked != MotionEvent.ACTION_CANCEL &&
                                    if (
                                        kotlin.math.abs(speed) > config.scaledMinimumFlingVelocity
                                    ) {
                                        speed < 0f
                                    } else drawerProgress > 0.5f
                            animateDrawerTo(if (open) 1f else 0f)
                        } else if (
                            event.actionMasked == MotionEvent.ACTION_UP &&
                                kotlin.math.abs(event.rawX - startX) < slop &&
                                kotlin.math.abs(event.rawY - startY) < slop
                        ) {
                            view.performClick()
                        } else if (drawerTransition.isRunning) {
                            animateDrawerTo(if (drawerProgress > 0.5f) 1f else 0f)
                        }
                        dragging = false
                        true
                    }
                    else -> true
                }
            }
            isClickable = true
            isFocusable = true
            addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) {
                        ambientAppearance = null
                        updateCapsuleSurface()
                    }

                    override fun onViewDetachedFromWindow(view: View) {
                        velocity?.recycle()
                        velocity = null
                        closeDrawerImmediately()
                        ambientAppearance = null
                        blurDrawable?.setBlurRadius(0)
                        blurDrawable = null
                    }
                }
            )
            val icons =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
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
            val night =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            val color = if (night) 0xCC202124.toInt() else 0xCCE8EAED.toInt()
            setColor(if (batteryController.isPowerSave) color or 0xFF000000.toInt() else color)
        }

    private fun updateCapsuleSurface() {
        val capsule = capsuleContainer ?: return
        val ambient = statusBarStateController.isDozing
        if (ambientAppearance == ambient) return
        ambientAppearance = ambient
        blurDrawable?.setBlurRadius(0)
        blurDrawable = null
        val density = context.resources.displayMetrics.density
        if (ambient) {
            // AOD never samples the wallpaper or keeps a translucent blur surface active.
            capsule.background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = CAPSULE_RADIUS_DP * density
                    setColor(android.graphics.Color.BLACK)
                    setStroke(density.toInt().coerceAtLeast(1), 0xFF888888.toInt())
                }
        } else {
            capsule.background = fallbackBackground(density)
            if (!batteryController.isPowerSave) installBlur(capsule, density)
        }
    }

    private fun installBlur(view: View, density: Float) {
        val root = view.viewRootImpl ?: return
        try {
            val drawable = root.createBackgroundBlurDrawable()
            drawable.setCornerRadius(CAPSULE_RADIUS_DP * density)
            drawable.setBlurRadius(0)
            val night =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            drawable.setColor(android.graphics.Color.TRANSPARENT)
            blurDrawable = drawable
            view.background =
                LayerDrawable(
                    arrayOf(
                        drawable,
                        GradientDrawable().apply {
                            cornerRadius = CAPSULE_RADIUS_DP * density
                            setColor(if (night) 0xB8202124.toInt() else 0xB8E8EAED.toInt())
                        },
                    )
                )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Background blur is unavailable", e)
        }
    }

    /** Background blur regions do not inherit RenderNode alpha or ancestor visibility. */
    private fun updateBlurSurfaces() {
        val active =
            customContainerMode &&
                !statusBarStateController.isDozing &&
                !batteryController.isPowerSave
        val atRest = !drawerTransition.isRunning
        updateBlurSurface(
            capsuleContainer,
            blurDrawable,
            active && atRest && drawerProgress == 0f && shadeProgress == 0f,
            18f,
        )
        updateBlurSurface(
            floatingPanel,
            panelBlurDrawable,
            active && (drawerTransition.isRunning || drawerProgress > 0f) && shadeProgress == 0f,
            24f,
        )
    }

    private fun updateBlurSurface(
        view: View?,
        drawable: BackgroundBlurDrawable?,
        enabled: Boolean,
        radiusDp: Float,
    ) {
        if (drawable == null) return
        var alpha =
            if (
                enabled &&
                    view != null &&
                    view.isAttachedToWindow &&
                    view.windowVisibility == View.VISIBLE
            )
                1f
            else 0f
        var ancestor = view
        while (ancestor != null && alpha > 0f) {
            if (ancestor.visibility != View.VISIBLE) {
                alpha = 0f
                break
            }
            alpha *= ancestor.alpha * ancestor.transitionAlpha
            ancestor = ancestor.parent as? View
        }
        // Explicitly remove inactive regions from the aggregator. Relying on the
        // RenderNode's positionLost callback leaves stale blur during keyguard fades.
        val visible = alpha > 0f
        drawable.setVisible(visible, false)
        drawable.setAlpha((255f * alpha.coerceIn(0f, 1f)).toInt())
        if (visible && view != null) {
            // View clipping does not constrain SurfaceFlinger's blur region. Give
            // the drawable the actual revealed bounds on every frame, including
            // reverse drags and layout changes, so blur never precedes the panel.
            // Set bounds after visibility/alpha to rebuild the drawable cutout path.
            val clip = view.clipBounds
            drawable.setBounds(
                clip?.left ?: 0,
                clip?.top ?: 0,
                clip?.right ?: view.width,
                clip?.bottom ?: view.height,
            )
        }
        drawable.setBlurRadius(
            if (alpha > 0f) (radiusDp * context.resources.displayMetrics.density).toInt() else 0
        )
    }

    private fun refreshIcons() {
        val icons = iconsContainer ?: return
        // Keep source bounds stable until the drawer is closed, including reverse motion.
        // The final refresh reads the latest pipeline snapshot, so no update is lost.
        if (drawerTransition.isRunning || drawerProgress > 0f) {
            iconsRefreshPending = true
            return
        }
        updateCapsuleSurface()
        icons.removeAllViews()
        val shownPackages = mutableSetOf<String>()
        for (entry in renderedNotifications) {
            if (icons.childCount >= MAX_ICONS - (if (hasMedia) 1 else 0)) break
            if (visibilityProvider.shouldHideNotification(entry)) continue
            val pkg = entry.sbn.packageName
            if (hasMedia && pkg == mediaPlayer?.packageName) continue
            if (!shownPackages.add(pkg)) continue
            val icon = getNotificationIcon(entry) ?: continue
            val density = context.resources.displayMetrics.density
            icons.addView(
                ImageView(context).apply {
                    setImageDrawable(icon)
                    layoutParams =
                        LinearLayout.LayoutParams(
                            (ICON_SIZE_DP * density).toInt(),
                            (ICON_SIZE_DP * density).toInt(),
                        )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = entry.sbn.packageName
                    if (statusBarStateController.isDozing) {
                        imageTintList =
                            android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                    }
                }
            )
        }
        // Reserve a slot for the media control, including paused/resumable sessions.
        // A dedicated note stays visible even when the app supplies an empty icon.
        if (hasMedia) {
            val size = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
            val player = mediaPlayer
            icons.addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_lockscreen_media_note)
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription =
                        player?.app ?: context.getString(R.string.controls_media_title)
                    imageTintList =
                        android.content.res.ColorStateList.valueOf(
                            if (
                                statusBarStateController.isDozing ||
                                    context.resources.configuration.uiMode and
                                        Configuration.UI_MODE_NIGHT_MASK ==
                                        Configuration.UI_MODE_NIGHT_YES
                            )
                                android.graphics.Color.WHITE
                            else android.graphics.Color.DKGRAY
                        )
                }
            )
        }
        // Gaps belong between icons, never after the last icon. A single icon plus
        // symmetric capsule padding therefore measures as an exact square.
        for (index in 0 until icons.childCount) {
            val child = icons.getChildAt(index)
            val params = child.layoutParams as LinearLayout.LayoutParams
            params.marginEnd =
                if (index < icons.childCount - 1) {
                    (ICON_PADDING_DP * context.resources.displayMetrics.density).toInt()
                } else 0
            child.layoutParams = params
        }
        updateCapsuleVisibility()
        updateCapsuleAppearance()
    }

    private fun getNotificationIcon(entry: NotificationEntry): Drawable? {
        val statusIcon = entry.icons.statusBarIcon?.drawable
        statusIcon?.constantState?.newDrawable()?.mutate()?.let {
            return it
        }
        val notifIcon =
            try {
                entry.sbn.notification.smallIcon?.loadDrawable(context)
            } catch (_: RuntimeException) {
                null
            }
        return notifIcon ?: getAppIcon(entry.sbn)
    }

    private fun getAppIcon(sbn: StatusBarNotification): Drawable? =
        try {
            packageManager.getApplicationIcon(packageManager.getApplicationInfo(sbn.packageName, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    private fun updateCapsuleVisibility() {
        capsuleContainer?.visibility =
            if (customContainerMode && (iconsContainer?.childCount ?: 0) > 0) {
                if (drawerProgress == 1f && !drawerTransition.isRunning) View.INVISIBLE
                else View.VISIBLE
            } else {
                View.GONE
            }
        updateBlurSurfaces()
    }
}
