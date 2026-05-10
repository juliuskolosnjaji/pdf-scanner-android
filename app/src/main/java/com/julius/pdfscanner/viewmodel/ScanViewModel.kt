package com.julius.pdfscanner.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.julius.pdfscanner.data.DocColorFilter
import com.julius.pdfscanner.data.ScanMode
import com.julius.pdfscanner.processing.ContactExtractor
import com.julius.pdfscanner.processing.ContactInfo
import com.julius.pdfscanner.processing.OcrProcessor
import com.julius.pdfscanner.processing.PdfBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ProcessingState {
    object Idle : ProcessingState()
    data class InProgress(val step: String, val progress: Float) : ProcessingState()
    data class Done(val file: File) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val _pageUris = MutableStateFlow<List<Uri>>(emptyList())
    val pageUris: StateFlow<List<Uri>> = _pageUris.asStateFlow()

    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    private val _savedPdfs = MutableStateFlow<List<File>>(emptyList())
    val savedPdfs: StateFlow<List<File>> = _savedPdfs.asStateFlow()

    private val _scanMode = MutableStateFlow(ScanMode.DOCUMENT)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _colorFilter = MutableStateFlow(DocColorFilter.ORIGINAL)
    val colorFilter: StateFlow<DocColorFilter> = _colorFilter.asStateFlow()

    private val _compressionQuality = MutableStateFlow(100)
    val compressionQuality: StateFlow<Int> = _compressionQuality.asStateFlow()

    var extractedContact: ContactInfo? = null
        private set

    private val ocrProcessor = OcrProcessor()
    private val pdfBuilder = PdfBuilder()

    init { refreshPdfs() }

    // ── scan configuration ────────────────────────────────────────────────────

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
        // Whiteboard mode auto-selects B&W filter
        if (mode == ScanMode.WHITEBOARD) _colorFilter.value = DocColorFilter.AUTO
        else _colorFilter.value = DocColorFilter.ORIGINAL
    }

    fun setColorFilter(filter: DocColorFilter) { _colorFilter.value = filter }
    fun setCompressionQuality(q: Int) { _compressionQuality.value = q }
    fun isBusinessCardMode() = _scanMode.value == ScanMode.BUSINESS_CARD

    // ── pages ─────────────────────────────────────────────────────────────────

    fun setPages(uris: List<Uri>) {
        _pageUris.value = uris
        _state.value = ProcessingState.Idle
        extractedContact = null
    }

    fun reorderPages(uris: List<Uri>) { _pageUris.value = uris }

    fun clearPages() {
        _pageUris.value = emptyList()
        _state.value = ProcessingState.Idle
        extractedContact = null
    }

    // ── saved PDFs ────────────────────────────────────────────────────────────

    fun refreshPdfs() {
        val ctx = getApplication<Application>()
        val dir = File(ctx.filesDir, "pdfs")
        _savedPdfs.value = if (dir.exists())
            dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        else emptyList()
    }

    fun deleteFile(file: File) { file.delete(); refreshPdfs() }

    fun renameFile(file: File, newName: String): File? {
        val sanitized = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { return null }
        val newFile = File(file.parent, "$sanitized.pdf")
        if (newFile == file) return file
        return if (!newFile.exists() && file.renameTo(newFile)) {
            refreshPdfs()
            if (_state.value is ProcessingState.Done) _state.value = ProcessingState.Done(newFile)
            newFile
        } else null
    }

    // ── processing ────────────────────────────────────────────────────────────

    fun processAndSave() {
        val uris = _pageUris.value
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        val mode = _scanMode.value
        val filter = _colorFilter.value

        viewModelScope.launch {
            try {
                val results = mutableListOf<Pair<Uri, com.google.mlkit.vision.text.Text>>()
                val allOcrText = StringBuilder()

                uris.forEachIndexed { i, uri ->
                    _state.value = ProcessingState.InProgress(
                        "Running OCR on page ${i + 1} of ${uris.size}…",
                        i.toFloat() / uris.size * 0.85f
                    )
                    val text = ocrProcessor.recognize(context, uri)
                    results.add(uri to text)
                    allOcrText.append(text.text).append("\n")
                }

                // Business card: extract contact before building PDF
                if (mode == ScanMode.BUSINESS_CARD) {
                    extractedContact = ContactExtractor.extract(allOcrText.toString())
                }

                _state.value = ProcessingState.InProgress("Building PDF…", 0.93f)

                val outDir = File(context.filesDir, "pdfs").also { it.mkdirs() }
                val baseName = generateName()
                var outFile = File(outDir, "$baseName.pdf")
                var counter = 2
                while (outFile.exists()) { outFile = File(outDir, "$baseName ($counter).pdf"); counter++ }

                pdfBuilder.build(
                    context = context,
                    pages = results,
                    output = outFile,
                    colorFilter = filter,
                    compressionQuality = _compressionQuality.value,
                    scanMode = mode
                )
                refreshPdfs()
                _state.value = ProcessingState.Done(outFile)

            } catch (e: Exception) {
                _state.value = ProcessingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun generateName(): String {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val prefix = when (_scanMode.value) {
            ScanMode.DOCUMENT -> "Scan"
            ScanMode.BOOK -> "Book"
            ScanMode.ID_CARD -> "ID"
            ScanMode.BUSINESS_CARD -> "Card"
            ScanMode.WHITEBOARD -> "Board"
        }
        return "$prefix – ${fmt.format(Date())}"
    }
}
