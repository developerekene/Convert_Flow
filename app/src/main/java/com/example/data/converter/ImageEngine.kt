package com.example.data.converter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.example.domain.model.ConversionOptions
import com.example.domain.model.FileFormat
import com.example.domain.model.PdfPageSize
import com.example.domain.model.ResizeMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageEngine {

    fun inspectDimensions(file: File): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
    }

    fun decodeOrientedBitmap(file: File, maxDimension: Int = 4096): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight)
        while (maxSide / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: error("Could not decode image: ${file.name}")

        val rotation = runCatching {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (rotation == 0f) return raw
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }

    fun resizeBitmap(bitmap: Bitmap, options: ConversionOptions): Bitmap {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val (dstW, dstH) = when (options.resizeMode) {
            ResizeMode.NONE -> return bitmap
            ResizeMode.PERCENTAGE -> {
                val pct = options.scalePercent.coerceIn(5, 200) / 100.0
                max(1, (srcW * pct).roundToInt()) to max(1, (srcH * pct).roundToInt())
            }
            ResizeMode.PRESET -> {
                val targetMax = options.resizePreset.maxDim
                val scale = targetMax.toDouble() / max(srcW, srcH).toDouble()
                if (scale >= 1.0 && options.resizePreset != com.example.domain.model.ImageResizePreset.SQUARE_1024) {
                    srcW to srcH
                } else {
                    max(1, (srcW * scale).roundToInt()) to max(1, (srcH * scale).roundToInt())
                }
            }
            ResizeMode.DIMENSIONS -> {
                val reqW = options.targetWidth?.takeIf { it > 0 }
                val reqH = options.targetHeight?.takeIf { it > 0 }
                when {
                    reqW != null && reqH != null && !options.keepAspectRatio -> reqW to reqH
                    reqW != null -> {
                        val h = max(1, ((reqW.toDouble() / srcW) * srcH).roundToInt())
                        reqW to h
                    }
                    reqH != null -> {
                        val w = max(1, ((reqH.toDouble() / srcH) * srcW).roundToInt())
                        w to reqH
                    }
                    else -> return bitmap
                }
            }
        }
        if (dstW == srcW && dstH == srcH) return bitmap
        return Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
    }

    fun convertOrCompressImage(
        inputFile: File,
        outputFile: File,
        targetFormat: FileFormat,
        options: ConversionOptions
    ): Pair<Int, Int> {
        val original = decodeOrientedBitmap(inputFile)
        var working = resizeBitmap(original, options)
        if (working !== original) original.recycle()

        val finalBitmap = if (targetFormat == FileFormat.JPG || targetFormat == FileFormat.BMP) {
            flattenAlphaToWhite(working).also {
                if (it !== working) working.recycle()
            }
        } else {
            working
        }

        val outW = finalBitmap.width
        val outH = finalBitmap.height

        try {
            if (targetFormat == FileFormat.BMP) {
                writeBmp24(finalBitmap, outputFile)
                return outW to outH
            }

            val targetKb = options.targetSizeKb?.takeIf { it > 0 }
            if (targetKb != null && (targetFormat == FileFormat.JPG || targetFormat == FileFormat.WEBP)) {
                val bytes = compressToTargetKb(finalBitmap, targetFormat, targetKb * 1024, options.quality)
                FileOutputStream(outputFile).use { it.write(bytes) }
            } else {
                val fmt = compressFormatFor(targetFormat, options.quality)
                FileOutputStream(outputFile).use { fos ->
                    finalBitmap.compress(fmt, options.quality.coerceIn(10, 100), fos)
                }
            }
        } finally {
            finalBitmap.recycle()
        }
        return outW to outH
    }

    fun imagesToPdf(
        inputFiles: List<File>,
        outputFile: File,
        pageSize: PdfPageSize,
        quality: Int = 90
    ): Int {
        val pdfDocument = PdfDocument()
        var pagesWritten = 0
        try {
            inputFiles.forEachIndexed { idx, file ->
                val bmp = decodeOrientedBitmap(file, maxDimension = 2600)
                try {
                    val (pageW, pageH) = when (pageSize) {
                        PdfPageSize.ORIGINAL -> bmp.width to bmp.height
                        else -> {
                            if (bmp.width > bmp.height) {
                                pageSize.heightPt to pageSize.widthPt
                            } else {
                                pageSize.widthPt to pageSize.heightPt
                            }
                        }
                    }

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val margin = if (pageSize == PdfPageSize.ORIGINAL) 0f else 24f
                    val availW = (pageW - margin * 2).coerceAtLeast(1f)
                    val availH = (pageH - margin * 2).coerceAtLeast(1f)
                    val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                    val drawW = bmp.width * scale
                    val drawH = bmp.height * scale
                    val left = (pageW - drawW) / 2f
                    val top = (pageH - drawH) / 2f

                    val scaled = if (quality < 85 && pageSize != PdfPageSize.ORIGINAL) {
                        val targetW = max(1, (drawW * 1.5f).roundToInt())
                        val targetH = max(1, (drawH * 1.5f).roundToInt())
                        if (targetW < bmp.width) Bitmap.createScaledBitmap(bmp, targetW, targetH, true) else bmp
                    } else bmp

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(scaled, null, RectF(left, top, left + drawW, top + drawH), paint)
                    if (scaled !== bmp) scaled.recycle()

                    pdfDocument.finishPage(page)
                    pagesWritten++
                } finally {
                    bmp.recycle()
                }
            }
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }
        return pagesWritten
    }

    private fun compressToTargetKb(
        bitmap: Bitmap,
        format: FileFormat,
        maxBytes: Int,
        initialQuality: Int
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        var currentBmp = bitmap
        var ownsCurrent = false

        try {
            for (scaleStep in 0..3) {
                var low = 15
                var high = initialQuality.coerceIn(20, 100)
                var bestBytes: ByteArray? = null

                while (low <= high) {
                    val mid = (low + high) / 2
                    baos.reset()
                    currentBmp.compress(compressFormatFor(format, mid), mid, baos)
                    val candidate = baos.toByteArray()
                    if (candidate.size <= maxBytes) {
                        bestBytes = candidate
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                if (bestBytes != null) return bestBytes

                // Downscale by 75% and retry
                val nextW = max(64, (currentBmp.width * 0.75f).roundToInt())
                val nextH = max(64, (currentBmp.height * 0.75f).roundToInt())
                val nextBmp = Bitmap.createScaledBitmap(currentBmp, nextW, nextH, true)
                if (ownsCurrent) currentBmp.recycle()
                currentBmp = nextBmp
                ownsCurrent = true
            }
            baos.reset()
            currentBmp.compress(compressFormatFor(format, 20), 20, baos)
            return baos.toByteArray()
        } finally {
            if (ownsCurrent) currentBmp.recycle()
        }
    }

    private fun compressFormatFor(format: FileFormat, quality: Int): Bitmap.CompressFormat {
        return when (format) {
            FileFormat.PNG -> Bitmap.CompressFormat.PNG
            FileFormat.JPG -> Bitmap.CompressFormat.JPEG
            FileFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS
                    else Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> Bitmap.CompressFormat.PNG
        }
    }

    private fun flattenAlphaToWhite(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return out
    }

    private fun writeBmp24(bitmap: Bitmap, outputFile: File) {
        val w = bitmap.width
        val h = bitmap.height
        val rowBytes = w * 3
        val padding = (4 - (rowBytes % 4)) % 4
        val imageSize = (rowBytes + padding) * h
        val fileSize = 54 + imageSize

        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte())
            put('M'.code.toByte())
            putInt(fileSize)
            putShort(0)
            putShort(0)
            putInt(54)
            putInt(40)
            putInt(w)
            putInt(h)
            putShort(1)
            putShort(24)
            putInt(0)
            putInt(imageSize)
            putInt(2835)
            putInt(2835)
            putInt(0)
            putInt(0)
        }.array()

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val rowBuffer = ByteArray(rowBytes + padding)

        FileOutputStream(outputFile).use { fos ->
            fos.write(header)
            for (y in h - 1 downTo 0) {
                var pos = 0
                val rowOffset = y * w
                for (x in 0 until w) {
                    val px = pixels[rowOffset + x]
                    rowBuffer[pos++] = (px and 0xFF).toByte()
                    rowBuffer[pos++] = ((px shr 8) and 0xFF).toByte()
                    rowBuffer[pos++] = ((px shr 16) and 0xFF).toByte()
                }
                for (p in 0 until padding) rowBuffer[pos++] = 0
                fos.write(rowBuffer)
            }
        }
    }
}
