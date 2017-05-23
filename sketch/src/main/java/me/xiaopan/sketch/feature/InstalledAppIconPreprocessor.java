/*
 * Copyright (C) 2017 Peng fei Pan <sky@xiaopan.me>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.sketch.feature;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

import me.xiaopan.sketch.Configuration;
import me.xiaopan.sketch.SLog;
import me.xiaopan.sketch.SLogType;
import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.cache.BitmapPool;
import me.xiaopan.sketch.cache.BitmapPoolUtils;
import me.xiaopan.sketch.cache.DiskCache;
import me.xiaopan.sketch.request.ImageFrom;
import me.xiaopan.sketch.request.LoadRequest;
import me.xiaopan.sketch.request.UriScheme;
import me.xiaopan.sketch.util.DiskLruCache;
import me.xiaopan.sketch.util.SketchUtils;

public class InstalledAppIconPreprocessor implements ImagePreprocessor.Preprocessor {

    public static final String INSTALLED_APP_URI_HOST = "installedApp";
    public static final String INSTALLED_APP_URI_PARAM_PACKAGE_NAME = "packageName";
    public static final String INSTALLED_APP_URI_PARAM_VERSION_CODE = "versionCode";

    private static final String LOG_NAME = "InstalledAppIconPreprocessor";

    @Override
    public boolean match(LoadRequest request) {
        return request.getUriScheme() == UriScheme.FILE && request.getRealUri().startsWith(INSTALLED_APP_URI_HOST);
    }

    @Override
    public PreProcessResult process(LoadRequest request) {
        String diskCacheKey = request.getUri();
        Configuration configuration = request.getConfiguration();

        DiskCache diskCache = configuration.getDiskCache();
        ReentrantLock diskCacheEditLock = diskCache.getEditLock(diskCacheKey);
        diskCacheEditLock.lock();

        PreProcessResult result = readInstalledAppIcon(configuration.getContext(), diskCache, request, diskCacheKey);

        diskCacheEditLock.unlock();
        return result;
    }

    private PreProcessResult readInstalledAppIcon(Context context, DiskCache diskCache, LoadRequest loadRequest, String diskCacheKey) {
        DiskCache.Entry appIconDiskCacheEntry = diskCache.get(diskCacheKey);
        if (appIconDiskCacheEntry != null) {
            return new PreProcessResult(appIconDiskCacheEntry, ImageFrom.DISK_CACHE);
        }

        Uri uri = Uri.parse(loadRequest.getUri());

        String packageName = uri.getQueryParameter(INSTALLED_APP_URI_PARAM_PACKAGE_NAME);
        int versionCode = Integer.valueOf(uri.getQueryParameter(INSTALLED_APP_URI_PARAM_VERSION_CODE));

        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        if (packageInfo.versionCode != versionCode) {
            return null;
        }

        String apkFilePath = packageInfo.applicationInfo.sourceDir;
        BitmapPool bitmapPool = Sketch.with(context).getConfiguration().getBitmapPool();
        boolean lowQualityImage = loadRequest.getOptions().isLowQualityImage();
        Bitmap iconBitmap = SketchUtils.readApkIcon(context, apkFilePath, lowQualityImage, LOG_NAME, bitmapPool);
        if (iconBitmap == null) {
            return null;
        }

        if (iconBitmap.isRecycled()) {
            if (SLogType.REQUEST.isEnabled()) {
                SLog.w(SLogType.REQUEST, LOG_NAME, "apk icon bitmap recycled. %s", loadRequest.getKey());
            }
            return null;
        }

        DiskCache.Editor diskCacheEditor = diskCache.edit(diskCacheKey);
        OutputStream outputStream;
        if (diskCacheEditor != null) {
            try {
                outputStream = new BufferedOutputStream(diskCacheEditor.newOutputStream(), 8 * 1024);
            } catch (IOException e) {
                e.printStackTrace();
                BitmapPoolUtils.freeBitmapToPool(iconBitmap, bitmapPool);
                diskCacheEditor.abort();
                return null;
            }
        } else {
            outputStream = new ByteArrayOutputStream();
        }

        try {
            iconBitmap.compress(SketchUtils.bitmapConfigToCompressFormat(iconBitmap.getConfig()), 100, outputStream);

            if (diskCacheEditor != null) {
                diskCacheEditor.commit();
            }
        } catch (DiskLruCache.EditorChangedException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } catch (DiskLruCache.ClosedException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } catch (DiskLruCache.FileNotExistException e) {
            e.printStackTrace();
            diskCacheEditor.abort();
            return null;
        } finally {
            BitmapPoolUtils.freeBitmapToPool(iconBitmap, bitmapPool);
            SketchUtils.close(outputStream);
        }

        if (diskCacheEditor != null) {
            appIconDiskCacheEntry = diskCache.get(diskCacheKey);
            if (appIconDiskCacheEntry != null) {
                return new PreProcessResult(appIconDiskCacheEntry, ImageFrom.LOCAL);
            } else {
                if (SLogType.REQUEST.isEnabled()) {
                    SLog.w(SLogType.REQUEST, LOG_NAME, "not found apk icon cache file. %s", loadRequest.getKey());
                }
                return null;
            }
        } else {
            return new PreProcessResult(((ByteArrayOutputStream) outputStream).toByteArray(), ImageFrom.LOCAL);
        }
    }
}
