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
package org.avium.systemui.chips.ui.viewmodel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.avium.systemui.chips.ui.model.AviumChipType
import org.avium.systemui.chips.broadcast.AviumChipBroadcastReceiver
import javax.inject.Inject

@SysUISingleton
class AviumChipViewModel
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val context: Context
) : OngoingActivityChipViewModel {

    private val _chip = MutableStateFlow<OngoingActivityChipModel>(OngoingActivityChipModel.Inactive())
    override val chip: StateFlow<OngoingActivityChipModel> = _chip

    private var timeoutJob: Job? = null
    private var currentUpdateKey: String? = null

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "avium_chip_channel"
        private const val NOTIFICATION_ID_BASE = 10000
    }

    fun showChip(
        typeCode: Int,
        text: String? = null,
        durationMs: Long? = null,
        updateKey: String? = null
    ) {
        val chipType = AviumChipType.fromTypeCode(typeCode)
        if (chipType == AviumChipType.UNKNOWN) {
            return
        }

        currentUpdateKey = updateKey

        storeTypeCode(updateKey, typeCode)
        text?.let { storeText(updateKey, it) }

        val icon = loadIcon(chipType.iconResName)
        val content = if (text != null) {
            OngoingActivityChipModel.Content.Text(text)
        } else {
            OngoingActivityChipModel.Content.IconOnly
        }

        val timeout = durationMs ?: chipType.defaultTimeoutMs

        _chip.value = OngoingActivityChipModel.Active(
            key = "avium_chip_${updateKey ?: System.currentTimeMillis()}",
            managingPackageName = "org.avium.systemui",
            isImportantForPrivacy = false,
            icon = icon,
            content = content,
            colors = ColorsModel.SystemThemed,
            onClickListenerLegacy = null,
            clickBehavior = OngoingActivityChipModel.ClickBehavior.None,
            isHidden = false,
            shouldAnimate = true,
        )

        startTimeout(timeout)

        if (typeCode != 1 && text != null) {
            showNotification(text, updateKey)
        }
    }

    fun updateChip(
        typeCode: Int? = null,
        text: String? = null,
        durationMs: Long? = null,
        updateKey: String? = null
    ) {
        if (currentUpdateKey != updateKey) {
            return
        }

        val currentChip = _chip.value
        if (currentChip !is OngoingActivityChipModel.Active) {
            return
        }

        val chipType = typeCode?.let { AviumChipType.fromTypeCode(it) }
        val icon = chipType?.let { loadIcon(it.iconResName) } ?: currentChip.icon

        val content = if (text != null) {
            OngoingActivityChipModel.Content.Text(text)
        } else {
            currentChip.content
        }

        val timeout = durationMs ?: chipType?.defaultTimeoutMs ?: 900000L

        _chip.value = currentChip.copy(
            icon = icon,
            content = content,
        )

        startTimeout(timeout)
    }

    fun cancelChip(updateKey: String? = null) {
        if (updateKey != null && currentUpdateKey != updateKey) {
            return
        }

        timeoutJob?.cancel()
        timeoutJob = null
        currentUpdateKey = null
        _chip.value = OngoingActivityChipModel.Inactive(shouldAnimate = true)

        cancelNotification(updateKey)
    }

    private fun startTimeout(timeoutMs: Long) {
        timeoutJob?.cancel()
        timeoutJob = applicationScope.launch {
            delay(timeoutMs)
            cancelChip()
        }
    }

    private fun showNotification(text: String, updateKey: String?) {
        createNotificationChannel()

        val notificationId = getNotificationId(updateKey)
        val dismissIntent = createDismissIntent(updateKey)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_avium_notifs_chip_notifs)
            .setContentTitle(context.getString(R.string.avium_chip_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${context.getString(R.string.avium_chip_notification_dismiss_hint)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(dismissIntent)
            .setDeleteIntent(createDeleteIntent(updateKey))
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun cancelNotification(updateKey: String?) {
        val notificationId = getNotificationId(updateKey)
        notificationManager.cancel(notificationId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.avium_chip_notification_title),
                IMPORTANCE_HIGH
            ).apply {
                description = "Avium Chip Notifications"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createDismissIntent(updateKey: String?): PendingIntent {
        val intent = Intent(AviumChipBroadcastReceiver.ACTION_SHOW_CHIP).apply {
            putExtra(AviumChipBroadcastReceiver.EXTRA_ACTION, AviumChipBroadcastReceiver.ACTION_CANCEL)
            putExtra(AviumChipBroadcastReceiver.EXTRA_UPDATE_KEY, updateKey)
        }

        return PendingIntent.getBroadcast(
            context,
            getNotificationId(updateKey),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDeleteIntent(updateKey: String?): PendingIntent {
        val intent = Intent(AviumChipBroadcastReceiver.ACTION_SHOW_CHIP).apply {
            putExtra(AviumChipBroadcastReceiver.EXTRA_ACTION, AviumChipBroadcastReceiver.ACTION_SHOW)
            putExtra(AviumChipBroadcastReceiver.EXTRA_TYPE, currentUpdateKey?.let { getStoredTypeCode(it) } ?: 2)
            putExtra(AviumChipBroadcastReceiver.EXTRA_TEXT, getStoredText(updateKey))
            putExtra(AviumChipBroadcastReceiver.EXTRA_UPDATE_KEY, updateKey)
        }

        return PendingIntent.getBroadcast(
            context,
            getNotificationId(updateKey) + 1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNotificationId(updateKey: String?): Int {
        return NOTIFICATION_ID_BASE + (updateKey?.hashCode() ?: 0).let { if (it < 0) -it else it }
    }

    private var storedTypeCodeMap = mutableMapOf<String?, Int>()
    private var storedTextMap = mutableMapOf<String?, String>()

    private fun storeTypeCode(updateKey: String?, typeCode: Int) {
        storedTypeCodeMap[updateKey] = typeCode
    }

    private fun getStoredTypeCode(updateKey: String?): Int? {
        return storedTypeCodeMap[updateKey]
    }

    private fun storeText(updateKey: String?, text: String) {
        storedTextMap[updateKey] = text
    }

    private fun getStoredText(updateKey: String?): String? {
        return storedTextMap[updateKey]
    }

    private fun loadIcon(iconResName: String): OngoingActivityChipModel.ChipIcon.SingleColorIcon {
        val iconRes = when (iconResName) {
            "ic_avium_notifs_chip_music" -> R.drawable.ic_avium_notifs_chip_music
            "ic_avium_notifs_chip_notifs" -> R.drawable.ic_avium_notifs_chip_notifs
            "ic_avium_notifs_chip_code" -> R.drawable.ic_avium_notifs_chip_code
            "ic_avium_notifs_chip_food" -> R.drawable.ic_avium_notifs_chip_food
            "ic_avium_notifs_chip_local" -> R.drawable.ic_avium_notifs_chip_local
            "ic_avium_notifs_chip_class" -> R.drawable.ic_avium_notifs_chip_class
            else -> R.drawable.ic_avium_notifs_chip_notifs
        }

        val icon = Icon.Resource(
            iconRes,
            ContentDescription.Loaded("Avium chip")
        )

        return OngoingActivityChipModel.ChipIcon.SingleColorIcon(icon)
    }
}
