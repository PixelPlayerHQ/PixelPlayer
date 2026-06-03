package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.database.DownloadEntity
import com.theveloper.pixelplay.data.database.DownloadStatus
import com.theveloper.pixelplay.presentation.viewmodel.DownloadsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsManagerScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No downloads yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.songId }) { download ->
                    DownloadItem(
                        download = download,
                        onCancel = { viewModel.cancelDownload(download.songId) },
                        onRetry = { viewModel.retryDownload(download) },
                        onRemove = { viewModel.removeDownload(download.songId) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItem(
    download: DownloadEntity,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = download.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusIcon: ImageVector
                    val statusText: String
                    val statusColor = when (download.status) {
                        DownloadStatus.DOWNLOADING -> {
                            statusIcon = Icons.Rounded.Download
                            statusText = "Downloading..."
                            MaterialTheme.colorScheme.primary
                        }
                        DownloadStatus.COMPLETED -> {
                            statusIcon = Icons.Rounded.CheckCircle
                            statusText = "Completed"
                            MaterialTheme.colorScheme.secondary
                        }
                        DownloadStatus.FAILED -> {
                            statusIcon = Icons.Rounded.Error
                            statusText = "Failed"
                            MaterialTheme.colorScheme.error
                        }
                        DownloadStatus.PENDING -> {
                            statusIcon = Icons.Rounded.Download
                            statusText = "Pending"
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        else -> {
                            statusIcon = Icons.Rounded.Cancel
                            statusText = "Cancelled"
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    }
                    
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }

                if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PENDING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { download.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
                
                if (download.status == DownloadStatus.FAILED && download.errorMessage != null) {
                    Text(
                        text = download.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            when (download.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Cancel, contentDescription = "Cancel")
                    }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry")
                    }
                }
                DownloadStatus.COMPLETED -> {
                    // Maybe show "Open" or "Delete"
                }
            }
        }
    }
}
