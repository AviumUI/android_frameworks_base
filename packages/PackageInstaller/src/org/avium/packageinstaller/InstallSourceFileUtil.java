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

package org.avium.packageinstaller;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public final class InstallSourceFileUtil {
    private static final String LOG_TAG = InstallSourceFileUtil.class.getSimpleName();

    public static final String EXTRA_DELETE_SOURCE_PACKAGE =
            "org.avium.packageinstaller.extra.DELETE_SOURCE_PACKAGE";
    public static final String EXTRA_ORIGINAL_PACKAGE_URI =
            "org.avium.packageinstaller.extra.ORIGINAL_PACKAGE_URI";

    private static final String EXTRA_LEGACY_STAGED_SESSION_ID = "EXTRA_STAGED_SESSION_ID";
    private static final String EXTRA_V2_STAGED_SESSION_ID =
            "com.android.packageinstaller.extra.STAGED_SESSION_ID";

    private InstallSourceFileUtil() {
    }

    public static void putOriginalPackageUri(@NonNull Intent intent, @Nullable Uri packageUri) {
        if (packageUri != null) {
            intent.putExtra(EXTRA_ORIGINAL_PACKAGE_URI, packageUri);
        }
    }

    public static void putDeleteSourcePackageExtras(@NonNull Intent fromIntent,
            @NonNull Intent toIntent) {
        final Uri sourceUri = getSourcePackageUri(fromIntent);
        if (sourceUri == null) {
            return;
        }
        toIntent.putExtra(EXTRA_DELETE_SOURCE_PACKAGE, true);
        toIntent.putExtra(EXTRA_ORIGINAL_PACKAGE_URI, sourceUri);
    }

    public static boolean canOfferDeleteSourcePackage(@NonNull Intent intent) {
        return getSourcePackageUri(intent) != null;
    }

    public static void deleteSourcePackageIfRequested(@NonNull Context context,
            @NonNull Intent intent) {
        if (!intent.getBooleanExtra(EXTRA_DELETE_SOURCE_PACKAGE, false)) {
            return;
        }

        final Uri sourceUri = getSourcePackageUri(intent);
        if (sourceUri == null) {
            return;
        }

        try {
            final String scheme = sourceUri.getScheme();
            if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                final String path = sourceUri.getPath();
            } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
                final ContentResolver resolver = context.getContentResolver();
                if (DocumentsContract.isDocumentUri(context, sourceUri)) {
                    DocumentsContract.deleteDocument(resolver, sourceUri);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    private static Uri getSourcePackageUri(@NonNull Intent intent) {
        Uri sourceUri = intent.getParcelableExtra(EXTRA_ORIGINAL_PACKAGE_URI, Uri.class);
        final boolean hasOriginalUri = sourceUri != null;
        if (sourceUri == null) {
            sourceUri = intent.getData();
        }
        if (sourceUri == null) {
            return null;
        }

        final String scheme = sourceUri.getScheme();
        if (!ContentResolver.SCHEME_FILE.equals(scheme)
                && !ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            return null;
        }

        if (!hasOriginalUri && ContentResolver.SCHEME_FILE.equals(scheme)
                && (intent.hasExtra(EXTRA_LEGACY_STAGED_SESSION_ID)
                        || intent.hasExtra(EXTRA_V2_STAGED_SESSION_ID))) {
            return null;
        }

        return sourceUri;
    }
}
