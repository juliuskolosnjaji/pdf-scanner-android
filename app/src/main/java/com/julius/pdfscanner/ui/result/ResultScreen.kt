package com.julius.pdfscanner.ui.result

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.julius.pdfscanner.processing.ContactExtractor
import com.julius.pdfscanner.processing.JpegExporter
import com.julius.pdfscanner.processing.PdfProtector
import com.julius.pdfscanner.ui.home.RenameDialog
import com.julius.pdfscanner.ui.home.sharePdf
import com.julius.pdfscanner.ui.home.openPdf
import com.julius.pdfscanner.viewmodel.ProcessingState
import com.julius.pdfscanner.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(viewModel: ScanViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showRename by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (viewModel.state.value == ProcessingState.Idle) viewModel.processAndSave()
    }
    LaunchedEffect(state) {
        if (state is ProcessingState.Done) currentFile = (state as ProcessingState.Done).file
    }

    // After processing, check if business card mode → show contact dialog
    LaunchedEffect(state) {
        if (state is ProcessingState.Done && viewModel.isBusinessCardMode()) {
            showContactDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (state is ProcessingState.Done) "Saved" else "Processing") })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "result") { s ->
                when (s) {
                    is ProcessingState.Idle,
                    is ProcessingState.InProgress -> ProgressView(
                        step = (s as? ProcessingState.InProgress)?.step ?: "Starting…",
                        progress = (s as? ProcessingState.InProgress)?.progress ?: 0f
                    )
                    is ProcessingState.Done -> DoneView(
                        file = s.file,
                        statusMsg = statusMsg,
                        onOpen = { openPdf(context, s.file) },
                        onShare = { sharePdf(context, s.file) },
                        onRename = { showRename = true },
                        onExportJpeg = {
                            scope.launch {
                                statusMsg = "Exporting…"
                                withContext(Dispatchers.IO) { JpegExporter.export(context, s.file) }
                                statusMsg = "Exported to Pictures/PdfScanner"
                            }
                        },
                        onPasswordProtect = { showPasswordDialog = true },
                        onCompress = { showCompressDialog = true },
                        onDone = onDone
                    )
                    is ProcessingState.Error -> ErrorView(message = s.message, onDone = onDone)
                }
            }
        }
    }

    if (showRename) currentFile?.let { file ->
        RenameDialog(file.nameWithoutExtension,
            onConfirm = { viewModel.renameFile(file, it); showRename = false },
            onDismiss = { showRename = false })
    }

    if (showPasswordDialog) currentFile?.let { file ->
        PasswordDialog(
            onConfirm = { password ->
                scope.launch {
                    statusMsg = "Encrypting…"
                    val result = withContext(Dispatchers.IO) {
                        val out = File(file.parent, "${file.nameWithoutExtension} (protected).pdf")
                        runCatching { PdfProtector.protect(file, out, password); out }
                    }
                    if (result.isSuccess) {
                        viewModel.refreshPdfs()
                        statusMsg = "Protected PDF saved"
                    } else {
                        statusMsg = if (result.exceptionOrNull() is UnsupportedOperationException)
                            "Password protection not available in this build"
                        else "Encryption failed"
                    }
                }
                showPasswordDialog = false
            },
            onDismiss = { showPasswordDialog = false }
        )
    }

    if (showCompressDialog) currentFile?.let { file ->
        CompressDialog(
            onConfirm = { quality ->
                scope.launch {
                    statusMsg = "Compressing…"
                    withContext(Dispatchers.IO) {
                        // Re-build PDF at lower quality by re-rendering pages
                        val out = File(file.parent, "${file.nameWithoutExtension} (compressed).pdf")
                        android.graphics.pdf.PdfRenderer(
                            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        ).use { renderer ->
                            val document = android.graphics.pdf.PdfDocument()
                            for (i in 0 until renderer.pageCount) {
                                val page = renderer.openPage(i)
                                val bmp = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                                android.graphics.Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                val compressed = com.julius.pdfscanner.processing.ImageProcessor.compressBitmap(bmp, quality)
                                val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(compressed.width, compressed.height, i + 1).create()
                                val docPage = document.startPage(info)
                                docPage.canvas.drawBitmap(compressed, 0f, 0f, null)
                                document.finishPage(docPage)
                                bmp.recycle(); compressed.recycle()
                            }
                            java.io.FileOutputStream(out).use { document.writeTo(it) }
                            document.close()
                        }
                    }
                    viewModel.refreshPdfs()
                    statusMsg = "Compressed PDF saved"
                }
                showCompressDialog = false
            },
            onDismiss = { showCompressDialog = false }
        )
    }

    if (showContactDialog) {
        val contactInfo = viewModel.extractedContact
        if (contactInfo != null && !contactInfo.isEmpty) {
            ContactSaveDialog(
                contactInfo = contactInfo,
                onSave = {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                        contactInfo.name?.let { putExtra(android.provider.ContactsContract.Intents.Insert.NAME, it) }
                        contactInfo.phone?.let { putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, it) }
                        contactInfo.email?.let { putExtra(android.provider.ContactsContract.Intents.Insert.EMAIL, it) }
                        contactInfo.company?.let { putExtra(android.provider.ContactsContract.Intents.Insert.COMPANY, it) }
                    }
                    context.startActivity(intent)
                    showContactDialog = false
                },
                onDismiss = { showContactDialog = false }
            )
        }
    }
}

// ── sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun ProgressView(step: String, progress: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(40.dp)) {
        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(72.dp), strokeWidth = 6.dp)
        Text(step, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DoneView(
    file: File,
    statusMsg: String?,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onExportJpeg: () -> Unit,
    onPasswordProtect: () -> Unit,
    onCompress: () -> Unit,
    onDone: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Saved!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f), textAlign = TextAlign.Center)

        statusMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

        Spacer(Modifier.height(4.dp))

        // Primary
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onShare) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share") }
            Button(onClick = onOpen) { Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open") }
        }

        // Secondary actions
        Divider(modifier = Modifier.fillMaxWidth())
        Text("More options", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportJpeg) { Icon(Icons.Default.Image, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("JPEG") }
            OutlinedButton(onClick = onPasswordProtect) { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Protect") }
            OutlinedButton(onClick = onCompress) { Icon(Icons.Default.Compress, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Compress") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRename) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Rename") }
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun ErrorView(message: String, onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(40.dp)) {
        Icon(Icons.Default.Error, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.error)
        Text("Something went wrong", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onDone) { Text("Go Back") }
    }
}

@Composable
private fun PasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text("Password Protect") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text("Confirm password") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    isError = confirm.isNotEmpty() && confirm != password,
                    supportingText = { if (confirm.isNotEmpty() && confirm != password) Text("Passwords don't match") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotBlank() && password == confirm) { Text("Protect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CompressDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var quality by remember { mutableFloatStateOf(60f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Compress, null) },
        title = { Text("Compress PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Image quality: ${quality.toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(value = quality, onValueChange = { quality = it }, valueRange = 20f..90f, steps = 6)
                Text(when {
                    quality < 40 -> "High compression — smaller file, lower quality"
                    quality < 70 -> "Balanced compression"
                    else -> "Light compression — larger file, better quality"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(quality.toInt()) }) { Text("Compress") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ContactSaveDialog(
    contactInfo: com.julius.pdfscanner.processing.ContactInfo,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ContactPage, null) },
        title = { Text("Save Contact?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Extracted from business card:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                Spacer(Modifier.height(4.dp))
                contactInfo.name?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                contactInfo.company?.let { Text(it) }
                contactInfo.phone?.let { Text(it) }
                contactInfo.email?.let { Text(it) }
                contactInfo.website?.let { Text(it) }
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Save to Contacts") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip") } }
    )
}
