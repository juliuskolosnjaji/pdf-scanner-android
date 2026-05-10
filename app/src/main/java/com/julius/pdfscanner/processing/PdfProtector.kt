package com.julius.pdfscanner.processing

import java.io.File

object PdfProtector {

    /**
     * PDF encryption requires a native library (PdfBox / iText) that could not
     * be resolved at build time. Throws UnsupportedOperationException so callers
     * can surface a user-friendly message instead of silently failing.
     */
    fun protect(input: File, output: File, password: String) {
        throw UnsupportedOperationException("PDF password protection is unavailable in this build.")
    }
}
