package com.julius.pdfscanner.processing

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.google.mlkit.vision.text.Text
import java.io.File
import java.io.FileOutputStream

class PdfBuilder {

    fun build(context: Context, pages: List<Pair<Uri, Text>>, output: File) {
        val document = PdfDocument()

        pages.forEachIndexed { index, (uri, ocrText) ->
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return@forEachIndexed

            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap.recycle()

            // Invisible text overlay — makes the PDF searchable
            val textPaint = Paint().apply {
                color = Color.TRANSPARENT
                alpha = 0
                isAntiAlias = true
            }
            ocrText.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    line.boundingBox?.let { box ->
                        textPaint.textSize = box.height().toFloat().coerceAtLeast(8f)
                        canvas.drawText(line.text, box.left.toFloat(), box.bottom.toFloat(), textPaint)
                    }
                }
            }

            document.finishPage(page)
        }

        FileOutputStream(output).use { document.writeTo(it) }
        document.close()
    }
}
