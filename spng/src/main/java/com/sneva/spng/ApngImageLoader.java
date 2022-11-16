package com.sneva.spng;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import com.sneva.spng.assist.ApngImageDownloader;
import com.sneva.spng.assist.ApngImageLoaderCallback;
import com.sneva.spng.assist.ApngImageLoadingListener;
import com.sneva.spng.assist.ApngListener;
import com.sneva.spng.assist.PngImageLoader;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

public class ApngImageLoader extends ImageLoader {
    public static boolean enableVerboseLog = false;
    public static boolean enableDebugLog = false;

    private static ApngImageLoader singleton;

    private Context context;

    public static ApngImageLoader getInstance() {
        if (singleton == null) {
            synchronized (ApngImageLoader.class) {
                if (singleton == null) {
                    singleton = new ApngImageLoader();
                }
            }
        }
        return singleton;
    }

    protected ApngImageLoader() { /*Singleton*/ }

    public void init(Context context) {
        this.init(context, null, null);
    }

    public void init(Context context, ImageLoaderConfiguration commonImageLoaderConfiguration, ImageLoaderConfiguration apngComponentImageLoaderConfiguration) {
        this.context = context.getApplicationContext();
        if (commonImageLoaderConfiguration == null) {
            commonImageLoaderConfiguration = getDefaultCommonImageLoaderConfiguration();
        }
        if (apngComponentImageLoaderConfiguration == null) {
            apngComponentImageLoaderConfiguration = getDefaultApngComponentImageLoaderConfiguration(this.context);
        }
        PngImageLoader.getInstance().init(commonImageLoaderConfiguration);
        super.init(apngComponentImageLoaderConfiguration);
    }

    public void setEnableVerboseLog(boolean enableVerboseLog) {
        ApngImageLoader.enableVerboseLog = enableVerboseLog;
    }

    public void setEnableDebugLog(boolean enableDebugLog) {
        ApngImageLoader.enableDebugLog = enableDebugLog;
    }

    @Override
    public void displayImage(String uri, ImageView imageView) {
        displayApng(uri, imageView, null);
    }

    @Override
    public void displayImage(String uri, ImageView imageView, DisplayImageOptions options) {
        displayApng(uri, imageView, options, null);
    }

    public void displayApng(String uri, ImageView imageView, ApngConfig config) {
        super.displayImage(uri, imageView, new ApngImageLoadingListener(context, Uri.parse(uri), getAutoPlayHandler(config, null)));
    }

    public void displayApng(String uri, ImageView imageView, DisplayImageOptions options, ApngConfig config) {
        super.displayImage(uri, imageView, options, new ApngImageLoadingListener(context, Uri.parse(uri), getAutoPlayHandler(config, null)));
    }

    public void displayApng(String uri, ImageView imageView, ApngConfig config, ApngListener apngListener) {
        super.displayImage(uri, imageView, new ApngImageLoadingListener(context, Uri.parse(uri), getAutoPlayHandler(config, apngListener)));
    }

    public void displayApng(String uri, ImageView imageView, DisplayImageOptions options, ApngConfig config, ApngListener apngListener) {
        super.displayImage(uri, imageView, options, new ApngImageLoadingListener(context, Uri.parse(uri), getAutoPlayHandler(config, apngListener)));
    }

    private ImageLoaderConfiguration getDefaultApngComponentImageLoaderConfiguration(Context context) {
        DisplayImageOptions defaultDisplayImageOptions = new DisplayImageOptions.Builder().cacheInMemory(false).cacheOnDisk(true).build();
        return new ImageLoaderConfiguration.Builder(context).memoryCache(new LruMemoryCache(2 * 1024 * 1024)).memoryCacheSize(2 * 1024 * 1024).diskCacheSize(50 * 1024 * 1024).diskCacheFileCount(100).imageDownloader(new ApngImageDownloader(context)).defaultDisplayImageOptions(defaultDisplayImageOptions).build();
    }

    private ImageLoaderConfiguration getDefaultCommonImageLoaderConfiguration() {
        return new ImageLoaderConfiguration.Builder(this.context).memoryCache(new LruMemoryCache(8 * 1024 * 1024)).memoryCacheSize(8 * 1024 * 1024).diskCacheSize(50 * 1024 * 1024).diskCacheFileCount(100).build();
    }

    private ApngImageLoaderCallback getAutoPlayHandler(final ApngConfig config, final ApngListener apngListener) {
        if (config == null || !config.autoPlay) {
            return null;
        } else {
            return new ApngImageLoaderCallback() {
                @Override
                public void onLoadFinish(boolean success, String imageUri, View view) {
                    if (!success) return;
                    ApngDrawable apngDrawable = ApngDrawable.getFromView(view);
                    if (apngDrawable == null) return;
                    apngDrawable.setApngListener(apngListener);
                    if (config.numPlays > 0) apngDrawable.setNumPlays(config.numPlays);
                    apngDrawable.setShowLastFrameOnStop(config.showLastFrameOnStop);
                    apngDrawable.start();
                }
            };
        }
    }

    public static class ApngConfig {
        public int numPlays = 0;
        public boolean autoPlay = false;
        public boolean showLastFrameOnStop = false;
        public ApngConfig(int numPlays, boolean autoPlay, boolean showLastFrameOnStop) {
            this.numPlays = numPlays;
            this.autoPlay = autoPlay;
            this.showLastFrameOnStop = showLastFrameOnStop;
        }
    }
}
