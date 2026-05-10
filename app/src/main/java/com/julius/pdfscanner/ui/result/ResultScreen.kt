package com.julius.pdfscanner.ui.result

import androidx.compose.animation.*
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
import com.julius.pdfscanner.ui.home.RenameDialog
import com.julius.pdfscanner.ui.home.openPdf
import com.julius.pdfscanner.ui.home.sharePdf
import com.julius.pdfscanner.viewmodel.ProcessingState
import com.julius.pdfscanner.viewmodel.ScanViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(viewModel: ScanViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showRename by remember { mutableStateOf(false) }
    var currentFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        if (viewModel.state.value == ProcessingState.Idle) viewModel.processAndSave()
    }

    // Track file as it becomes available
    LaunchedEffect(state) {
        if (state is ProcessingState.Done) currentFile = (state as ProcessingState.Done).file
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state is ProcessingState.Done) "Saved" else "Processing") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "result"
            ) { s ->
                when (s) {
                    is ProcessingState.Idle,
                    is ProcessingState.InProgress -> {
                        val step = (s as? ProcessingState.InProgress)?.step ?: "Starting…"
                        val progress = (s as? ProcessingState.InProgress)?.progress ?: 0f
                        ProgressView(step = step, progress = progress)
                    }

                    is ProcessingState.Done -> {
                        DoneView(
                            file = s.file,
                            onOpen = { openPdf(context, s.file) },
                            onShare = { sharePdf(context, s.file) },
                            onRename = { showRename = true },
                            onDone = onDone
                        )
                    }

                    is ProcessingState.Error -> {
                        ErrorView(message = s.message, onDone = onDone)
                    }
                }
            }
        }
    }

    if (showRename) {
        currentFile?.let { file ->
            RenameDialog(
                currentName = file.nameWithoutExtension,
                onConfirm = { newName ->
                    viewModel.renameFile(file, newName)
                    showRename = false
                },
                onDismiss = { showRename = false }
            )
        }
    }
}

@Composable
private fun ProgressView(step: String, progress: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(40.dp)
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(72.dp),
            strokeWidth = 6.dp
        )
        Text(step, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DoneView(
    file: File,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(40.dp)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text("Saved!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            file.nameWithoutExtension,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        // Primary actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
            Button(onClick = onOpen) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open")
            }
        }

        // Secondary actions
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Rename")
            }
            TextButton(onClick = onDone) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onDone: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(40.dp)
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text("Something went wrong", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onDone) { Text("Go Back") }
    }
}
