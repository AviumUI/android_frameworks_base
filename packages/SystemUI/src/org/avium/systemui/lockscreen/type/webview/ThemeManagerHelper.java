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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ThemeManagerHelper {
    private static final String THEME_DIR = "lockscreen_themes";
    private static final String CURRENT_THEME_LINK = "current";

    private File mThemeBaseDir;
    private File mCurrentThemeDir;
    private Map<String, byte[]> mResourceCache;

    public ThemeManagerHelper(File filesDir) {
        mThemeBaseDir = new File(filesDir, THEME_DIR);
        mCurrentThemeDir = new File(mThemeBaseDir, CURRENT_THEME_LINK);
        mResourceCache = new HashMap<>();
        initThemeDirectory();
    }

    private void initThemeDirectory() {
        if (!mThemeBaseDir.exists()) {
            mThemeBaseDir.mkdirs();
        }
    }

    public File getCurrentThemeDir() {
        return mCurrentThemeDir;
    }

    public Map<String, byte[]> getResourceCache() {
        return mResourceCache;
    }

    public void cacheThemeResources() {
        mResourceCache.clear();
        if (!mCurrentThemeDir.exists()) {
            return;
        }
        cacheDirectory(mCurrentThemeDir, "");
    }

    private int cacheDirectory(File dir, String relativePath) {
        File[] files = dir.listFiles();
        if (files == null) return 0;

        int count = 0;
        for (File file : files) {
            String path = relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
            if (file.isDirectory()) {
                count += cacheDirectory(file, path);
            } else {
                try {
                    byte[] data = new byte[(int) file.length()];
                    FileInputStream fis = new FileInputStream(file);
                    fis.read(data);
                    fis.close();
                    mResourceCache.put(path, data);
                    count++;
                } catch (IOException e) {
                }
            }
        }
        return count;
    }

    public boolean processThemeZip(String zipPath, String themeName) {
        File savedZipFile = new File(mThemeBaseDir, "theme_" + themeName + ".zip");
        File themeDir = new File(mThemeBaseDir, themeName);
        File tempThemeDir = new File(mThemeBaseDir, themeName + "_temp_" + System.currentTimeMillis());

        try {
            File zipFile = new File(zipPath);

            if (!zipFile.exists()) {
                return false;
            }

            FileInputStream fis = new FileInputStream(zipFile);
            FileOutputStream fos = new FileOutputStream(savedZipFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fis.close();
            fos.close();

            tempThemeDir.mkdirs();

            ZipFile zip = new ZipFile(savedZipFile);
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                File outFile = new File(tempThemeDir, entryName);

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    FileOutputStream entryFos = new FileOutputStream(outFile);
                    InputStream is = zip.getInputStream(entry);
                    byte[] buf = new byte[4096];
                    int l;
                    while ((l = is.read(buf)) > 0) {
                        entryFos.write(buf, 0, l);
                    }
                    is.close();
                    entryFos.close();
                }
            }
            zip.close();

            File indexFile = new File(tempThemeDir, "index.html");
            if (!indexFile.exists()) {
                deleteDirectory(tempThemeDir);
                return false;
            }

            if (themeDir.exists()) {
                deleteDirectory(themeDir);
            }
            tempThemeDir.renameTo(themeDir);

            File currentLink = new File(mThemeBaseDir, CURRENT_THEME_LINK);
            if (currentLink.exists()) {
                deleteDirectory(currentLink);
            }

            try {
                java.nio.file.Files.createSymbolicLink(currentLink.toPath(), themeDir.toPath());
            } catch (Exception e) {
                return false;
            }

            return true;

        } catch (Exception e) {
            if (tempThemeDir.exists()) {
                deleteDirectory(tempThemeDir);
            }
            return false;
        } finally {
            if (savedZipFile.exists()) {
                savedZipFile.delete();
            }
        }
    }

    private boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        return dir.delete();
    }
}
