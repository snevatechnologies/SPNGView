package com.sneva.spng.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.sneva.spng.exceptions.BadBitmapsDiffSize
import com.sneva.spng.utils.Utils.Companion.BlendOp.APNG_BLEND_OP_OVER
import com.sneva.spng.utils.Utils.Companion.BlendOp.APNG_BLEND_OP_SOURCE
import com.sneva.spng.utils.Utils.Companion.DisposeOp.*
import kotlin.experimental.and

class Utils {
    companion object {
        fun isPng(byteArray: ByteArray): Boolean {
            return if (byteArray.size == 8)
                byteArray.contentEquals(pngSignature)
            else
                byteArray.copyOfRange(0, 8).contentEquals(pngSignature)
        }

        @Deprecated("Will be removed with ApngAnimator and APNGDisassembler")
        fun isApng(byteArray: ByteArray): Boolean {
            if (!isPng(byteArray)) return false
            try {
                for (i in 8 until byteArray.size) {
                    val it = byteArray.copyOfRange(i, i + 4)
                    if (it.contentEquals(acTL)) {
                        return true
                    } else if (it.contentEquals(IDAT)) {
                        return false
                    }
                }
                return false
            } catch (e: Exception) {
                return false
            }
        }

        val pngSignature: ByteArray by lazy {
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
            )
        }

        enum class DisposeOp {
            APNG_DISPOSE_OP_NONE,
            APNG_DISPOSE_OP_BACKGROUND,
            APNG_DISPOSE_OP_PREVIOUS
        }

        fun encodeDisposeOp(disposeOp: DisposeOp): Int {
            return when (disposeOp) {
                APNG_DISPOSE_OP_NONE -> 0
                APNG_DISPOSE_OP_BACKGROUND -> 1
                APNG_DISPOSE_OP_PREVIOUS -> 2
            }
        }

        fun decodeDisposeOp(int: Int): DisposeOp {
            return when (int) {
                0 -> APNG_DISPOSE_OP_NONE
                1 -> APNG_DISPOSE_OP_BACKGROUND
                2 -> APNG_DISPOSE_OP_PREVIOUS
                else -> APNG_DISPOSE_OP_NONE
            }
        }

        enum class BlendOp {
            APNG_BLEND_OP_SOURCE,
            APNG_BLEND_OP_OVER
        }

        fun encodeBlendOp(blendOp: BlendOp): Int {
            return when (blendOp) {
                APNG_BLEND_OP_SOURCE -> 0
                APNG_BLEND_OP_OVER -> 1
            }
        }

        fun decodeBlendOp(int: Int): BlendOp {
            return when (int) {
                0 -> APNG_BLEND_OP_SOURCE
                1 -> APNG_BLEND_OP_OVER
                else -> APNG_BLEND_OP_SOURCE
            }
        }

        fun uShortToArray(i: Int): Array<Byte> {
            return arrayOf((i shr 24).toByte(), (i shr 16).toByte(), (i shr 8).toByte(), i.toByte())
        }

        fun uIntToByteArray(i: Int): ByteArray {
            return byteArrayOf(
                (i shr 24).toByte(),
                (i shr 16).toByte(),
                (i shr 8).toByte(),
                i.toByte()
            )
        }

        fun uShortToByteArray(i: Int): ByteArray {
            return byteArrayOf((i shr 8).toByte(), i /*>> 0*/.toByte())
        }

        fun uShortToByteArray(s: Short): ByteArray {
            return byteArrayOf((s.toInt() shr 8 and 0x00FF).toByte(), (s and 0xFF).toByte())
        }

        fun uIntFromBytesBigEndian(bytes: List<Int>): Int =
            ((bytes[0] and 0xFF) shl 24) or
                    ((bytes[1] and 0xFF) shl 16) or
                    ((bytes[2] and 0xFF) shl 8) or
                    (bytes[3] and 0xFF)

