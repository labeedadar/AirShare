@file:OptIn(ExperimentalMaterial3Api::class)

package com.airshare.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airshare.app.server.ServerState
import com.airshare.app.util.FileDocumentInfo
import com.airshare.app.util.FileUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSettings: () -> Unit,
    onShareQr: () -> Unit,
    onFolderPicked: () -> Unit
) {
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val serverState by viewModel.serverState.collectAsState()
    val selectedUri by viewModel.selectedUri.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<List<FileDocumentInfo>?>(null) }

    LaunchedEffect(Unit) { viewModel.bindService() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AirShare") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = onShareQr) {
                        Icon(Icons.Default.Share, "Share")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (serverState is ServerState.Running) {
                    FloatingActionButton(
                        onClick = { viewModel.stopServer() },
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Icon(Icons.Default.Stop, "Stop Server")
                    }
                    Spacer(Modifier.height(12.dp))
                }
                FloatingActionButton(
                    onClick = {
                        if (serverState is ServerState.Running) viewModel.stopServer()
                        else viewModel.startServer()
                    }
                ) {
                    Icon(
                        if (serverState is ServerState.Running) Icons.Default.Stop
                        else Icons.Default.CloudUpload,
                        contentDescription = if (serverState is ServerState.Running) "Stop" else "Start"
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            ServerStatusBar(serverState)
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedUri != null) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFolderPicked() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderOpen, null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedUri?.lastPathSegment ?: "Select Folder",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (selectedUri != null) {
                            Text(
                                "${files.size} files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                files.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedUri == null) "Select a folder to share files"
                            else "Folder is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${files.size} files",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        IconButton(onClick = { viewModel.loadFiles(selectedUri!!) }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(files, key = { it.documentId }) { file ->
                            FileItem(file = file, onDelete = { showDeleteDialog = listOf(file) })
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete ${toDelete.size} file(s)?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFiles(toDelete)
                    showDeleteDialog = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ServerStatusBar(state: ServerState) {
    val (color, text) = when (state) {
        is ServerState.Running -> MaterialTheme.colorScheme.primary to "Running: ${state.ip}:${state.port}"
        is ServerState.Starting -> MaterialTheme.colorScheme.tertiary to "Starting..."
        is ServerState.Error -> MaterialTheme.colorScheme.error to "Error: ${state.message}"
        is ServerState.Idle -> Color.Gray to "Server stopped"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
        }
    }
}

@Composable
private fun FileItem(file: FileDocumentInfo, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${FileUtils.formatSize(file.size)} - ${formatDate(file.lastModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete, "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatDate(millis: Long): String {
    if (millis <= 0) return ""
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(millis))
}

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var passwordLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        password = com.airshare.app.util.Prefs.getPassword(context) ?: ""
        passwordLoaded = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.FolderOpen, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (passwordLoaded) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text("Password Protection", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (leave empty to disable)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    scope.launch {
                        com.airshare.app.util.Prefs.setPassword(context, password)
                    }
                    onBack()
                }) {
                    Text("Save")
                }
            }
        }
    }
}
