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

package org.avium.systemui.lockscreen.type.webview;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.util.Map;

public class LockscreenWebViewClient extends WebViewClient {
    private Map<String, byte[]> mResourceCache;
    private Runnable mOnPageFinishedCallback;

    public LockscreenWebViewClient(Map<String, byte[]> resourceCache) {
        mResourceCache = resourceCache;
    }

    public void setOnPageFinishedCallback(Runnable callback) {
        mOnPageFinishedCallback = callback;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (mOnPageFinishedCallback != null) {
            mOnPageFinishedCallback.run();
        }
    }

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        if (url.startsWith("file://")) {
            return null;
        }

        String path = request.getUrl().getPath();
        if (path == null) {
            return null;
        }

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        byte[] data = mResourceCache.get(path);
        if (data != null) {
            String mimeType = getMimeType(path);
            return new WebResourceResponse(mimeType, "UTF-8", new ByteArrayInputStream(data));
        }

        return null;
    }

    private String getMimeType(String path) {
        if (path.endsWith(".html") || path.endsWith(".htm")) {
            return "text/html";
        } else if (path.endsWith(".css")) {
            return "text/css";
        } else if (path.endsWith(".js")) {
            return "application/javascript";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (path.endsWith(".gif")) {
            return "image/gif";
        } else if (path.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (path.endsWith(".json")) {
            return "application/json";
        } else if (path.endsWith(".woff")) {
            return "font/woff";
        } else if (path.endsWith(".woff2")) {
            return "font/woff2";
        } else if (path.endsWith(".ttf")) {
            return "font/ttf";
        } else {
            return "application/octet-stream";
        }
    }
}
