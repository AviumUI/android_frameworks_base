/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.privacy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.util.PrivacyIndicatorUtils
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class PrivacyIndicatorSystemAppsTest : SysuiTestCase() {
    private val policyContext = mock<Context>()
    private val packageManager = mock<PackageManager>()
    private val application = ApplicationInfo()
    private val uid = UserHandle.getUid(10, 10001)

    @Before
    fun setUp() {
        whenever(policyContext.contentResolver).thenReturn(context.contentResolver)
        whenever(policyContext.packageManager).thenReturn(packageManager)
        whenever(packageManager.getApplicationInfoAsUser("test.app", 0, 10))
            .thenReturn(application)
        Settings.System.putStringForUser(
            context.contentResolver, PrivacyIndicatorUtils.SHOW_SYSTEM_APPS, null,
            UserHandle.USER_CURRENT,
        )
    }

    @Test
    fun absentSetting_hidesSystemAppsButKeepsThirdPartyApps() {
        assertThat(PrivacyIndicatorUtils.showSystemApps(policyContext)).isFalse()
        application.flags = ApplicationInfo.FLAG_SYSTEM
        assertThat(isVisible()).isFalse()
        application.flags = 0
        assertThat(isVisible()).isTrue()
    }

    @Test
    fun updatedSystemApp_isHiddenWhenDisabled() {
        application.flags = ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        assertThat(isVisible()).isFalse()
    }

    @Test
    fun changingSetting_updatesPolicyWithoutRestart() {
        application.flags = ApplicationInfo.FLAG_SYSTEM
        assertThat(isVisible()).isFalse()
        setEnabled(true)
        assertThat(isVisible()).isTrue()
        setEnabled(false)
        assertThat(isVisible()).isFalse()
    }

    @Test
    fun removedPackage_doesNotSuppressRemainingAccess() {
        whenever(packageManager.getApplicationInfoAsUser("test.app", 0, 10))
            .thenThrow(PackageManager.NameNotFoundException())
        assertThat(isVisible()).isTrue()
    }

    private fun isVisible() =
        PrivacyIndicatorUtils.shouldShowApplication(policyContext, "test.app", uid)

    private fun setEnabled(enabled: Boolean) {
        Settings.System.putIntForUser(
            context.contentResolver, PrivacyIndicatorUtils.SHOW_SYSTEM_APPS,
            if (enabled) 1 else 0, UserHandle.USER_CURRENT,
        )
    }
}
