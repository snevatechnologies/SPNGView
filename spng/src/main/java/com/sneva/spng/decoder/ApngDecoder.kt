package com.sneva.spng.decoder

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RawRes
import com.sneva.spng.exceptions.BadApngException
import com.sneva.spng.exceptions.BadCRCException
import com.sneva.spng.utils.Loader
import com.sneva.spng.utils.Utils
import kotlinx.coroutines.*
import com.sneva.spng.BuildConfig
import com.sneva.spng.drawable.ApngDrawable
import java.io.*
import java.net.URL
import java.nio.ByteBuffer
import java.util.zip.CRC32

class ApngDecoder(input: InputStream, val config: Config) {
    class Config(
        internal var speed: Float = 1f,
        internal var bitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888,
        internal var decodeCoverFrame: Boolean = false
    ) {
        fun getSpeed(): Float = this.speed
        fun setSpeed(speed: Float): Config {
            this.speed = speed
            return this
        }

        fun getBitmapConfig(): Bitmap.Config = this.bitmapConfig
        fun setBitmapConfig(config: Bitmap.Config): Config {
            this.bitmapConfig = config
            return this
        }

        fun isDecodingCoverFrame(): Boolean {
            return this.decodeCoverFrame
        }

        fun setIsDecodingCoverFrame(decodeCoverFrame: Boolean): Config {
            this.decodeCoverFrame = decodeCoverFrame
            return this
        }
    }

