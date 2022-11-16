package com.sneva.spng.assist;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.sneva.spng.ApngImageLoader;

public class PngImageLoader extends ImageLoader {
    private static PngImageLoader singleton;

    public static PngImageLoader getInstance() {
        if (singleton == null) {
            synchronized (ApngImageLoader.class) {
                if (singleton == null) {
                    singleton = new PngImageLoader();
                }
            }
        }
        return singleton;
    }

    protected PngImageLoader() { /*Singleton*/ }
}
