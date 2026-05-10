package com.julius.pdfscanner.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.julius.pdfscanner.data.DocColorFilter
import java.io.File
import java.io.FileOutputStream

object ImageProcessor {

    fun applyFilter(bitmap: Bitmap, filter: DocColorFilter): Bitmap {
        return when (filter) {
            DocColorFilter.ORIGINAL -> bitmap
            DocColorFilter.AUTO -> applyMatrix(bitmap, autoMatrix())
            DocColorFilter.GRAYSCALE -> applyMatrix(bitmap, grayscaleMatrix())
            DocColorFilter.BLACK_WHITE -> applyBnW(bitmap)
        }
    }

    fun applyWhiteboardFilter(bitmap: Bitmap): Bitmap {
        // Aggressive contrast boost + desaturate slightly to kill shadows
        val matrix = floatArrayOf(
            2.2f, 0f,   0f,   0f, -120f,
            0f,   2.2f, 0f,   0f, -120f,
            0f,   0f,   2.2f, 0f, -120f,
            0f,   0f,   0f,   1f, 0f
        )
        return applyMatrix(bitmap, matrix)
    }

    fun splitBookPages(bitmap: Bitmap): Pair<Bitmap, Bitmap> {
        val mid = bitmap.width / 2
        val left = Bitmap.createBitmap(bitmap, 0, 0, mid, bitmap.height)
        val right = Bitmap.createBitmap(bitmap, mid, 0, bitmap.width - mid, bitmap.height)
        return left to right
    }

    fun compressBitmap(bitmap: Bitmap, quality: Int): Bitmap {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /** Save a bitmap to the app cache and return its URI. */
    fun saveToCacheAndGetUri(context: Context, bitmap: Bitmap, name: String): Uri {
        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return Uri.fromFile(file)
    }

    fun loadBitmap(context: Context, uri: Uri): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

    // ── matrices ──────────────────────────────────────────────────────────────

    fun grayscaleMatrix() = floatArrayOf(
        0.3f, 0.59f, 0.11f, 0f, 0f,
        0.3f, 0.59f, 0.11f, 0f, 0f,
        0.3f, 0.59f, 0.11f, 0f, 0f,
        0f,   0f,    0f,    1f, 0f
    )

    private fun autoMatrix() = floatArrayOf(
        1.6f, 0f,   0f,   0f, -40f,
        0f,   1.6f, 0f,   0f, -40f,
        0f,   0f,   1.6f, 0f, -40f,
        0f,   0f,   0f,   1f, 0f
    )

    private fun applyMatrix(src: Bitmap, matrix: FloatArray): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix)) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun applyBnW(src: Bitmap): Bitmap {
        // First grayscale then threshold
        val gray = applyMatrix(src, grayscaleMatrix())
        val matrix = floatArrayOf(
            5f, 0f, 0f, 0f, -350f,
            0f, 5f, 0f, 0f, -350f,
            0f, 0f, 5f, 0f, -350f,
            0f, 0f, 0f, 1f, 0f
        )
        return applyMatrix(gray, matrix)
    }
}
