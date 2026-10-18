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

package org.avium.systemui.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.settings.UserTracker
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings

@SysUISingleton
class CustomLockscreenRepository
@Inject
constructor(
    private val context: Context,
    private val userTracker: UserTracker,
    @Main private val mainExecutor: Executor,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val _isEnabled = MutableStateFlow(CustomLockscreenSettings.isEnabled())
    val isEnabled = _isEnabled.asStateFlow()
    private val _settingsVersion = MutableStateFlow(0L)
    /** Emits after a complete configuration or foreground user changes. */
    val settingsVersion = _settingsVersion.asStateFlow()
    private var lastConfig: String? = null
    private var lastUser = -1

    private fun updateState() {
        val user = userTracker.userId
        val raw =
            Settings.Secure.getStringForUser(
                context.contentResolver,
                CustomLockscreenSettings.CONFIG,
                user,
            )
        if (lastUser == user && lastConfig == raw) return
        if (lastUser != user) CustomLockscreenSettings.setUserId(user)
        lastUser = user
        lastConfig = raw
        _isEnabled.value = CustomLockscreenSettings.isEnabled()
        _settingsVersion.value += 1L
        // The AOD style controller still uses an internal invalidation broadcast.
        // Configuration itself is always read from Settings Provider.
        context.sendBroadcast(
            Intent(ACTION_SETTINGS_CHANGED)
                .setPackage(context.packageName)
                .putExtra("provider_update", true)
        )
    }

    init {
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (
                        intent.action == ACTION_SETTINGS_CHANGED &&
                            !intent.getBooleanExtra("provider_update", false)
                    ) {
                        _settingsVersion.value += 1L
                    }
                }
            },
            IntentFilter(ACTION_SETTINGS_CHANGED),
            "android.permission.WRITE_SECURE_SETTINGS",
            handler,
            Context.RECEIVER_EXPORTED,
        )
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(CustomLockscreenSettings.CONFIG),
            false,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = updateState()
            },
            UserHandle.USER_ALL,
        )
        userTracker.addCallback(
            object : UserTracker.Callback {
                override fun onUserChanged(newUser: Int, userContext: Context) = updateState()
            },
            mainExecutor,
        )
        updateState()
    }

    companion object {
        const val ACTION_SETTINGS_CHANGED = "org.avium.systemui.lockscreen.SETTINGS_CHANGED"
    }
}
