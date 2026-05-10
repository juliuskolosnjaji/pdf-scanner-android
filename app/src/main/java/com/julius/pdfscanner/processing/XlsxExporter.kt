package com.julius.pdfscanner.processing

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {

    fun export(context: Context, pdfFile: File, ocrTexts: List<String>): File {
        val outDir = File(context.filesDir, "exports").also { it.mkdirs() }
        val outFile = File(outDir, "${pdfFile.nameWithoutExtension}.xlsx")

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            zip.putStr("[Content_Types].xml", CONTENT_TYPES)
            zip.putStr("_rels/.rels", ROOT_RELS)
            zip.putStr("xl/workbook.xml", WORKBOOK)
            zip.putStr("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.putStr("xl/worksheets/sheet1.xml", buildSheet(ocrTexts))
        }
        return outFile
    }

    private fun ZipOutputStream.putStr(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun buildSheet(ocrTexts: List<String>): String {
        var row = 1
        val rows = buildString {
            ocrTexts.forEachIndexed { pageIdx, text ->
                appendRow(row++, "--- Page ${pageIdx + 1} ---")
                text.lines().filter { it.isNotBlank() }.forEach { line ->
                    appendRow(row++, line.trim())
                }
                row++ // blank gap between pages
            }
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>$rows
  </sheetData>
</worksheet>"""
    }

    private fun StringBuilder.appendRow(rowNum: Int, value: String) {
        val escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        append("\n    <row r=\"$rowNum\"><c r=\"A$rowNum\" t=\"inlineStr\"><is><t>$escaped</t></is></c></row>")
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="OCR Text" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
}
