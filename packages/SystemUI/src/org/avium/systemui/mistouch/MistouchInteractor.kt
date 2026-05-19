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

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

class MistouchInteractor private constructor() {

    interface MistouchEvents {
        fun onAffordanceLongClick() {}
        fun onDoubleTapPowerGesture() {}
        fun onEmergencyButtonClick() {}
        fun onMistouchVisibilityChanged(visible: Boolean) {}
    }

    private val listeners = CopyOnWriteArrayList<WeakReference<MistouchEvents>>()

    fun addListener(listener: MistouchEvents) {
        listeners.add(WeakReference(listener))
    }

    fun removeListener(listener: MistouchEvents) {
        listeners.removeAll { it.get() == listener }
    }

    private fun notifyListeners(action: (MistouchEvents) -> Unit) {
        val deadReferences = mutableListOf<WeakReference<MistouchEvents>>()
        for (ref in listeners) {
            val listener = ref.get()
            if (listener != null) {
                action(listener)
            } else {
                deadReferences.add(ref)
            }
        }
        listeners.removeAll(deadReferences)
    }

    fun handleEmergencyButtonClick() {
        notifyListeners { it.onEmergencyButtonClick() }
    }

    fun handleDoubleTapPowerGesture() {
        notifyListeners { it.onDoubleTapPowerGesture() }
    }

    fun handleAffordanceLongClick() {
        notifyListeners { it.onAffordanceLongClick() }
    }

    fun handleMistouchVisibilityChanged(visible: Boolean) {
        notifyListeners { it.onMistouchVisibilityChanged(visible) }
    }

    companion object {
        @Volatile
        private var instance: MistouchInteractor? = null

        @JvmStatic
        fun get(): MistouchInteractor {
            return instance ?: synchronized(this) {
                instance ?: MistouchInteractor().also { instance = it }
            }
        }
    }
}
