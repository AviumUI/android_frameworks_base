/*
 * Copyright (C) 2023-2024 The Nameless-AOSP Project
 * Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: (Apache-2.0 OR MIT)
 */

package org.avium.content;

/** @hide */
public class ContextExt {

    private ContextExt() {}

    /**
     * Use with {@link #getSystemService} to retrieve a
     * {@link org.avium.hardware.SensorBlockManager} for managing sensor block state.
     *
     * @hide
     * @see #getSystemService
     * @see org.avium.hardware.SensorBlockManager
     */
    public static final String SENSOR_BLOCK_MANAGER_SERVICE = "sensor_block";
}
