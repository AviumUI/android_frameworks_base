/* Copyright (C) 2026 The AviumUI Project */
package org.avium.systemui.aod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import org.avium.systemui.lockscreen.CustomLockscreenRepository
import java.io.PrintWriter
import javax.inject.Inject

@SysUISingleton
class AviumAodStyleController @Inject constructor(
    private val context: Context,
    private val registry: AviumAodStyleRegistry,
) : CoreStartable {
    private var started = false
    private var aodVisible = false
    private var activeStyle: AviumAodStyle? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_TIME_TICK -> activeStyle?.onTimeTick()
                ACTION_STYLE_CHANGED,
                CustomLockscreenRepository.ACTION_SETTINGS_CHANGED -> if (aodVisible) selectStyle(true)
            }
        }
    }

    override fun start() {
        if (started) return
        started = true
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(ACTION_STYLE_CHANGED)
            addAction(CustomLockscreenRepository.ACTION_SETTINGS_CHANGED)
        }, Context.RECEIVER_EXPORTED)
    }

    fun onAodStarted() {
        if (aodVisible) return
        aodVisible = true
        selectStyle(false)
    }

    fun onAodStopped() {
        if (!aodVisible) return
        aodVisible = false
        activeStyle?.onAodStopped()
        activeStyle = null
    }

    fun dump(pw: PrintWriter) {
        pw.println("AviumAodStyleController:")
        pw.println("  aodVisible=$aodVisible activeStyle=${activeStyle?.id}")
        registry.dump(pw)
    }

    private fun selectStyle(restart: Boolean) {
        val next = registry.activeStyle()
        if (!restart && activeStyle === next) return
        activeStyle?.onAodStopped()
        activeStyle = next
        next.onAodStarted()
    }

    companion object { const val ACTION_STYLE_CHANGED = "org.avium.systemui.aod.STYLE_CHANGED" }
}