    private var inputStream: InputStream? = input
    private var result: Result<Drawable>? = null
    suspend fun decodeApng(
        context: Context
    ): Result<Drawable> =
        kotlin.runCatching {
            withContext(Dispatchers.Default) {
                val inputStream = BufferedInputStream(inputStream)
                val bytes = ByteArray(8)
                inputStream.mark(8)

                withContext(Dispatchers.IO) {
                    inputStream.read(bytes)
                }

                if (Utils.isPng(bytes)) {
                    var png: ByteArrayOutputStream? = null
                    var cover: ByteArrayOutputStream? = null
                    var delay = -1f
                    var yOffset = -1
                    var xOffset = -1
                    var plte: ByteArray? = null
                    var tnrs: ByteArray? = null
                    var maxWidth = 0
                    var maxHeight = 0
                    var blendOp: Utils.Companion.BlendOp =
                        Utils.Companion.BlendOp.APNG_BLEND_OP_SOURCE
                    var disposeOp: Utils.Companion.DisposeOp =
                        Utils.Companion.DisposeOp.APNG_DISPOSE_OP_NONE
                    var ihdrOfApng = ByteArray(0)
                    var isApng = false
                    val drawable = ApngDrawable().apply {
                        isOneShot = false
                    }

                    var buffer: Bitmap? = null
                    var byteRead: Int
                    val lengthChunk = ByteArray(4)
                    do {
                        val length: Int
                        val chunk: ByteArray
                        if (withContext(Dispatchers.IO) {
                                byteRead = inputStream.read(lengthChunk)

                                if (byteRead != -1) {
                                    length = Utils.uIntFromBytesBigEndian(lengthChunk)

                                    chunk = ByteArray(length + 8)
                                    byteRead = inputStream.read(chunk)
                                    false
                                } else {
                                    chunk = ByteArray(0)
                                    true
                                }
                            }) {
                            break
                        }

                        val byteArray = lengthChunk.plus(chunk)
                        val chunkCRC = Utils.uIntFromBytesBigEndian(byteArray, byteArray.size - 4)
                        val crc = CRC32()
                        crc.update(byteArray, 4, byteArray.size - 8)
                        if (chunkCRC == crc.value.toInt()) {
                            val name = byteArray.copyOfRange(4, 8)
                            when {
                                name.contentEquals(Utils.fcTL) -> {
                                    if (png == null) {
                                        if (config.decodeCoverFrame) {
                                            drawable.coverFrame = cover?.let {
                                                it.write(zeroLength)
                                                // Generate crc for IEND
                                                val crC32 = CRC32()
                                                crC32.update(Utils.IEND, 0, Utils.IEND.size)
                                                it.write(Utils.IEND)
                                                it.write(Utils.uIntToByteArray(crC32.value.toInt()))

                                                val pngBytes = it.toByteArray()
                                                BitmapFactory.decodeByteArray(
                                                    pngBytes,
                                                    0,
                                                    pngBytes.size
                                                )
                                            }
                                        }
                                        cover?.close()
                                        cover = null
                                    } else {
                                        png.write(zeroLength)
                                        val crC32 = CRC32()
                                        crC32.update(Utils.IEND, 0, Utils.IEND.size)
                                        png.write(Utils.IEND)
                                        png.write(Utils.uIntToByteArray(crC32.value.toInt()))

                                        val btm = Bitmap.createBitmap(
                                            maxWidth,
                                            maxHeight,
                                            Bitmap.Config.ARGB_8888
                                        )

                                        val pngBytes = png.toByteArray()
                                        val decoded = BitmapFactory.decodeByteArray(
                                            pngBytes,
                                            0,
                                            pngBytes.size
                                        )
                                        val canvas = Canvas(btm)
                                        canvas.drawBitmap(buffer!!, 0f, 0f, null)

                                        if (blendOp == Utils.Companion.BlendOp.APNG_BLEND_OP_SOURCE) {
                                            canvas.drawRect(
                                                xOffset.toFloat(),
                                                yOffset.toFloat(),
                                                xOffset + decoded.width.toFloat(),
                                                yOffset + decoded.height.toFloat(),
                                                clearPaint
                                            )
                                        }

                                        canvas.drawBitmap(
                                            decoded,
                                            xOffset.toFloat(),
                                            yOffset.toFloat(),
                                            null
                                        )

                                        drawable.addFrame(
                                            BitmapDrawable(
                                                context.resources,
                                                if (btm.config != config.bitmapConfig) {
                                                    if (BuildConfig.DEBUG)
                                                        Log.v(
                                                            TAG,
                                                            "Bitmap Config : ${btm.config}, Config : $config"
                                                        )
                                                    btm.copy(config.bitmapConfig, btm.isMutable)
                                                } else {
                                                    btm
                                                }
                                            ),
                                            (delay / config.speed).toInt()
                                        )

                                        when (disposeOp) {
                                            Utils.Companion.DisposeOp.APNG_DISPOSE_OP_PREVIOUS -> {
                                            }
                                            Utils.Companion.DisposeOp.APNG_DISPOSE_OP_BACKGROUND -> {
                                                val res = Bitmap.createBitmap(
                                                    maxWidth,
                                                    maxHeight,
                                                    Bitmap.Config.ARGB_8888
                                                )
                                                val can = Canvas(res)
                                                can.drawBitmap(btm, 0f, 0f, null)
                                                can.drawRect(
                                                    xOffset.toFloat(),
                                                    yOffset.toFloat(),
                                                    xOffset + decoded.width.toFloat(),
                                                    yOffset + decoded.height.toFloat(),
                                                    clearPaint
                                                )
                                                buffer = res
                                            }
                                            else -> buffer = btm
                                        }
                                    }

                                    png?.close()
                                    png = ByteArrayOutputStream(4096)
                                    val width = Utils.uIntFromBytesBigEndian(
                                        byteArray, 12
                                    )
                                    val height = Utils.uIntFromBytesBigEndian(
                                        byteArray, 16
                                    )
                                    val delayNum = Utils.uShortFromBytesBigEndian(
                                        byteArray, 28
                                    ).toFloat()
                                    var delayDen = Utils.uShortFromBytesBigEndian(
                                        byteArray, 30
                                    ).toFloat()
                                    if (delayDen == 0f) {
                                        delayDen = 100f
                                    }

                                    delay = (delayNum / delayDen * 1000)
                                    xOffset = Utils.uIntFromBytesBigEndian(
                                        byteArray, 20
                                    )
                                    yOffset = Utils.uIntFromBytesBigEndian(
                                        byteArray, 24
                                    )
                                    blendOp = Utils.decodeBlendOp(byteArray[33].toInt())
                                    disposeOp = Utils.decodeDisposeOp(byteArray[32].toInt())

                                    if (xOffset + width > maxWidth) {
                                        throw BadApngException("`xOffset` + `width` must be <= `IHDR` width")
                                    } else if (yOffset + height > maxHeight) {
                                        throw BadApngException("`yOffset` + `height` must be <= `IHDR` height")
                                    }

                                    png.write(Utils.pngSignature)
                                    png.write(
                                        generateIhdr(
                                            ihdrOfApng,
                                            width,
                                            height
                                        )
                                    )
                                    plte?.let {
                                        png.write(it)
                                    }
                                    tnrs?.let {
                                        png.write(it)
                                    }

                                }
                                name.contentEquals(Utils.IEND) -> {
                                    if (isApng && png != null) {
                                        png.write(zeroLength)
                                        val crC32 = CRC32()
                                        crC32.update(Utils.IEND, 0, Utils.IEND.size)
                                        png.write(Utils.IEND)
                                        png.write(Utils.uIntToByteArray(crC32.value.toInt()))

                                        val btm = Bitmap.createBitmap(
                                            maxWidth,
                                            maxHeight,
                                            Bitmap.Config.ARGB_8888
                                        )

                                        val pngBytes = png.toByteArray()
                                        png.close()
                                        val decoded = BitmapFactory.decodeByteArray(
                                            pngBytes,
                                            0,
                                            pngBytes.size
                                        )
                                        val canvas = Canvas(btm)
                                        canvas.drawBitmap(buffer!!, 0f, 0f, null)

                                        if (blendOp == Utils.Companion.BlendOp.APNG_BLEND_OP_SOURCE) {
                                            canvas.drawRect(
                                                xOffset.toFloat(),
                                                yOffset.toFloat(),
                                                xOffset + decoded.width.toFloat(),
                                                yOffset + decoded.height.toFloat(),
                                                clearPaint
                                            )
                                        }

                                        canvas.drawBitmap(
                                            decoded,
                                            xOffset.toFloat(),
                                            yOffset.toFloat(),
                                            null
                                        )
                                        drawable.addFrame(
                                            BitmapDrawable(
                                                context.resources,
                                                if (btm.config != config.bitmapConfig) {
                                                    if (BuildConfig.DEBUG)
                                                        Log.v(
                                                            TAG,
                                                            "Bitmap Config : ${btm.config}, Config : $config"
                                                        )
                                                    btm.copy(config.bitmapConfig, btm.isMutable)
                                                } else {
                                                    btm
                                                }
                                            ),
                                            (delay / config.speed).toInt()
                                        )

                                        when (disposeOp) {
                                            Utils.Companion.DisposeOp.APNG_DISPOSE_OP_PREVIOUS -> {
                                            }
                                            Utils.Companion.DisposeOp.APNG_DISPOSE_OP_BACKGROUND -> {
                                                val res = Bitmap.createBitmap(
                                                    maxWidth,
                                                    maxHeight,
                                                    Bitmap.Config.ARGB_8888
                                                )
                                                val can = Canvas(res)
                                                can.drawBitmap(btm, 0f, 0f, null)
                                                can.drawRect(
                                                    xOffset.toFloat(),
                                                    yOffset.toFloat(),
                                                    xOffset + decoded.width.toFloat(),
                                                    yOffset + decoded.height.toFloat(),
                                                    clearPaint
                                                )
                                                buffer = res
                                            }
                                            else -> buffer = btm
                                        }
                                    } else {
                                        cover?.let {
                                            it.write(zeroLength)
                                            val crC32 = CRC32()
                                            crC32.update(Utils.IEND, 0, Utils.IEND.size)
                                            it.write(Utils.IEND)
                                            it.write(Utils.uIntToByteArray(crC32.value.toInt()))

                                            withContext(Dispatchers.IO) {
                                                inputStream.close()
                                            }

                                            val pngBytes = it.toByteArray()
                                            it.close()

                                            return@withContext BitmapDrawable(
                                                context.resources,
                                                BitmapFactory.decodeByteArray(
                                                    pngBytes,
                                                    0,
                                                    pngBytes.size
                                                )
                                            )
                                        }
                                    }
                                }
                                name.contentEquals(Utils.IDAT) -> {
                                    val w = if (png == null) {
                                        if (isApng && !config.decodeCoverFrame) {
                                            if (BuildConfig.DEBUG)
                                                Log.d(TAG, "Ignoring cover frame")
                                            continue
                                        }
                                        if (cover == null) {
                                            cover = ByteArrayOutputStream()
                                            cover.write(Utils.pngSignature)
                                            cover.write(
                                                generateIhdr(
                                                    ihdrOfApng,
                                                    maxWidth,
                                                    maxHeight
                                                )
                                            )
                                        }
                                        cover
                                    } else {
                                        png
                                    }

                                    val bodySize =
                                        Utils.uIntFromBytesBigEndian(
                                            byteArray, 0
                                        )
                                    w.write(byteArray.copyOfRange(0, 4))
                                    val body = ByteArray(4 + bodySize)
                                    System.arraycopy(Utils.IDAT, 0, body, 0, 4)
                                    System.arraycopy(byteArray, 8, body, 4, bodySize)
                                    val crC32 = CRC32()
                                    crC32.update(body, 0, body.size)
                                    w.write(body)
                                    w.write(Utils.uIntToByteArray(crC32.value.toInt()))
                                }
                                name.contentEquals(Utils.fdAT) -> {
                                    val bodySize = Utils.uIntFromBytesBigEndian(byteArray, 0)
                                    png?.write(Utils.uIntToByteArray(bodySize - 4))
                                    val body = ByteArray(bodySize)
                                    System.arraycopy(Utils.IDAT, 0, body, 0, 4)
                                    System.arraycopy(byteArray, 12, body, 4, bodySize - 4)
                                    val crC32 = CRC32()
                                    crC32.update(body, 0, body.size)
                                    png?.write(body)
                                    png?.write(Utils.uIntToByteArray(crC32.value.toInt()))
                                }
                                name.contentEquals(Utils.plte) -> {
                                    plte = byteArray
                                }
                                name.contentEquals(Utils.tnrs) -> {
                                    tnrs = byteArray
                                }
                                name.contentEquals(Utils.IHDR) -> {
                                    val bodySize = Utils.uIntFromBytesBigEndian(byteArray, 0)
                                    maxWidth = Utils.uIntFromBytesBigEndian(byteArray, 8)
                                    maxHeight = Utils.uIntFromBytesBigEndian(byteArray, 12)
                                    ihdrOfApng = byteArray.copyOfRange(4 + 4, 4 + bodySize + 4)

                                    buffer = Bitmap.createBitmap(
                                        maxWidth,
                                        maxHeight,
                                        Bitmap.Config.ARGB_8888
                                    )
                                }
                                name.contentEquals(Utils.acTL) -> {
                                    isApng = true
                                }
                            }
                        } else throw BadCRCException()
                    } while (byteRead != -1 && isActive)
                    withContext(Dispatchers.IO) {
                        inputStream.close()
                    }
                    drawable
                } else {
                    if (BuildConfig.DEBUG)
                        Log.i(TAG, "Decoding non APNG stream")
                    inputStream.reset()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val bytesRead: ByteArray
                        withContext(Dispatchers.IO) {
                            bytesRead = inputStream.readBytes()
                            inputStream.close()
                        }
                        val buf = ByteBuffer.wrap(bytesRead)
                        val source = ImageDecoder.createSource(buf)
                        withContext(Dispatchers.IO) {
                            ImageDecoder.decodeDrawable(source)
                        }
                    } else {
                        val drawable = Drawable.createFromStream(
                            inputStream,
                            null
                        )
                        withContext(Dispatchers.IO) {
                            inputStream.close()
                        }
                        drawable!!
                    }
                }
            }
        }

    suspend fun getDecoded(context: Context): Result<Drawable> {
        if (result == null) {
            result = decodeApng(context)

            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    inputStream?.close()
                }
            }.onFailure {
                this.result = Result.failure(it)
            }

            inputStream = null
        }

        return result ?: Result.failure(NullPointerException("result is null"))
    }

    constructor(file: File, config: Config = Config()) : this(FileInputStream(file), config)
    constructor(
        context: Context,
        uri: Uri,
        config: Config = Config()
    ) : this(context.contentResolver.openInputStream(uri)!!, config)

    constructor(
        context: Context,
        @RawRes res: Int,
        config: Config = Config()
    ) : this(context.resources.openRawResource(res), config)

    private fun generateIhdr(ihdrOfApng: ByteArray, width: Int, height: Int): ByteArray {
        val ihdr =
            ByteArray(0xD + 4 + 4 + 4)
        System.arraycopy(Utils.uIntToByteArray(0xD), 0, ihdr, 0, 4)
        val ihdrBody = ByteArray(0xD + 4) // 0xD (IHDR body length) + 4 : IHDR
        System.arraycopy(Utils.IHDR, 0, ihdrBody, 0, 4)
        System.arraycopy(Utils.uIntToByteArray(width), 0, ihdrBody, 4, 4)
        System.arraycopy(Utils.uIntToByteArray(height), 0, ihdrBody, 8, 4)
        System.arraycopy(ihdrOfApng, 8, ihdrBody, 12, 5)
        val crC32 = CRC32()
        crC32.update(ihdrBody, 0, 0xD + 4)
        System.arraycopy(ihdrBody, 0, ihdr, 4, 0xD + 4)
        System.arraycopy(Utils.uIntToByteArray(crC32.value.toInt()), 0, ihdr, 0xD + 4 + 4, 4)
        return ihdr
    }

    companion object {
        private const val TAG = "ApngDecoder"
        private val zeroLength = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        private val clearPaint: Paint by lazy {
            Paint().apply {
                xfermode = PorterDuffXfermode(
                    PorterDuff.Mode.CLEAR
                )
            }
        }

        @Suppress("unused")
        @JvmStatic
        suspend fun constructFromUrl(
            url: URL,
            config: Config = Config()
        ): Result<ApngDecoder> =
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    ApngDecoder(
                        ByteArrayInputStream(Loader.load(url)),
                        config
                    )
                }
            }
    }
}