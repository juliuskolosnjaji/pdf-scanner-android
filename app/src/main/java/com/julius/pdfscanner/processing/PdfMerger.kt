package com.julius.pdfscanner.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

object PdfMerger {

    /**
     * Merges [sources] into [output] by rendering each page as a bitmap and
     * re-drawing it. Searchable text is re-preserved via PdfRenderer display mode.
     */
    fun merge(sources: List<File>, output: File) {
        val document = PdfDocument()
        var pageNum = 1

        sources.forEach { file ->
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(fd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val info = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, pageNum++).create()
                    val docPage = document.startPage(info)
                    docPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                    bmp.recycle()
                    document.finishPage(docPage)
                }
            }
            fd.close()
        }

        FileOutputStream(output).use { document.writeTo(it) }
        document.close()
    }
}