        fun uIntFromBytesBigEndian(bytes: ByteArray, offset: Int = 0): Int =
            ((bytes[offset + 0].toInt() and 0xFF) shl 24) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 3].toInt() and 0xFF)

        fun uShortFromBytesBigEndian(bytes: List<Int>): Int =
            (((bytes[0] and 0xFF) shl 8) or
                    (bytes[1] and 0xFF))

        fun uShortFromBytesBigEndian(bytes: ByteArray, offset: Int = 0): Int =
            (((bytes[offset].toInt() and 0xFF) shl 8) or
                    (bytes[offset + 1].toInt() and 0xFF))

        val fcTL: ByteArray by lazy { byteArrayOf(0x66, 0x63, 0x54, 0x4c) }
        val IEND: ByteArray by lazy { byteArrayOf(0x49, 0x45, 0x4e, 0x44) }
        val IDAT: ByteArray by lazy { byteArrayOf(0x49, 0x44, 0x41, 0x54) }
        val fdAT: ByteArray by lazy { byteArrayOf(0x66, 0x64, 0x41, 0x54) }
        val plte: ByteArray by lazy { byteArrayOf(0x50, 0x4c, 0x54, 0x45) }
        val tnrs: ByteArray by lazy { byteArrayOf(0x74, 0x52, 0x4e, 0x53) }
        val IHDR: ByteArray by lazy { byteArrayOf(0x49, 0x48, 0x44, 0x52) }
        val acTL: ByteArray by lazy { byteArrayOf(0x61, 0x63, 0x54, 0x4c) }

        data class DiffResult(
            val bitmap: Bitmap,
            val offsetX: Int,
            val offsetY: Int,
            val blendOp: BlendOp
        )

        @Throws(BadBitmapsDiffSize::class)
        fun getDiffBitmap(firstBitmap: Bitmap, secondBitmap: Bitmap): DiffResult {
            if (firstBitmap.width < secondBitmap.width || firstBitmap.height < secondBitmap.height) {
                throw BadBitmapsDiffSize(
                    firstBitmap.width,
                    firstBitmap.height,
                    firstBitmap.width,
                    firstBitmap.height
                )
            }

            val resultBtm = Bitmap.createBitmap(
                secondBitmap.width,
                secondBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            var offsetX = resultBtm.width + 1
            var offsetY = resultBtm.height + 1

            var lastX = 0
            var lastY = 0

            val blendOp =
                if (containTransparency(secondBitmap)) APNG_BLEND_OP_SOURCE else APNG_BLEND_OP_OVER

            for (y in 0 until secondBitmap.height) {
                for (x in 0 until secondBitmap.width) {
                    val btmPixel = secondBitmap.getPixel(x, y)
                    if (firstBitmap.getPixel(
                            x,
                            y
                        ) == btmPixel
                    ) {
                        if (blendOp == APNG_BLEND_OP_OVER)
                            resultBtm.setPixel(x, y, Color.TRANSPARENT)
                        else
                            resultBtm.setPixel(x, y, firstBitmap.getPixel(x, y))
                    } else {
                        resultBtm.setPixel(x, y, btmPixel)
                        if (x < offsetX)
                            offsetX = x
                        if (y < offsetY)
                            offsetY = y
                        if (x > lastX)
                            lastX = x
                        if (y > lastY)
                            lastY = y
                    }
                }
            }

            lastX++
            lastY++

            val newWidth = lastX - offsetX
            val newHeight = lastY - offsetY

            val resizedResultBtm =
                Bitmap.createBitmap(resultBtm, offsetX, offsetY, newWidth, newHeight)

            return DiffResult(resizedResultBtm, offsetX, offsetY, blendOp)
        }

        fun containTransparency(btm: Bitmap): Boolean {
            var result = false
            var y = 0
            var x = 0
            while (y < btm.height && !result) {
                if (btm.getPixel(x, y) == Color.TRANSPARENT) {
                    result = true
                }

                x++
                if (x == btm.width) {
                    x = 0
                    y++
                }
            }
            return result
        }

        suspend fun <T, U> Result<T>.mapResult(block: suspend (T) -> Result<U>): Result<U> {
            return this.fold(
                onSuccess = {
                    block.invoke(it)
                },
                onFailure = { Result.failure(it) }
            )
        }
    }
}