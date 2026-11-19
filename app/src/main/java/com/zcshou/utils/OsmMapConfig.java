package com.zcshou.utils;

import android.content.Context;

import androidx.preference.PreferenceManager;

import com.zcshou.gogogo.BuildConfig;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

public final class OsmMapConfig {
    private OsmMapConfig() {
    }

    public static void init(Context context) {
        Context appContext = context.getApplicationContext();
        IConfigurationProvider configuration = Configuration.getInstance();
        configuration.load(appContext, PreferenceManager.getDefaultSharedPreferences(appContext));
        configuration.setUserAgentValue("GoGoGo/" + BuildConfig.VERSION_NAME
                + " (https://github.com/zcshou/gogogo)");

        File basePath = new File(appContext.getCacheDir(), "osmdroid");
        if (!basePath.exists()) {
            //noinspection ResultOfMethodCallIgnored
            basePath.mkdirs();
        }
        File tileCache = new File(basePath, "tiles");
        if (!tileCache.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tileCache.mkdirs();
        }
        configuration.setOsmdroidBasePath(basePath);
        configuration.setOsmdroidTileCache(tileCache);
    }
}
