/*
 * Copyright (C) 2026 The AviumUI Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.avium.server;

import android.annotation.NonNull;

import com.android.server.SystemServiceManager;
import com.android.server.utils.TimingsTraceAndSlog;

import org.avium.server.sensors.SensorBlockController;

public final class AviumSystemServer {

    private AviumSystemServer() {}

    public static void startBootstrapServices(@NonNull SystemServiceManager serviceManager,
            @NonNull TimingsTraceAndSlog t) {
        t.traceBegin("StartSensorBlockController");
        serviceManager.startService(SensorBlockController.class);
        t.traceEnd();
    }
}
