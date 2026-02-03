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
package org.avium.systemui.chips.ui.model

enum class AviumChipType(
    val typeCode: Int,
    val iconResName: String,
    val defaultTimeoutMs: Long
) {
    MUSIC(1, "ic_avium_notifs_chip_music", 5000L),
    NOTIFICATIONS(2, "ic_avium_notifs_chip_notifs", 900000L),
    CODE(3, "ic_avium_notifs_chip_code", 900000L),
    FOOD(4, "ic_avium_notifs_chip_food", 900000L),
    LOCAL(5, "ic_avium_notifs_chip_local", 900000L),
    CLASS(6, "ic_avium_notifs_chip_class", 900000L),

    UNKNOWN(-1, "", 900000L);

    companion object {
        fun fromTypeCode(code: Int): AviumChipType {
            return values().find { it.typeCode == code } ?: UNKNOWN
        }
    }
}
