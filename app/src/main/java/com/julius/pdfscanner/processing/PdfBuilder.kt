package com.julius.pdfscanner.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.google.mlkit.vision.text.Text
import com.julius.pdfscanner.data.DocColorFilter
import com.julius.pdfscanner.data.PaperSize
import com.julius.pdfscanner.data.ScanMode
import java.io.File
import java.io.FileOutputStream

class PdfBuilder {

    fun build(
        context: Context,
        pages: List<Pair<Uri, Text>>,
        output: File,
        colorFilter: DocColorFilter = DocColorFilter.ORIGINAL,
        compressionQuality: Int = 100,
        scanMode: ScanMode = ScanMode.DOCUMENT,
        paperSize: PaperSize = PaperSize.AUTO,
        editedTexts: Map<Int, String> = emptyMap()
    ) {
        val document = PdfDocument()
        var pageNum = 1

        pages.forEachIndexed { sourceIndex, (uri, ocrText) ->
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@forEachIndexed

            val bitmapsToWrite: List<Pair<Bitmap, Text?>> = when (scanMode) {
                ScanMode.BOOK -> {
                    val (left, right) = ImageProcessor.splitBookPages(raw)
                    listOf(left to null, right to null)
                }
                else -> listOf(raw to ocrText)
            }

            bitmapsToWrite.forEach { (src, text) ->
                val filtered = ImageProcessor.applyFilter(src, colorFilter)
                val compressed = if (compressionQuality < 100)
                    ImageProcessor.compressBitmap(filtered, compressionQuality)
                else filtered

                val (pageW, pageH) = pageDimensions(paperSize, compressed.width, compressed.height)
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum++).create()
                val page = document.startPage(info)

                // Scale bitmap to fit the page, preserving aspect ratio
                val placed = if (paperSize != PaperSize.AUTO) {
                    val s = minOf(pageW.toFloat() / compressed.width, pageH.toFloat() / compressed.height)
                    Bitmap.createScaledBitmap(
                        compressed,
                        (compressed.width * s).toInt().coerceAtLeast(1),
                        (compressed.height * s).toInt().coerceAtLeast(1),
                        true
                    )
                } else compressed

                page.canvas.drawColor(Color.WHITE)
                val offsetX = (pageW - placed.width) / 2f
                val offsetY = (pageH - placed.height) / 2f
                page.canvas.drawBitmap(placed, offsetX, offsetY, null)
                if (placed !== compressed) placed.recycle()
                if (compressed !== filtered) compressed.recycle()

                // Text overlay: prefer user-edited text, fall back to ML Kit bounding boxes
                val editedText = if (scanMode != ScanMode.BOOK) editedTexts[sourceIndex] else null
                when {
                    editedText != null -> drawPlainOverlay(page.canvas, editedText)
                    text != null -> drawBoundingBoxOverlay(page.canvas, text, offsetX, offsetY,
                        if (paperSize != PaperSize.AUTO) placed.width.toFloat() / (compressed.width.takeIf { it > 0 } ?: 1) else 1f)
                }

                document.finishPage(page)
            }
        }

        FileOutputStream(output).use { document.writeTo(it) }
        document.close()
    }

    private fun pageDimensions(paperSize: PaperSize, srcW: Int, srcH: Int): Pair<Int, Int> =
        if (paperSize == PaperSize.AUTO) srcW to srcH
        else paperSize.widthPt to paperSize.heightPt

    private fun drawPlainOverlay(canvas: android.graphics.Canvas, text: String) {
        val paint = Paint().apply { color = Color.TRANSPARENT; alpha = 0; textSize = 10f; isAntiAlias = true }
        text.lines().forEachIndexed { i, line ->
            canvas.drawText(line, 4f, 12f + i * 12f, paint)
        }
    }

    private fun drawBoundingBoxOverlay(canvas: android.graphics.Canvas, text: Text, offsetX: Float, offsetY: Float, scale: Float = 1f) {
        val paint = Paint().apply { color = Color.TRANSPARENT; alpha = 0; isAntiAlias = true }
        text.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.boundingBox?.let { box ->
                    paint.textSize = (box.height() * scale).coerceAtLeast(8f)
                    canvas.drawText(line.text, box.left * scale + offsetX, box.bottom * scale + offsetY, paint)
                }
            }
        }
    }
}
