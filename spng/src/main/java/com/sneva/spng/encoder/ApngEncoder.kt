package com.sneva.spng.encoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.sneva.spng.exceptions.BadParameterException
import com.sneva.spng.exceptions.InvalidFrameSizeException
import com.sneva.spng.utils.Utils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.max
import kotlin.math.min

class ApngEncoder(
    private val outputStream: OutputStream,
    private val width: Int,
    private val height: Int,
    numberOfFrames: Int
) {
    companion object {
        private const val TAG = "ApngEncoder"
        const val FILTER_NONE = 0
        const val FILTER_SUB = 1
        const val FILTER_UP = 2
        const val FILTER_LAST = 2
    }

    private var currentFrame = 0
    private var currentSeq = 0
    private var crc = CRC32()
    private var crcValue: Long = 0
    private var bytesPerPixel: Int = 0
    private var compressionLevel: Int = 0
    private var filter: Int = FILTER_NONE
    private var encodeAlpha: Boolean = true
    private var priorRow: ByteArray? = null
    private var leftBytes: ByteArray? = null
    private var repetitionCount: Int = 0
    private var firstFrameInAnim: Boolean = true
    private var optimise : Boolean = true
    private var bitmapBuffer : Bitmap? = null

    init {
        outputStream.write(Utils.pngSignature)
        writeHeader()
        writeACTL(numberOfFrames)
    }

    fun setEncodeAlpha(encodeAlpha: Boolean): ApngEncoder {
        if (optimise && !encodeAlpha)
            throw BadParameterException("If encodeAlpha is false, then optimise must be false")
        this.encodeAlpha = encodeAlpha
        return this
    }

    fun isAlphaEncoded(): Boolean {
        return encodeAlpha
    }

    @Throws(BadParameterException::class)
    fun setFilter(filter: Int): ApngEncoder {
        if (filter <= FILTER_LAST) {
            this.filter = filter
        } else {
            throw BadParameterException("Invalid filter value")
        }
        return this
    }

    fun getFilter(): Int {
        return filter
    }

    @Suppress("unused")
    fun setRepetitionCount(repetitionCount: Int): ApngEncoder {
        this.repetitionCount = repetitionCount
        return this
    }

    @Suppress("unused")
    fun getRepetitionCount() : Int {
        return this.repetitionCount
    }

    @Throws(BadParameterException::class)
    fun setCompressionLevel(compressionLevel: Int): ApngEncoder {
        if (compressionLevel in 0..9) {
            this.compressionLevel = compressionLevel
        } else {
            throw BadParameterException("Invalid compression level : $compressionLevel, expected a number in range 0..9")
        }

        return this
    }

    fun getCompressionLevel() : Int {
        return this.compressionLevel
    }

    fun setIsFirstFrameInAnim(firstFrameInAnim: Boolean): ApngEncoder {
        this.firstFrameInAnim = firstFrameInAnim
        return this
    }

    @Suppress("unused")
    fun isFirstFrameInAnim() : Boolean {
        return this.firstFrameInAnim
    }

    fun setOptimiseApng(optimise : Boolean) : ApngEncoder {
        if (optimise && !encodeAlpha)
            throw BadParameterException("If optimise is set to true, then encodeAlpha must be true")
        this.optimise = optimise
        return this
    }

    fun isOptimisingApng() : Boolean {
        return this.optimise
    }

    @JvmOverloads
    @Throws(
        NullPointerException::class,
        InvalidFrameSizeException::class,
        IOException::class
    )
    fun writeFrame(
        inputStream: InputStream,
        delay: Float = 1000f,
        xOffsets: Int = 0,
        yOffsets: Int = 0,
        blendOp: Utils.Companion.BlendOp = Utils.Companion.BlendOp.APNG_BLEND_OP_SOURCE,
        disposeOp: Utils.Companion.DisposeOp = Utils.Companion.DisposeOp.APNG_DISPOSE_OP_NONE
    ) {
        val btm = BitmapFactory.decodeStream(inputStream)!!

        writeFrame(btm, delay, xOffsets, yOffsets, blendOp, disposeOp)
        btm.recycle()
    }

    @JvmOverloads
    @Throws(InvalidFrameSizeException::class, IOException::class)
    fun writeFrame(
        btm: Bitmap,
        delay: Float = 1000f,
        xOffsets: Int = 0,
        yOffsets: Int = 0,
        blendOp: Utils.Companion.BlendOp = Utils.Companion.BlendOp.APNG_BLEND_OP_SOURCE,
        disposeOp: Utils.Companion.DisposeOp = Utils.Companion.DisposeOp.APNG_DISPOSE_OP_NONE
    ) {
        if (currentFrame == 0) {
            if (btm.width != width || btm.height != height)
                throw InvalidFrameSizeException(
                    btm.width,
                    btm.height,
                    width,
                    height,
                    currentFrame == 0
                )
        }

        var frameBtm = btm
        var frameXOffsets = xOffsets
        var frameYOffsets = yOffsets
        var frameBlendOp = blendOp

        if (optimise && currentFrame != 0 || (currentFrame == 0 && firstFrameInAnim)) {
            if (bitmapBuffer == null) {
                bitmapBuffer = btm.copy(btm.config, false)
            } else {
                val diff = Utils.getDiffBitmap(bitmapBuffer!!, btm)
                frameBtm = diff.bitmap
                frameXOffsets = diff.offsetX
                frameYOffsets = diff.offsetY
                frameBlendOp = diff.blendOp
                bitmapBuffer = btm.copy(btm.config, false)
            }
        }

        if (frameBtm.width > width || frameBtm.height > height)
            throw InvalidFrameSizeException(frameBtm.width, frameBtm.height, width, height, currentFrame == 0)

        if (firstFrameInAnim || currentFrame != 0)
            writeFCTL(frameBtm, delay, disposeOp, frameBlendOp, frameXOffsets, frameYOffsets)
        writeImageData(frameBtm)
        currentFrame++
    }

    @Throws(IOException::class)
    fun writeEnd() {
        outputStream.write(byteArrayOf(0, 0, 0, 0))
        val iend = Utils.IEND
        crc.reset()
        crc.update(iend, 0, iend.size)
        outputStream.write(iend)
        outputStream.write(Utils.uIntToByteArray(crc.value.toInt()))
    }

    @Throws(IOException::class)
    private fun writeHeader() {
        writeInt4(13)

        val header = ByteArrayOutputStream(17)

        header.write(Utils.IHDR)
        header.write(Utils.uIntToByteArray(width))
        header.write(Utils.uIntToByteArray(height))
        header.write(8)
        header.write(if (encodeAlpha) 6 else 2)
        header.write(0)
        header.write(0)
        header.write(0)

        val headerBytes = header.toByteArray()
        outputStream.write(
            headerBytes
        )
        crc.reset()
        crc.update(headerBytes)
        crcValue = crc.value
        writeInt4(crcValue.toInt())
    }

    @Throws(IOException::class)
    private fun writeInt4(n: Int) {
        outputStream.write(Utils.uIntToByteArray(n))
    }

    @Throws(IOException::class)
    private fun writeACTL(num: Int) {
        outputStream.write(byteArrayOf(0, 0, 0, 0x08))
        val acTL = ByteArrayOutputStream(12)
        acTL.write(Utils.acTL)
        acTL.write(Utils.uIntToByteArray(num))
        acTL.write(Utils.uIntToByteArray(repetitionCount))
        val acTLBytes = acTL.toByteArray()
        outputStream.write(acTLBytes)
        crc.reset()
        crc.update(acTLBytes, 0, acTLBytes.size)
        outputStream.write(Utils.uIntToByteArray(crc.value.toInt()))
    }

    @Throws(IOException::class)
    private fun writeFCTL(
        btm: Bitmap,
        delay: Float,
        disposeOp: Utils.Companion.DisposeOp,
        blendOp: Utils.Companion.BlendOp,
        xOffsets: Int,
        yOffsets: Int
    ) {
        outputStream.write(byteArrayOf(0x00, 0x00, 0x00, 0x1A))
        val fcTL = ByteArrayOutputStream(30) // 0x1A + 4
        fcTL.write(Utils.fcTL)
        fcTL.write(Utils.uIntToByteArray(currentSeq++))
        fcTL.write(Utils.uIntToByteArray(btm.width))
        fcTL.write(Utils.uIntToByteArray(btm.height))
        fcTL.write(Utils.uIntToByteArray(xOffsets))
        fcTL.write(Utils.uIntToByteArray(yOffsets))
        fcTL.write(Utils.uShortToByteArray(delay.toInt().toShort()))
        fcTL.write(Utils.uShortToByteArray(1000.toShort()))
        fcTL.write(Utils.encodeDisposeOp(disposeOp))
        fcTL.write(Utils.encodeBlendOp(blendOp))
        val fcTLBytes = fcTL.toByteArray()
        crc.reset()
        crc.update(fcTLBytes, 0, fcTLBytes.size)
        outputStream.write(fcTLBytes)
        outputStream.write(Utils.uIntToByteArray(crc.value.toInt()))
    }

    private fun writeImageData(image: Bitmap): Boolean {
        var rowsLeft =  image.height
        var startRow = 0
        var nRows: Int
        var scanLines: ByteArray
        var scanPos: Int
        var startPos: Int
        val compressedLines: ByteArray
        val nCompressed: Int
        bytesPerPixel = if (encodeAlpha) 4 else 3
        val scrunch = Deflater(compressionLevel)
        val outBytes = ByteArrayOutputStream(1024)

        val compBytes = DeflaterOutputStream(outBytes, scrunch)
        try {
            while (rowsLeft > 0) {
                nRows = min(32767 / ( image.width * (bytesPerPixel + 1)), rowsLeft)
                nRows = max(nRows, 1)
                val pixels = IntArray( image.width * nRows)
                image.getPixels(pixels, 0,  image.width, 0, startRow,  image.width, nRows)
                scanLines = ByteArray( image.width * nRows * bytesPerPixel + nRows)
                if (filter == FILTER_SUB) {
                    leftBytes = ByteArray(16)
                }
                if (filter == FILTER_UP) {
                    priorRow = ByteArray( image.width * bytesPerPixel)
                }
                scanPos = 0
                startPos = 1
                for (i in 0 until  image.width * nRows) {
                    if (i %  image.width == 0) {
                        scanLines[scanPos++] = filter.toByte()
                        startPos = scanPos
                    }
                    scanLines[scanPos++] = (pixels[i] shr 16 and 0xff).toByte()
                    scanLines[scanPos++] = (pixels[i] shr 8 and 0xff).toByte()
                    scanLines[scanPos++] = (pixels[i] and 0xff).toByte()
                    if (encodeAlpha) {
                        scanLines[scanPos++] = (pixels[i] shr 24 and 0xff).toByte()
                    }
                    if (i %  image.width ==  image.width - 1 && filter != FILTER_NONE) {
                        if (filter == FILTER_SUB) {
                            filterSub(scanLines, startPos,  image.width)
                        }
                        if (filter == FILTER_UP) {
                            filterUp(scanLines, startPos,  image.width)
                        }
                    }
                }
                compBytes.write(scanLines, 0, scanPos)
                startRow += nRows
                rowsLeft -= nRows
            }
            compBytes.close()
            compressedLines = outBytes.toByteArray()
            nCompressed = compressedLines.size
            crc.reset()
            writeInt4(nCompressed + if (currentFrame == 0) 0 else 4)

            if (currentFrame == 0) {
                outputStream.write(Utils.IDAT)
                crc.update(Utils.IDAT)
            } else {
                val fdat = Utils.fdAT+ Utils.uIntToByteArray(currentSeq++)
                outputStream.write(fdat)
                crc.update(fdat)
            }
            outputStream.write(compressedLines)
            crc.update(compressedLines, 0, nCompressed)

            crcValue = crc.value
            writeInt4(crcValue.toInt())
            scrunch.finish()
            scrunch.end()
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error while writing IDAT/fdAT chunks", e)
            return false
        }
    }

    private fun filterSub(pixels: ByteArray, startPos: Int, width: Int) {
        val offset = bytesPerPixel
        val actualStart = startPos + offset
        val nBytes = width * bytesPerPixel
        var leftInsert = offset
        var leftExtract = 0
        var i: Int = actualStart
        while (i < startPos + nBytes) {
            leftBytes!![leftInsert] = pixels[i]
            pixels[i] = ((pixels[i] - leftBytes!![leftExtract]) % 256).toByte()
            leftInsert = (leftInsert + 1) % 0x0f
            leftExtract = (leftExtract + 1) % 0x0f
            i++
        }
    }

    private fun filterUp(pixels: ByteArray, startPos: Int, width: Int) {
        var i = 0
        val nBytes: Int = width * bytesPerPixel
        var currentByte: Byte
        while (i < nBytes) {
            currentByte = pixels[startPos + i]
            pixels[startPos + i] = ((pixels[startPos + i] - priorRow!![i]) % 256).toByte()
            priorRow!![i] = currentByte
            i++
        }
    }
}