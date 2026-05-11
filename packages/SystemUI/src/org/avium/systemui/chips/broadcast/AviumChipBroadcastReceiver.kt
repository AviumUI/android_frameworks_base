/*
 *
 * Copyright (C) 2026 The AviumUI Project
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.avium.systemui.chips.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.avium.systemui.chips.ui.viewmodel.AviumChipViewModel

class AviumChipBroadcastReceiver(
    private val viewModel: AviumChipViewModel
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        val action = intent.getStringExtra(EXTRA_ACTION) ?: ACTION_SHOW

        when (action) {
            ACTION_SHOW -> handleShowAction(intent)
            ACTION_UPDATE -> handleUpdateAction(intent)
            ACTION_CANCEL -> handleCancelAction(intent)
        }
    }

    private fun handleShowAction(intent: Intent) {
        val typeCode = intent.getIntExtra(EXTRA_TYPE, -1)
        val text = intent.getStringExtra(EXTRA_TEXT)
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, -1L)
        val updateKey = intent.getStringExtra(EXTRA_UPDATE_KEY)

        if (typeCode == -1) {
            return
        }

        val duration = if (durationMs > 0) durationMs else null

        viewModel.showChip(
            typeCode = typeCode,
            text = text,
            durationMs = duration,
            updateKey = updateKey
        )
    }

    private fun handleUpdateAction(intent: Intent) {
        val typeCode = intent.getIntExtra(EXTRA_TYPE, -1).let {
            if (it == -1) null else it
        }
        val text = intent.getStringExtra(EXTRA_TEXT)
        val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, -1L).let {
            if (it > 0) it else null
        }
        val updateKey = intent.getStringExtra(EXTRA_UPDATE_KEY)

        if (updateKey == null) {
            return
        }

        viewModel.updateChip(
            typeCode = typeCode,
            text = text,
            durationMs = durationMs,
            updateKey = updateKey
        )
    }

    private fun handleCancelAction(intent: Intent) {
        val updateKey = intent.getStringExtra(EXTRA_UPDATE_KEY)
        viewModel.cancelChip(updateKey)
    }

    companion object {
        const val ACTION_SHOW_CHIP = "org.avium.systemui.chips.action.SHOW_CHIP"

        public const val EXTRA_ACTION = "action"
        public const val ACTION_SHOW = "show"
        public const val ACTION_UPDATE = "update"
        public const val ACTION_CANCEL = "cancel"

        public const val EXTRA_TYPE = "type"
        public const val EXTRA_TEXT = "text"
        public const val EXTRA_DURATION_MS = "duration_ms"
        public const val EXTRA_UPDATE_KEY = "update_key"
    }
}
