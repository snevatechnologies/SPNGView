package com.sneva.spng.assist;

import static com.sneva.spng.ApngImageLoader.enableDebugLog;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.utils.DiskCacheUtils;
import com.nostra13.universalimageloader.utils.MemoryCacheUtils;
import com.sneva.spng.ApngDrawable;
import com.sneva.spng.ApngImageLoader;
import com.sneva.spng.R;
import com.sneva.spng.Slogger;

import java.io.File;

public class ApngImageLoadingListener implements ImageLoadingListener {
    private final ApngImageLoaderCallback callback;
    private final Context context;
    private final Uri uri;

    public ApngImageLoadingListener(Context context, Uri uri, ApngImageLoaderCallback callback) {
        this.context = context;
        this.uri = uri;
        this.callback = callback;
    }

    @Override
    public void onLoadingStarted(String imageUri, View view) {
        if (view == null) return;
        view.setTag(R.id.tag_image, uri.toString());
    }

    @Override
    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
        if (view == null) return;

        Object tag = view.getTag(R.id.tag_image);
        if (enableDebugLog) Slogger.d("tag: %s", tag);
        if (tag != null && tag instanceof String) {
            String actualUri = tag.toString();
            File pngFile = AssistUtil.getCopiedFile(context, actualUri);
            if (pngFile == null) {
                if (enableDebugLog) Slogger.w("Can't locate the file!!! %s", actualUri);
            } else if (pngFile.exists()) {
                boolean isApng = AssistUtil.isApng(pngFile);
                if (isApng) {
                    if (enableDebugLog) Slogger.d("Setup apng drawable");
                    ApngDrawable drawable = new ApngDrawable(context, loadedImage, Uri.fromFile(pngFile));
                    ((ImageView) view).setImageDrawable(drawable);
                } else {
                    ((ImageView) view).setImageBitmap(loadedImage);
                }
            } else {
                if (enableDebugLog) Slogger.d("Clear cache and reload");
                MemoryCacheUtils.removeFromCache(actualUri, ApngImageLoader.getInstance().getMemoryCache());
                DiskCacheUtils.removeFromCache(actualUri, ApngImageLoader.getInstance().getDiskCache());
                ApngImageLoader.getInstance().displayImage(actualUri, (ImageView) view, this);
            }
        }
        if (shouldForward()) callback.onLoadFinish(true, imageUri, view);
    }

    @Override
    public void onLoadingCancelled(String imageUri, View view) {
        if (view == null) return;
        Object tag = view.getTag(R.id.tag_image);
        if (enableDebugLog) Slogger.d("tag: %s", tag);
        view.setTag(R.id.tag_image, null);
        if (shouldForward()) callback.onLoadFinish(false, imageUri, view);
    }

    @Override
    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
        if (view == null) return;
        Object tag = view.getTag(R.id.tag_image);
        if (enableDebugLog) Slogger.d("tag: %s", tag);
        view.setTag(R.id.tag_image, null);
        if (shouldForward()) callback.onLoadFinish(false, imageUri, view);
    }

    private boolean shouldForward() {
        return callback != null;
    }
}