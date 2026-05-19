/*
 * Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.avium.systemui.aod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.display.AmbientDisplayConfiguration
import android.os.Handler
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import org.avium.systemui.mistouch.MistouchInteractor
import java.io.PrintWriter
import java.util.Calendar
import javax.inject.Inject

@SysUISingleton
class AviumAodController @Inject constructor(
    private val context: Context,
    @Main private val mainHandler: Handler,
    private val ambientDisplayConfiguration: AmbientDisplayConfiguration,
    private val statusBarStateController: StatusBarStateController,
    private val userTracker: UserTracker,
    private val batteryController: BatteryController,
    private val shakeSensorController: AviumAodShakeSensorController,
) : CoreStartable, MistouchInteractor.MistouchEvents {

    interface Callback {
        fun onAviumAodPolicyChanged() {}
    }

    companion object {
        private const val DEFAULT_START_TIME = "07:00"
        private const val DEFAULT_END_TIME = "22:00"
        private const val SHAKE_SHOW_DURATION_MS = 10_000L
    }

    private val resolver = context.contentResolver
    private val callbacks = mutableSetOf<Callback>()

    private var started = false
    private var scheduleEnabled = false
    private var shakeEnabled = false
    private var startMinute = parseTime(DEFAULT_START_TIME, DEFAULT_START_TIME)
    private var endMinute = parseTime(DEFAULT_END_TIME, DEFAULT_END_TIME)
    private var mistouchVisible = false
    private var dozing = false
    private var shakeWindowUntilElapsed = 0L

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            handleSettingsChanged()
        }
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handlePolicyEnvironmentChanged()
            updateShakeSensor()
        }
    }

    private val statusBarStateListener = object : StatusBarStateController.StateListener {
        override fun onDozingChanged(isDozing: Boolean) {
            dozing = isDozing
            if (dozing && isShakeModeAvailable()) {
                showShakeWindow()
            } else if (!dozing) {
                clearShakeWindow()
            }
            updateShakeSensor()
            dispatchPolicyChanged()
        }
    }

    private val batteryStateChangeCallback = object : BatteryStateChangeCallback {
        override fun onPowerSaveChanged(isPowerSave: Boolean) {
            handlePolicyEnvironmentChanged()
            updateShakeSensor()
        }
    }

    private val clearShakeWindowRunnable = Runnable {
        clearShakeWindow()
        dispatchPolicyChanged()
    }

    init {
        shakeSensorController.setOnShakeListener(::handleShakeDetected)
    }

    override fun start() {
        if (started) return
        started = true
        readSettings()
        registerSettingsObserver()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(timeReceiver, filter)
        statusBarStateController.addCallback(statusBarStateListener)
        batteryController.addCallback(batteryStateChangeCallback)
        MistouchInteractor.get().addListener(this)
        dozing = statusBarStateController.isDozing
        if (dozing && isShakeModeAvailable()) {
            showShakeWindow()
        }
        updateShakeSensor()
    }

    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    fun isAodAllowed(baseAlwaysOn: Boolean): Boolean {
        if (!started) {
            readSettings()
        }
        if (!baseAlwaysOn || mistouchVisible) return false
        if (!isScheduleWindowAllowed()) return false
        if (shakeEnabled) return isShakeWindowActive()
        return true
    }

    fun shouldShowAodForBaseConfig(baseAlwaysOn: Boolean): Boolean = isAodAllowed(baseAlwaysOn)

    fun onDozeMachineStarted() {
        dozing = true
        if (isShakeModeAvailable()) {
            showShakeWindow()
        }
        updateShakeSensor()
        dispatchPolicyChanged()
    }

    fun onDozeMachineFinished() {
        dozing = false
        clearShakeWindow()
        updateShakeSensor()
        dispatchPolicyChanged()
    }

    // fun dump(pw: PrintWriter) {
    //     pw.println("AviumAodController:")
    //     pw.println(" scheduleEnabled=$scheduleEnabled")
    //     pw.println(" shakeEnabled=$shakeEnabled")
    //     pw.println(" startMinute=$startMinute")
    //     pw.println(" endMinute=$endMinute")
    //     pw.println(" mistouchVisible=$mistouchVisible")
    //     pw.println(" dozing=$dozing")
    //     pw.println(" shakeWindowActive=${isShakeWindowActive()}")
    //     shakeSensorController.dump(pw)
    // }

    override fun onMistouchVisibilityChanged(visible: Boolean) {
        if (mistouchVisible == visible) return
        mistouchVisible = visible
        if (visible) {
            clearShakeWindow()
        } else if (dozing && isShakeModeAvailable()) {
            showShakeWindow()
        }
        updateShakeSensor()
        dispatchPolicyChanged()
    }

    private fun handleSettingsChanged() {
        readSettings()
        if (dozing && isShakeModeAvailable()) {
            showShakeWindow()
        } else {
            clearShakeWindow()
        }
        updateShakeSensor()
        dispatchPolicyChanged()
    }

    private fun handlePolicyEnvironmentChanged() {
        if (!isShakeModeAvailable()) {
            clearShakeWindow()
            dispatchPolicyChanged()
        }
    }

    private fun registerSettingsObserver() {
        val uris = arrayOf(
            Settings.Secure.getUriFor(Settings.Secure.AVIUM_AOD_SCHEDULE_ENABLED),
            Settings.Secure.getUriFor(Settings.Secure.AVIUM_AOD_SCHEDULE_START_TIME),
            Settings.Secure.getUriFor(Settings.Secure.AVIUM_AOD_SCHEDULE_END_TIME),
            Settings.Secure.getUriFor(Settings.Secure.AVIUM_AOD_SHAKE_ENABLED),
            Settings.Secure.getUriFor(Settings.Secure.DOZE_ALWAYS_ON),
        )
        uris.forEach { uri ->
            resolver.registerContentObserver(uri, false, settingsObserver, UserHandle.USER_SYSTEM)
        }
    }

    private fun readSettings() {
        scheduleEnabled = Settings.Secure.getIntForUser(
            resolver,
            Settings.Secure.AVIUM_AOD_SCHEDULE_ENABLED,
            0,
            UserHandle.USER_SYSTEM,
        ) == 1
        shakeEnabled = Settings.Secure.getIntForUser(
            resolver,
            Settings.Secure.AVIUM_AOD_SHAKE_ENABLED,
            0,
            UserHandle.USER_SYSTEM,
        ) == 1
        startMinute = parseTime(
            Settings.Secure.getStringForUser(
                resolver,
                Settings.Secure.AVIUM_AOD_SCHEDULE_START_TIME,
                UserHandle.USER_SYSTEM,
            ) ?: DEFAULT_START_TIME,
            DEFAULT_START_TIME,
        )
        endMinute = parseTime(
            Settings.Secure.getStringForUser(
                resolver,
                Settings.Secure.AVIUM_AOD_SCHEDULE_END_TIME,
                UserHandle.USER_SYSTEM,
            ) ?: DEFAULT_END_TIME,
            DEFAULT_END_TIME,
        )
    }

    private fun updateShakeSensor() {
        shakeSensorController.setListening(dozing && isShakeModeAvailable())
    }

    private fun handleShakeDetected() {
        if (!isShakeModeAvailable()) return
        showShakeWindow()
        dispatchPolicyChanged()
    }

    private fun showShakeWindow() {
        val now = SystemClock.elapsedRealtime()
        shakeWindowUntilElapsed = now + SHAKE_SHOW_DURATION_MS
        mainHandler.removeCallbacks(clearShakeWindowRunnable)
        mainHandler.postDelayed(clearShakeWindowRunnable, SHAKE_SHOW_DURATION_MS)
    }

    private fun dispatchPolicyChanged() {
        callbacks.toList().forEach { it.onAviumAodPolicyChanged() }
    }

    private fun clearShakeWindow() {
        shakeWindowUntilElapsed = 0L
        mainHandler.removeCallbacks(clearShakeWindowRunnable)
    }

    private fun isShakeWindowActive(): Boolean =
        shakeWindowUntilElapsed > SystemClock.elapsedRealtime()

    private fun isShakeModeAvailable(): Boolean =
        shakeEnabled &&
            !mistouchVisible &&
            isScheduleWindowAllowed() &&
            ambientDisplayConfiguration.alwaysOnEnabled(userTracker.userId) &&
            !batteryController.isAodPowerSave

    private fun isScheduleWindowAllowed(): Boolean =
        !scheduleEnabled || isWithinSchedule()

    private fun isWithinSchedule(): Boolean {
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return if (startMinute == endMinute) {
            true
        } else if (startMinute < endMinute) {
            minute in startMinute until endMinute
        } else {
            minute >= startMinute || minute < endMinute
        }
    }

    private fun parseTime(value: String, defaultValue: String): Int {
        val parts = value.split(":")
        if (parts.size != 2) return parseTimeUnchecked(defaultValue)
        val hour = parts[0].toIntOrNull()
        val minute = parts[1].toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            return parseTimeUnchecked(defaultValue)
        }
        return hour * 60 + minute
    }

    private fun parseTimeUnchecked(value: String): Int {
        val parts = value.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
