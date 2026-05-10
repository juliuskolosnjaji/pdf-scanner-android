package com.julius.pdfscanner.processing

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.google.mlkit.vision.text.Text
import com.julius.pdfscanner.data.DocColorFilter
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
        scanMode: ScanMode = ScanMode.DOCUMENT
    ) {
        val document = PdfDocument()
        var pageNum = 1

        pages.forEach { (uri, ocrText) ->
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@forEach

            val bitmapsToWrite = when (scanMode) {
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

                val info = PdfDocument.PageInfo.Builder(compressed.width, compressed.height, pageNum++).create()
                val page = document.startPage(info)
                page.canvas.drawBitmap(compressed, 0f, 0f, null)
                compressed.recycle()

                // Invisible text overlay (OCR text → searchable PDF)
                if (text != null) {
                    val paint = Paint().apply {
                        color = Color.TRANSPARENT
                        alpha = 0
                        isAntiAlias = true
                    }
                    text.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            line.boundingBox?.let { box ->
                                paint.textSize = box.height().toFloat().coerceAtLeast(8f)
                                page.canvas.drawText(line.text, box.left.toFloat(), box.bottom.toFloat(), paint)
                            }
                        }
                    }
                }

                document.finishPage(page)
            }
        }

        FileOutputStream(output).use { document.writeTo(it) }
        document.close()
    }
}
