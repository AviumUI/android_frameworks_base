/*
 * Copyright (C) 2025-2026 The AviumUI Project
 * Copyright (C) 2025 The AxionAOSP Project
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
package org.avium.systemui.mistouch

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.phone.BiometricUnlockController
import com.android.systemui.statusbar.policy.KeyguardStateController
import javax.inject.Inject

@SysUISingleton
class MistouchPreventionWindowController @Inject constructor(
    private val context: Context,
    private val keyguardStateController: KeyguardStateController,
    private val keyguardUpdateMonitor: KeyguardUpdateMonitor,
    private val biometricUnlockController: BiometricUnlockController,
    private val statusBarStateController: StatusBarStateController,
    private val wakefulnessLifecycle: WakefulnessLifecycle,
): CoreStartable, MistouchInteractor.MistouchEvents {

    companion object {
        private const val TAG = "MistouchPreventionWindowController"
        private const val POCKET_SENSOR = Sensor.TYPE_PROXIMITY
        private const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_NORMAL
        private const val GRAVITY_THRESHOLD = -0.7f
        private const val MIN_INCLINATION = 75
        private const val MAX_INCLINATION = 100
        private const val DISABLE_DURATION = 5000L
        private const val KEY_MISTOUCH_PREVENTION = "nt_mistouch_prevention_enable"
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val proximitySensor = sensorManager?.getDefaultSensor(POCKET_SENSOR, true)
    private val accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private val preventionView = MistouchPreventionView(context)

    private var registered = false
    private var windowAdded = false
    private var disableEventInMillis = 0L
    private var sensorNearWhenSleep = false
    private var isProxNear = false
    private var isAccelInPocket = false
    private var mistouchWindowVisible = false

    private val mistouchPreventionEnabled: Boolean
        get() = Settings.Secure.getIntForUser(
            context.contentResolver,
            KEY_MISTOUCH_PREVENTION, 1, UserHandle.USER_CURRENT
        ) == 1

    private val keyguardShowing: Boolean
        get() = keyguardStateController.isShowing

    private val dozing: Boolean
        get() = statusBarStateController.isDozing

    private val volumeKeyCallback = object : MistouchPreventionView.VolumeKeyCallback {
        override fun onVolumeUpPressed() {
            disable()
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return

            if (event.sensor.type == POCKET_SENSOR) {
                isProxNear = event.values[0] < 1.0f
                updateState()
            } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val g = event.values.clone()
                val magnitude = Math.sqrt(
                    (g[0] * g[0] + g[1] * g[1] + g[2] * g[2]).toDouble()
                )

                g[0] = (g[0] / magnitude).toFloat()
                g[1] = (g[1] / magnitude).toFloat()
                g[2] = (g[2] / magnitude).toFloat()

                val inclination = Math.round(Math.toDegrees(Math.acos(g[2].toDouble()))).toInt()

                val isGravityInPocket = g[1] < GRAVITY_THRESHOLD
                val isInclinationInPocket = inclination in MIN_INCLINATION..MAX_INCLINATION

                isAccelInPocket = isGravityInPocket && isInclinationInPocket
                updateState()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun updateState() {
        if (isProxNear || isAccelInPocket) showWindow() else hideWindow()
    }

    private val keyguardCallback = object : KeyguardUpdateMonitorCallback() {
        override fun onUserSwitching(userId: Int) {
            sensorNearWhenSleep = false
            disable()
        }

        override fun onUserSwitchComplete(userId: Int) {
            if (mistouchPreventionEnabled && keyguardShowing) {
                enable()
            }
        }

        override fun onKeyguardVisibilityChanged(showing: Boolean) {
            handleKeyguardShowingChanged(showing)
        }

        override fun onStartedWakingUp() {
            handleStartedWakingUp()
        }

        override fun onStartedGoingToSleep(why: Int) {
            handleScreenTurnedOff()
        }
    }

    private val wakefulnessObserver = object : WakefulnessLifecycle.Observer {
        override fun onStartedGoingToSleep() {
            handleScreenTurnedOff()
        }

        override fun onFinishedGoingToSleep() {}

        override fun onStartedWakingUp() {
            handleStartedWakingUp()
        }

        override fun onFinishedWakingUp() {}
    }

    private val statusBarStateListener = object : StatusBarStateController.StateListener {
        override fun onDozingChanged(isDozing: Boolean) {
            handleDozingChanged(isDozing)
        }
    }

    override fun start() {
        preventionView.setVisibility(View.INVISIBLE)
        preventionView.addCallback(volumeKeyCallback)

        if (proximitySensor != null || accelerometerSensor != null) {
            keyguardUpdateMonitor.registerCallback(keyguardCallback)
            wakefulnessLifecycle.addObserver(wakefulnessObserver)
            statusBarStateController.addCallback(statusBarStateListener)
            MistouchInteractor.get().addListener(this)
        }
    }

    private fun enable() {
        if (proximitySensor == null && accelerometerSensor == null) return

        if (!mistouchPreventionEnabled || registered) return
        if (System.currentTimeMillis() - disableEventInMillis < DISABLE_DURATION) return

        proximitySensor?.let { sensorManager?.registerListener(sensorListener, it, SENSOR_DELAY) }
        accelerometerSensor?.let { sensorManager?.registerListener(sensorListener, it, SENSOR_DELAY) }
        registered = true
        addViewToWindow()
    }

    private fun disable() {
        if (proximitySensor == null && accelerometerSensor == null) return

        removeViewFromWindow()

        if (registered) {
            sensorManager?.unregisterListener(sensorListener)
            registered = false
        }
    }

    private fun addViewToWindow() {
        if (windowAdded) {
            return
        }

        preventionView.visibility = View.INVISIBLE

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            2026,
            524544,
            -2
        ).apply {
            privateFlags = privateFlags or 80
            privateFlags = privateFlags or 536870912
            layoutInDisplayCutoutMode = 3
            fitInsetsTypes = 0
            setTitle("MistouchPreve")
        }

        windowManager?.addView(preventionView, layoutParams)
        windowAdded = true
    }

    private fun removeViewFromWindow() {
        if (windowAdded) {
            windowManager?.removeView(preventionView)
            windowAdded = false
            notifyMistouchVisibilityChanged(false)
        }
    }

    private fun showWindow() {
        preventionView.addCallback(volumeKeyCallback)
        preventionView.visibility = View.VISIBLE
        preventionView.requestFocus()
        notifyMistouchVisibilityChanged(true)
    }

    private fun hideWindow() {
        preventionView.visibility = View.INVISIBLE
        preventionView.removeCallback(volumeKeyCallback)
        notifyMistouchVisibilityChanged(false)
    }

    private fun notifyMistouchVisibilityChanged(visible: Boolean) {
        if (mistouchWindowVisible == visible) return
        mistouchWindowVisible = visible
        MistouchInteractor.get().handleMistouchVisibilityChanged(visible)
    }

    override fun onDoubleTapPowerGesture() {
        disableEventInMillis = System.currentTimeMillis()
        disable()
    }

    override fun onAffordanceLongClick() {
        disableEventInMillis = System.currentTimeMillis()
        disable()
    }

    override fun onEmergencyButtonClick() {
        disableEventInMillis = System.currentTimeMillis()
        disable()
    }

    private fun handleDozingChanged(isDozing: Boolean) {
        if (isDozing) enable()
    }

    private fun handleKeyguardShowingChanged(showing: Boolean) {
        val occluded = keyguardStateController.isOccluded

        if (showing) {
            enable()
        } else if (!showing && !occluded) {
            disable()
        } else if (occluded && System.currentTimeMillis() - disableEventInMillis < DISABLE_DURATION) {
            disable()
        }
    }

    private fun handleStartedWakingUp() {
        val isWakeAndUnlock = biometricUnlockController.isWakeAndUnlock
        if (keyguardShowing && !isWakeAndUnlock) {
            enable()
        }
        sensorNearWhenSleep = false
    }

    private fun handleScreenTurnedOff() {
        sensorNearWhenSleep = windowAdded
        disable()
    }
}
