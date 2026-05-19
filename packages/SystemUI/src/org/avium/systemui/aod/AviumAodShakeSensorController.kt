/*
 * Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.avium.systemui.aod

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import java.io.PrintWriter
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

@SysUISingleton
class AviumAodShakeSensorController @Inject constructor(
    context: Context,
    @Main private val mainHandler: Handler,
) {
    companion object {
        private const val SHAKE_COOLDOWN_MS = 200L
        private const val SHAKE_DELTA_THRESHOLD = 0.45f
        private const val SHAKE_TOTAL_THRESHOLD = 0.0f
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var listening = false
    private var lastShakeElapsed = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var haveLastSample = false
    private var onShake: (() -> Unit)? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            if (!haveLastSample) {
                lastX = x
                lastY = y
                lastZ = z
                haveLastSample = true
                return
            }

            val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
            val total = sqrt(x * x + y * y + z * z)
            lastX = x
            lastY = y
            lastZ = z

            val now = SystemClock.elapsedRealtime()
            if (delta >= SHAKE_DELTA_THRESHOLD &&
                    total >= SHAKE_TOTAL_THRESHOLD &&
                    now - lastShakeElapsed >= SHAKE_COOLDOWN_MS) {
                lastShakeElapsed = now
                onShake?.invoke()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun setOnShakeListener(listener: (() -> Unit)?) {
        onShake = listener
    }

    fun setListening(enabled: Boolean) {
        if (enabled == listening) return
        if (enabled) {
            val sensor = accelerometer ?: return
            resetSamples()
            sensorManager?.registerListener(
                sensorListener,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
                mainHandler,
            )
            listening = true
        } else {
            sensorManager?.unregisterListener(sensorListener)
            listening = false
            resetSamples()
        }
    }

    // fun dump(pw: PrintWriter) {
    //     pw.println(" AviumAodShakeSensorController:")
    //     pw.println("  available=${accelerometer != null}")
    //     pw.println("  listening=$listening")
    // }

    private fun resetSamples() {
        haveLastSample = false
    }
}
