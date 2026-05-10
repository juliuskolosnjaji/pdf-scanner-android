package com.julius.pdfscanner.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val ocrProcessor = OcrProcessor()
    private val pdfBuilder = PdfBuilder()

    init {
        refreshPdfs()
    }

    fun refreshPdfs() {
        val ctx = getApplication<Application>()
        val dir = File(ctx.filesDir, "pdfs")
        _savedPdfs.value = if (dir.exists())
            dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        else
            emptyList()
    }

    fun setPages(uris: List<Uri>) {
        _pageUris.value = uris
        _state.value = ProcessingState.Idle
    }

    fun clearPages() {
        _pageUris.value = emptyList()
        _state.value = ProcessingState.Idle
    }

    fun deleteFile(file: File) {
        file.delete()
        refreshPdfs()
    }

    fun renameFile(file: File, newName: String): File? {
        val sanitized = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { return null }
        val newFile = File(file.parent, "$sanitized.pdf")
        if (newFile == file) return file
        return if (!newFile.exists() && file.renameTo(newFile)) {
            refreshPdfs()
            // update Done state if currently pointing at this file
            if (_state.value is ProcessingState.Done) _state.value = ProcessingState.Done(newFile)
            newFile
        } else null
    }

    fun processAndSave() {
        val uris = _pageUris.value
        if (uris.isEmpty()) return
        val context = getApplication<Application>()

        viewModelScope.launch {
            try {
                val results = mutableListOf<Pair<Uri, com.google.mlkit.vision.text.Text>>()

                uris.forEachIndexed { i, uri ->
                    _state.value = ProcessingState.InProgress(
                        step = "Running OCR on page ${i + 1} of ${uris.size}…",
                        progress = i.toFloat() / uris.size * 0.85f
                    )
                    results.add(uri to ocrProcessor.recognize(context, uri))
                }

                _state.value = ProcessingState.InProgress("Building PDF…", 0.93f)

                val outDir = File(context.filesDir, "pdfs").also { it.mkdirs() }
                val baseName = generateName()
                var outFile = File(outDir, "$baseName.pdf")
                var counter = 2
                while (outFile.exists()) {
                    outFile = File(outDir, "$baseName ($counter).pdf")
                    counter++
                }

                pdfBuilder.build(context, results, outFile)
                refreshPdfs()
                _state.value = ProcessingState.Done(outFile)

            } catch (e: Exception) {
                _state.value = ProcessingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun generateName(): String {
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return "Scan – ${fmt.format(Date())}"
    }
}
