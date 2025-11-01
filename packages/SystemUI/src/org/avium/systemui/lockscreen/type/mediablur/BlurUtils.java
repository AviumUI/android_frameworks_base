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

package org.avium.systemui.lockscreen.type.mediablur;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.app.WallpaperManager;
import android.util.Log;

import java.io.FileDescriptor;
import android.graphics.BitmapFactory;

public class BlurUtils {

    private static final String TAG = "BlurUtils";

    public static Bitmap blur(Context context, Bitmap image, float blurRadius) {
        if (image == null || image.isRecycled()) {
            return null;
        }

        float scale = 0.25f; 
        int width = Math.round(image.getWidth() * scale);
        int height = Math.round(image.getHeight() * scale);
        
        if (width == 0 || height == 0) return null;

        Bitmap inputBitmap = Bitmap.createScaledBitmap(image, width, height, false);
        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript rs = null;
        try {
            rs = RenderScript.create(context);
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation inAlloc = Allocation.createFromBitmap(rs, inputBitmap);
            Allocation outAlloc = Allocation.createFromBitmap(rs, outputBitmap);
            
            script.setRadius(blurRadius > 25f ? 25f : blurRadius);
            
            script.setInput(inAlloc);
            script.forEach(outAlloc);
            outAlloc.copyTo(outputBitmap);
        } finally {
            if (rs != null) {
                rs.destroy();
            }
        }
        
        inputBitmap.recycle(); 

        return outputBitmap;
    }

    public static Bitmap getLockscreenWallpaper(Context context) {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
        try {
            ParcelFileDescriptor pfd = wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_LOCK);
            if (pfd != null) {
                FileDescriptor fd = pfd.getFileDescriptor();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap wallpaperBitmap = BitmapFactory.decodeFileDescriptor(fd, null, options);
                pfd.close();
                return wallpaperBitmap;
            }
        } catch (Exception e) {
            //ntd
        }

        try {
            Drawable wallpaperDrawable = wallpaperManager.getDrawable();
            if (wallpaperDrawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) wallpaperDrawable).getBitmap();
            } else {
                int width = wallpaperDrawable.getIntrinsicWidth();
                int height = wallpaperDrawable.getIntrinsicHeight();
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                wallpaperDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                wallpaperDrawable.draw(canvas);
                return bitmap;
            }
        } catch (Exception e) {
            //ntd
        }

        return null;
    }
}