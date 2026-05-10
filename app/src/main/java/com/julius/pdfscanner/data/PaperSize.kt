package com.julius.pdfscanner.data

enum class PaperSize(
    val label: String,
    val widthPt: Int,
    val heightPt: Int
) {
    AUTO("Auto", 0, 0),
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008)
}
