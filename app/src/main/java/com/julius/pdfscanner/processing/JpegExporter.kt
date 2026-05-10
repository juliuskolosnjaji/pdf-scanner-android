package com.julius.pdfscanner.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JpegExporter {

    /** Exports every page of [pdf] as a JPEG file into the public Pictures/PdfScanner folder.
     *  Returns the list of exported files. */
    fun export(context: Context, pdf: File, quality: Int = 90): List<File> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "PdfScanner"
        ).also { it.mkdirs() }

        val baseName = pdf.nameWithoutExtension
        val exported = mutableListOf<File>()

        val fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = 2f  // 2× = ~150 DPI; adjust as needed
                val bmp = Bitmap.createBitmap(
                    (page.width * scale).toInt(),
                    (page.height * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val suffix = if (renderer.pageCount > 1) "_p${i + 1}" else ""
                val out = File(dir, "$baseName$suffix.jpg")
                FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                bmp.recycle()
                exported += out
            }
        }
        fd.close()
        return exported
    }
}
