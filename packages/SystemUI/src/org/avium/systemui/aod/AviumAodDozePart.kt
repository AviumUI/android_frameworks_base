/*
 * Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.avium.systemui.aod

import android.hardware.display.AmbientDisplayConfiguration
import com.android.systemui.doze.DozeMachine
import com.android.systemui.doze.dagger.DozeScope
import com.android.systemui.settings.UserTracker
import java.io.PrintWriter
import javax.inject.Inject

@DozeScope
class AviumAodDozePart @Inject constructor(
    private val controller: AviumAodController,
    private val styleController: AviumAodStyleController,
    private val ambientDisplayConfiguration: AmbientDisplayConfiguration,
    private val userTracker: UserTracker,
) : DozeMachine.Part, AviumAodController.Callback {

    private var machine: DozeMachine? = null

    override fun setDozeMachine(dozeMachine: DozeMachine) {
        machine = dozeMachine
    }

    override fun transitionTo(oldState: DozeMachine.State, newState: DozeMachine.State) {
        when (newState) {
            DozeMachine.State.INITIALIZED -> {
                controller.addCallback(this)
                controller.onDozeMachineStarted()
            }
            DozeMachine.State.DOZE_AOD,
            DozeMachine.State.DOZE_AOD_DOCKED,
            DozeMachine.State.DOZE_AOD_MINMODE,
            DozeMachine.State.DOZE_AOD_PAUSING,
            DozeMachine.State.DOZE_AOD_PAUSED,
            DozeMachine.State.DOZE_REQUEST_PULSE,
            DozeMachine.State.DOZE_PULSING,
            DozeMachine.State.DOZE_PULSING_BRIGHT,
            DozeMachine.State.DOZE_PULSING_WITHOUT_UI,
            DozeMachine.State.DOZE_PULSING_AUTH_UI -> styleController.onAodStarted()
            DozeMachine.State.DOZE,
            DozeMachine.State.DOZE_PULSE_DONE,
            DozeMachine.State.DOZE_SUSPEND_TRIGGERS -> styleController.onAodStopped()
            DozeMachine.State.FINISH -> {
                styleController.onAodStopped()
                controller.removeCallback(this)
                controller.onDozeMachineFinished()
            }
            else -> Unit
        }
    }

    override fun destroy() {
        styleController.onAodStopped()
        controller.removeCallback(this)
    }

    override fun onAviumAodPolicyChanged() {
        val dozeMachine = machine ?: return
        if (dozeMachine.isExecutingTransition()) return
        val state = dozeMachine.getState() ?: return
        val baseAlwaysOn = ambientDisplayConfiguration.alwaysOnEnabled(userTracker.userId)
        val allowed = controller.isAodAllowed(baseAlwaysOn)
        when {
            allowed && state == DozeMachine.State.DOZE ->
                dozeMachine.requestState(DozeMachine.State.DOZE_AOD)
            !allowed && state == DozeMachine.State.DOZE_AOD ->
                dozeMachine.requestState(DozeMachine.State.DOZE)
            else -> Unit
        }
    }

    override fun dump(pw: PrintWriter) {
        pw.println("AviumAodDozePart:")
        pw.println(" machineState=${machine?.getState()}")
        styleController.dump(pw)
    }
}
