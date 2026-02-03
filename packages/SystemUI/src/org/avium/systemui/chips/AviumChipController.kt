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
package org.avium.systemui.chips

import android.content.Context
import android.content.IntentFilter
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import org.avium.systemui.chips.broadcast.AviumChipBroadcastReceiver
import javax.inject.Inject

@SysUISingleton
class AviumChipController @Inject constructor(
    private val context: Context,
    private val viewModel: org.avium.systemui.chips.ui.viewmodel.AviumChipViewModel
) : CoreStartable {

    private val receiver = AviumChipBroadcastReceiver(viewModel)
    private var isRegistered = false

    override fun start() {
        if (isRegistered) return

        val filter = IntentFilter(AviumChipBroadcastReceiver.ACTION_SHOW_CHIP)
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        isRegistered = true
    }
}
