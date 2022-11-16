package com.sneva.spng.assist;

import android.view.View;

public interface ApngImageLoaderCallback {
    void onLoadFinish(boolean success, String imageUri, View view);
}
