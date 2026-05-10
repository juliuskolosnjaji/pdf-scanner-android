package com.julius.pdfscanner.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.julius.pdfscanner.processing.PdfMerger
import com.julius.pdfscanner.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: ScanViewModel,
    onScanModeSelect: () -> Unit,
    onScanComplete: () -> Unit,
    onSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val savedPdfs by viewModel.savedPdfs.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var selectedForMerge by remember { mutableStateOf<Set<File>>(emptySet()) }
    val isSelecting = selectedForMerge.isNotEmpty()
    var isMerging by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.setPages(uris)
            onScanComplete()
        }
    }

    Scaffold(
        topBar = {
            if (isSelecting) {
                TopAppBar(
                    title = { Text("${selectedForMerge.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedForMerge = emptySet() }) {
                            Icon(Icons.Default.Close, "Cancel selection")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    isMerging = true
                                    withContext(Dispatchers.IO) {
                                        val outDir = File(context.filesDir, "pdfs").also { it.mkdirs() }
                                        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                        val out = File(outDir, "Merged – ${fmt.format(Date())}.pdf")
                                        PdfMerger.merge(selectedForMerge.sortedBy { it.name }, out)
                                    }
                                    viewModel.refreshPdfs()
                                    selectedForMerge = emptySet()
                                    isMerging = false
                                }
                            },
                            enabled = selectedForMerge.size >= 2 && !isMerging
                        ) {
                            Icon(Icons.Default.Merge, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Merge")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("PDF Scanner", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Import from gallery")
                        }
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(!isSelecting) {
                ExtendedFloatingActionButton(
                    onClick = onScanModeSelect,
                    icon = { Icon(Icons.Default.DocumentScanner, null) },
                    text = { Text("Scan") }
                )
            }
        }
    ) { padding ->
        if (savedPdfs.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                onScan = onScanModeSelect,
                onImport = { galleryLauncher.launch("image/*") }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedPdfs, key = { it.absolutePath }) { file ->
                    val isSelected = file in selectedForMerge
                    PdfCard(
                        file = file,
                        isSelected = isSelected,
                        isSelecting = isSelecting,
                        onClick = {
                            if (isSelecting) {
                                selectedForMerge = if (isSelected) selectedForMerge - file else selectedForMerge + file
                            } else {
                                selectedFile = file
                            }
                        },
                        onLongClick = { selectedForMerge = selectedForMerge + file },
                        onOpen = { openPdf(context, file) },
                        onShare = { sharePdf(context, file) }
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }

        if (isMerging) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.padding(32.dp)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Merging PDFs…")
                    }
                }
            }
        }
    }

    selectedFile?.let { file ->
        PdfActionSheet(
            file = file,
            onDismiss = { selectedFile = null },
            onOpen = { openPdf(context, file); selectedFile = null },
            onShare = { sharePdf(context, file); selectedFile = null },
            onRename = { viewModel.renameFile(file, it); selectedFile = null },
            onDelete = { viewModel.deleteFile(file); selectedFile = null }
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onScan: () -> Unit, onImport: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            Text("No scans yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Scan or import a document", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onImport) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
                Button(onClick = onScan) {
                    Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan")
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PdfCard(
    file: File,
    isSelected: Boolean,
    isSelecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val thumbnail = rememberPdfThumbnail(file)
    val info = rememberPdfInfo(file)
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp, 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isSelected -> Icon(Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    thumbnail != null -> Image(thumbnail.asImageBitmap(), null,
                        Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else -> Icon(Icons.Default.PictureAsPdf, null,
                        tint = MaterialTheme.colorScheme.primary.copy(0.45f), modifier = Modifier.size(28.dp))
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    file.nameWithoutExtension,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (info.pageCount > 0) append("${info.pageCount}p  ·  ")
                        append(info.sizeStr)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                )
                Text(
                    fmt.format(Date(file.lastModified())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.38f)
                )
            }

            if (!isSelecting) {
                Column {
                    IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfActionSheet(
    file: File,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val info = rememberPdfInfo(file)
    val fmt = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            // Header
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PictureAsPdf, null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(file.nameWithoutExtension, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            if (info.pageCount > 0) append("${info.pageCount} pages  ·  ")
                            append(info.sizeStr)
                            append("  ·  ")
                            append(fmt.format(Date(file.lastModified())))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
                    )
                }
            }
            HorizontalDivider()
            SheetAction(Icons.AutoMirrored.Filled.OpenInNew, "Open", onClick = onOpen)
            SheetAction(Icons.Default.Share, "Share", onClick = onShare)
            SheetAction(Icons.Default.Edit, "Rename", onClick = { showRename = true })
            SheetAction(Icons.Default.Delete, "Delete",
                onClick = { showDelete = true },
                tint = MaterialTheme.colorScheme.error)
        }
    }

    if (showRename) RenameDialog(
        file.nameWithoutExtension,
        onConfirm = { onRename(it); showRename = false },
        onDismiss = { showRename = false }
    )
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete scan?") },
        text = { Text("\"${file.nameWithoutExtension}\" will be permanently deleted.") },
        confirmButton = {
            TextButton(
                onClick = { onDelete(); showDelete = false },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
    )
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
        }
    }
}

@Composable
fun RenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, null) },
        title = { Text("Rename") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Document name") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── PDF info helpers ──────────────────────────────────────────────────────────

data class PdfFileInfo(val pageCount: Int, val sizeStr: String)

@Composable
fun rememberPdfInfo(file: File): PdfFileInfo {
    var info by remember(file) { mutableStateOf(PdfFileInfo(0, formatSize(file.length()))) }
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            runCatching {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(fd).use { info = PdfFileInfo(it.pageCount, formatSize(file.length())) }
                fd.close()
            }
        }
    }
    return info
}

@Composable
fun rememberPdfThumbnail(file: File): Bitmap? {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            runCatching {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(fd).use { renderer ->
                    val page = renderer.openPage(0)
                    val scale = 180f / page.width
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    android.graphics.Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }.also { fd.close() }
            }.onSuccess { bitmap = it }
        }
    }
    return bitmap
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

fun openPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}

fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share PDF"))
}
