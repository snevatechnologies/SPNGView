package com.sneva.spng.decoder

import android.content.Context
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RawRes
import kotlinx.coroutines.*
import com.sneva.spng.drawable.ApngDrawable
import com.sneva.spng.utils.Utils.Companion.mapResult
import java.io.File
import java.io.FileInputStream
import java.net.URL

class ApngLoader(parent: Job? = null) {
    interface Callback {
        fun onSuccess(drawable: Drawable)
        fun onError(error: Throwable)
    }

    private val job = SupervisorJob(parent)
    private val coroutineScope: CoroutineScope = CoroutineScope(job)

    fun cancelAll() {
        coroutineScope.cancel(CancellationException("Loading was canceled"))
    }

    suspend fun decodeApngInto(
        context: Context,
        file: File,
        imageView: ImageView,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ): Result<Drawable> =
        kotlin.runCatching {
            withContext(Dispatchers.IO) {
                FileInputStream(file)
            }
        }.mapResult { input ->
            ApngDecoder(
                input,
                config
            ).getDecoded(context)
        }.onSuccess { drawable ->
            withContext(Dispatchers.Main) {
                imageView.setImageDrawable(drawable)
                (drawable as? AnimationDrawable)?.start()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    (drawable as? AnimatedImageDrawable)?.start()
                }
            }
        }

    suspend fun decodeApngInto(
        context: Context,
        uri: Uri,
        imageView: ImageView,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ): Result<Drawable> =
        kotlin.runCatching {
            withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) }!!
        }.mapResult { inputStream ->
            ApngDecoder(
                inputStream,
                config
            ).getDecoded(context)
        }.onSuccess { drawable ->
            withContext(Dispatchers.Main) {
                imageView.setImageDrawable(drawable)
                (drawable as? AnimationDrawable)?.start()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    (drawable as? AnimatedImageDrawable)?.start()
                }
            }
        }

    suspend fun decodeApngInto(
        context: Context, @RawRes res: Int,
        imageView: ImageView,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ): Result<Drawable> =
        ApngDecoder(
            withContext(Dispatchers.IO) {
                context.resources.openRawResource(res)
            },
            config
        ).getDecoded(context)
            .onSuccess { drawable ->
                withContext(Dispatchers.Main) {
                    imageView.setImageDrawable(drawable)
                    (drawable as? AnimationDrawable)?.start()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        (drawable as? AnimatedImageDrawable)?.start()
                    }
                }
            }

    suspend fun decodeApngInto(
        context: Context,
        url: URL,
        imageView: ImageView,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ): Result<Drawable> =
        ApngDecoder.constructFromUrl(url, config).getOrElse { return Result.failure(it) }
            .getDecoded(context)
            .onSuccess { drawable ->
                withContext(Dispatchers.Main) {
                    imageView.setImageDrawable(drawable)
                    (drawable as? AnimationDrawable)?.start()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        (drawable as? AnimatedImageDrawable)?.start()
                    }
                }
            }

    @Suppress("unused")
    suspend fun decodeApngInto(
        context: Context,
        string: String,
        imageView: ImageView,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ): Result<Drawable> {
        return if (string.startsWith("http://") || string.startsWith("https://")) {
            kotlin.runCatching { URL(string) }
                .mapResult { url ->
                    decodeApngInto(
                        context,
                        url,
                        imageView,
                        config
                    )
                }

        } else if (File(string).exists()) {
            var pathToLoad =
                if (string.startsWith("content://")) string else "file://$string"
            pathToLoad = pathToLoad.replace("%", "%25").replace("#", "%23")
            decodeApngInto(
                context,
                Uri.parse(pathToLoad),
                imageView,
                config
            )
        } else if (string.startsWith("file://android_asset/")) {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    context.assets.open(string.replace("file:///android_asset/", ""))
                }
            }
                .mapResult { inputStream -> ApngDecoder(inputStream, config).getDecoded(context) }
                .onSuccess { drawable ->
                    withContext(Dispatchers.Main) {
                        imageView.setImageDrawable(drawable)
                        (drawable as? AnimationDrawable)?.start()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            (drawable as? AnimatedImageDrawable)?.start()
                        }
                    }
                }
        } else {
            throw Exception("Cannot open string")
        }
    }

    @Suppress("unused")
    @JvmOverloads
    fun decodeApngAsyncInto(
        context: Context,
        file: File,
        imageView: ImageView,
        callback: Callback? = null,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ) =
        coroutineScope.launch(Dispatchers.Default) {
            val drawable = decodeApngInto(context, file, imageView, config)
            withContext(Dispatchers.Main) {
                drawable
                    .onSuccess { callback?.onSuccess(it) }
                    .onFailure { callback?.onError(it) }
            }
        }

    @Suppress("unused")
    @JvmOverloads
    fun decodeApngAsyncInto(
        context: Context,
        uri: Uri,
        imageView: ImageView,
        callback: Callback? = null,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ) = coroutineScope.launch(Dispatchers.Default) {
        val drawable = decodeApngInto(context, uri, imageView, config)
        withContext(Dispatchers.Main) {
            drawable
                .onSuccess { callback?.onSuccess(it) }
                .onFailure { callback?.onError(it) }
        }
    }

    @Suppress("unused")
    @JvmOverloads
    fun decodeApngAsyncInto(
        context: Context, @RawRes res: Int,
        imageView: ImageView,
        callback: Callback? = null,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ) = coroutineScope.launch(Dispatchers.Default) {
        val drawable = decodeApngInto(context, res, imageView, config)
        withContext(Dispatchers.Main) {
            drawable
                .onSuccess { callback?.onSuccess(it) }
                .onFailure { callback?.onError(it) }
        }
    }

    @Suppress("unused")
    @JvmOverloads
    fun decodeApngAsyncInto(
        context: Context,
        url: URL,
        imageView: ImageView,
        callback: Callback? = null,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ) = coroutineScope.launch(Dispatchers.Default) {
        val drawable = decodeApngInto(context, url, imageView, config)
        withContext(Dispatchers.Main) {
            drawable
                .onSuccess { callback?.onSuccess(it) }
                .onFailure { callback?.onError(it) }
        }
    }

    @Suppress("unused")
    @JvmOverloads
    fun decodeApngAsyncInto(
        context: Context,
        string: String,
        imageView: ImageView,
        callback: Callback? = null,
        config: ApngDecoder.Config = ApngDecoder.Config()
    ) =
        coroutineScope.launch(Dispatchers.Default) {
            val drawable = decodeApngInto(context, string, imageView, config)
            withContext(Dispatchers.Main) {
                if (drawable.isSuccess) {
                    callback?.onSuccess(drawable.getOrNull()!!)
                } else {
                    callback?.onError(drawable.exceptionOrNull()!!)
                }
            }
        }
}