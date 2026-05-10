package com.julius.pdfscanner.ui.preview

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.julius.pdfscanner.data.DocColorFilter
import com.julius.pdfscanner.data.PaperSize
import com.julius.pdfscanner.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: ScanViewModel,
    onProcess: () -> Unit,
    onRetake: () -> Unit
) {
    val pageUris by viewModel.pageUris.collectAsState()
    val colorFilter by viewModel.colorFilter.collectAsState()
    val paperSize by viewModel.paperSize.collectAsState()
    val pagerState = rememberPagerState { pageUris.size }
    var showReorderSheet by remember { mutableStateOf(false) }
    var showTextEditSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review · ${pageUris.size} page${if (pageUris.size != 1) "s" else ""}") },
                actions = {
                    IconButton(onClick = { showTextEditSheet = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit text")
                    }
                    IconButton(onClick = { showReorderSheet = true }) {
                        Icon(Icons.Default.Reorder, contentDescription = "Reorder pages")
                    }
                    IconButton(onClick = onRetake) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retake")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                OptionBar(
                    colorFilter = colorFilter, onFilterSelect = { viewModel.setColorFilter(it) },
                    paperSize = paperSize, onSizeSelect = { viewModel.setPaperSize(it) }
                )
                Surface(shadowElevation = 4.dp) {
                    Button(
                        onClick = onProcess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        enabled = pageUris.isNotEmpty()
                    ) {
                        Text("Convert to PDF")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (pageUris.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pages", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    UriImage(uri = pageUris[page], colorFilter = colorFilter, modifier = Modifier.fillMaxSize())
                }

                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(pageUris.size) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == pagerState.currentPage) 10.dp else 6.dp)
                                .background(
                                    color = if (i == pagerState.currentPage)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }

    if (showReorderSheet) {
        ReorderSheet(uris = pageUris, onReorder = { viewModel.reorderPages(it) }, onDismiss = { showReorderSheet = false })
    }

    if (showTextEditSheet) {
        TextEditSheet(viewModel = viewModel, onDismiss = { showTextEditSheet = false })
    }
}

// ── option bar (color filter + paper size, single scrollable row) ──────────────

@Composable
private fun OptionBar(
    colorFilter: DocColorFilter, onFilterSelect: (DocColorFilter) -> Unit,
    paperSize: PaperSize, onSizeSelect: (PaperSize) -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocColorFilter.entries.forEach { filter ->
                FilterChip(
                    selected = colorFilter == filter,
                    onClick = { onFilterSelect(filter) },
                    label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
            VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 2.dp))
            Text("Size:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            PaperSize.entries.forEach { size ->
                FilterChip(
                    selected = paperSize == size,
                    onClick = { onSizeSelect(size) },
                    label = { Text(size.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}

// ── text edit sheet ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditSheet(viewModel: ScanViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageUris by viewModel.pageUris.collectAsState()
    val ocrCache by viewModel.ocrCache.collectAsState()
    val isOcrLoading by viewModel.isOcrLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.triggerOcrPreview() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxHeight(0.9f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit OCR Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            HorizontalDivider()

            when {
                isOcrLoading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Running OCR…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                    }
                }
                ocrCache == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No text detected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
                else -> Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    pageUris.indices.forEach { i ->
                        var text by rememberSaveable(key = "ocr_page_$i") {
                            mutableStateOf(viewModel.getPageOcrText(i))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Page ${i + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it; viewModel.updateEditedText(i, it) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 8,
                                placeholder = { Text("No text on this page") }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ── reorder sheet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReorderSheet(uris: List<Uri>, onReorder: (List<Uri>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var items by remember { mutableStateOf(uris) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    ModalBottomSheet(onDismissRequest = { onReorder(items); onDismiss() }, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reorder Pages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { onReorder(items); onDismiss() }) { Text("Done") }
            }
            HorizontalDivider()
            LazyColumn(state = lazyListState, modifier = Modifier.heightIn(max = 480.dp)) {
                items(items, key = { it.toString() }) { uri ->
                    ReorderableItem(reorderState, key = uri.toString()) { isDragging ->
                        val elevation = if (isDragging) 8.dp else 0.dp
                        Surface(shadowElevation = elevation, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val context = LocalContext.current
                                var bmp by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
                                LaunchedEffect(uri) {
                                    withContext(Dispatchers.IO) {
                                        bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                                    }
                                }
                                Box(
                                    modifier = Modifier.size(48.dp, 60.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                }
                                Text("Page ${items.indexOf(uri) + 1}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Drag",
                                    modifier = Modifier.draggableHandle(),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── image composable ──────────────────────────────────────────────────────────

@Composable
fun UriImage(uri: Uri, colorFilter: DocColorFilter = DocColorFilter.ORIGINAL, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }

    val composeFilter: androidx.compose.ui.graphics.ColorFilter? = when (colorFilter) {
        DocColorFilter.GRAYSCALE ->
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        DocColorFilter.AUTO ->
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                1.6f, 0f, 0f, 0f, -40f,
                0f, 1.6f, 0f, 0f, -40f,
                0f, 0f, 1.6f, 0f, -40f,
                0f, 0f, 0f,   1f, 0f
            )))
        DocColorFilter.BLACK_WHITE ->
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                5f, 0f, 0f, 0f, -350f,
                0f, 5f, 0f, 0f, -350f,
                0f, 0f, 5f, 0f, -350f,
                0f, 0f, 0f,  1f, 0f
            )))
        else -> null
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            colorFilter = composeFilter
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
