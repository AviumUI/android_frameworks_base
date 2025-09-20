/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.blueprints

import android.util.Log
import com.android.systemui.communal.ui.view.layout.sections.CommunalTutorialIndicatorSection
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.view.layout.sections.AccessibilityActionsSection
import com.android.systemui.keyguard.ui.view.layout.sections.AodBurnInSection
import com.android.systemui.keyguard.ui.view.layout.sections.AodNotificationIconsSection
import com.android.systemui.keyguard.ui.view.layout.sections.AodPromotedNotificationSection
import com.android.systemui.keyguard.ui.view.layout.sections.ClockSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultDeviceEntrySection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultIndicationAreaSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultNotificationStackScrollLayoutSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultSettingsPopupMenuSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultShortcutsSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultStatusBarSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultUdfpsAccessibilityOverlaySection
import com.android.systemui.keyguard.ui.view.layout.sections.KeyguardSectionsModule.Companion.KEYGUARD_AMBIENT_INDICATION_AREA_SECTION
import com.android.systemui.keyguard.ui.view.layout.sections.KeyguardSliceViewSection
import com.android.systemui.keyguard.ui.view.layout.sections.SmartspaceSection
import java.util.Optional
import javax.inject.Inject
import javax.inject.Named
import kotlin.jvm.optionals.getOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.avium.systemui.lockscreen.CustomLockscreenClockManager
import org.avium.systemui.lockscreen.sections.CustomClockSection
import org.avium.systemui.lockscreen.CustomLockscreenRepository

/**
 * Positions elements of the lockscreen to the default position.
 *
 * This will be the most common use case for phones in portrait mode.
 */
@SysUISingleton
@JvmSuppressWildcards
class DefaultKeyguardBlueprint
@Inject
constructor(
    private val accessibilityActionsSection: AccessibilityActionsSection,
    private val defaultIndicationAreaSection: DefaultIndicationAreaSection,
    private val defaultDeviceEntrySection: DefaultDeviceEntrySection,
    private val defaultShortcutsSection: DefaultShortcutsSection,
    @Named(KEYGUARD_AMBIENT_INDICATION_AREA_SECTION)
    private val defaultAmbientIndicationAreaSection: Optional<KeyguardSection>,
    private val defaultSettingsPopupMenuSection: DefaultSettingsPopupMenuSection,
    private val defaultStatusViewSection: DefaultStatusViewSection,
    private val defaultStatusBarSection: DefaultStatusBarSection,
    private val defaultNotificationStackScrollLayoutSection: DefaultNotificationStackScrollLayoutSection,
    private val aodNotificationIconsSection: AodNotificationIconsSection,
    private val aodBurnInSection: AodBurnInSection,
    private val communalTutorialIndicatorSection: CommunalTutorialIndicatorSection,
    private val clockSection: ClockSection,
    private val smartspaceSection: SmartspaceSection,
    private val keyguardSliceViewSection: KeyguardSliceViewSection,
    private val udfpsAccessibilityOverlaySection: DefaultUdfpsAccessibilityOverlaySection,
    private val customLockscreenClockManager: CustomLockscreenClockManager,
    private val customClockSection: CustomClockSection,
    private val customLockscreenRepository: CustomLockscreenRepository,
    aodPromotedNotificationSection: AodPromotedNotificationSection,
) : KeyguardBlueprint {
    override val id: String = DEFAULT

    override val sections: List<KeyguardSection>
        get() {
            val allSections =
                listOfNotNull(
                    accessibilityActionsSection,
                    defaultIndicationAreaSection,
                    defaultShortcutsSection,
                    defaultAmbientIndicationAreaSection.getOrNull(),
                    defaultSettingsPopupMenuSection,
                    defaultStatusViewSection,
                    defaultStatusBarSection,
                    defaultNotificationStackScrollLayoutSection,
                    aodNotificationIconsSection,
                    smartspaceSection,
                    aodBurnInSection,
                    communalTutorialIndicatorSection,
                    clockSection,
                    keyguardSliceViewSection,
                    defaultDeviceEntrySection,
                    aodPromotedNotificationSection,
                    udfpsAccessibilityOverlaySection, // Add LAST: Intentionally has z-order above others
                )

            return if (customLockscreenRepository.isEnabled.value) {
                Log.d("AVIUM_BLUEPRINT", "Custom lockscreen enabled. Replacing native sections.")
                allSections.filterNot {
                    it is ClockSection || it is SmartspaceSection || it is KeyguardSliceViewSection
                } + customClockSection
            } else {
                allSections
            }
        }

    companion object {
        const val DEFAULT = "default"
    }
}
