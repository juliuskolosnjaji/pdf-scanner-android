package com.julius.pdfscanner.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxExporter {

    fun export(context: Context, pdfFile: File, ocrTexts: List<String>): File {
        val outDir = File(context.filesDir, "exports").also { it.mkdirs() }
        val outFile = File(outDir, "${pdfFile.nameWithoutExtension}.docx")

        val imageBytes = mutableListOf<ByteArray>()
        val emuWidths = mutableListOf<Long>()
        val emuHeights = mutableListOf<Long>()

        val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val pw = page.width * 2
                val ph = page.height * 2
                val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                val baos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                bmp.recycle()
                imageBytes.add(baos.toByteArray())
                // 1 pixel at 144 DPI = 914400 / 144 = 6350 EMU
                emuWidths.add(pw.toLong() * 6350L)
                emuHeights.add(ph.toLong() * 6350L)
            }
        }
        fd.close()

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            zip.putStr("[Content_Types].xml", contentTypes())
            zip.putStr("_rels/.rels", rootRels())
            zip.putStr("word/_rels/document.xml.rels", docRels(imageBytes.size))
            zip.putStr("word/document.xml", document(imageBytes.size, ocrTexts, emuWidths, emuHeights))
            imageBytes.forEachIndexed { i, bytes ->
                zip.putNextEntry(ZipEntry("word/media/image${i + 1}.jpeg"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return outFile
    }

    private fun ZipOutputStream.putStr(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpeg" ContentType="image/jpeg"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun docRels(n: Int) = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) appendLine("""  <Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image$i.jpeg"/>""")
        append("</Relationships>")
    }

    private fun document(
        pageCount: Int,
        ocrTexts: List<String>,
        emuWidths: List<Long>,
        emuHeights: List<Long>
    ): String {
        val maxW = 5_400_000L  // ~5.9 inches — fits A4 content area
        val body = buildString {
            for (i in 0 until pageCount) {
                val rawW = emuWidths.getOrElse(i) { 5_000_000L }
                val rawH = emuHeights.getOrElse(i) { 7_000_000L }
                val scale = if (rawW > maxW) maxW.toDouble() / rawW else 1.0
                val w = (rawW * scale).toLong()
                val h = (rawH * scale).toLong()
                val rId = i + 1
                append("""
  <w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">
    <wp:extent cx="$w" cy="$h"/>
    <wp:docPr id="$rId" name="Image $rId"/>
    <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
      <pic:pic>
        <pic:nvPicPr><pic:cNvPr id="$rId" name="image$rId.jpeg"/><pic:cNvPicPr/></pic:nvPicPr>
        <pic:blipFill><a:blip r:embed="rId$rId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
        <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$w" cy="$h"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
      </pic:pic>
    </a:graphicData></a:graphic>
  </wp:inline></w:drawing></w:r></w:p>""")

                val txt = ocrTexts.getOrElse(i) { "" }.trim()
                if (txt.isNotEmpty()) {
                    val escaped = txt.xmlEscape()
                    append("\n  <w:p><w:r><w:t xml:space=\"preserve\">$escaped</w:t></w:r></w:p>")
                }
                if (i < pageCount - 1) append("\n  <w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document
  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
  xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
  xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
  xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
  xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>$body
    <w:sectPr/>
  </w:body>
</w:document>"""
    }

    private fun String.xmlEscape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
