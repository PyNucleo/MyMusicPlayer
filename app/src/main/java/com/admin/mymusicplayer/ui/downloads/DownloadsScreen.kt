package com.admin.mymusicplayer.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admin.mymusicplayer.data.repository.SessionRepository
import com.admin.mymusicplayer.downloads.DownloadRepository
import com.admin.mymusicplayer.downloads.DownloadStatus
import com.admin.mymusicplayer.playback.PlaybackQueueController
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(
    repository: DownloadRepository,
    sessionRepository: SessionRepository,
    playback: PlaybackQueueController,
    onBack: () -> Unit,
) {
    val downloads by repository.downloads.collectAsStateWithLifecycle()
    val storageError by repository.storageError.collectAsStateWithLifecycle()
    val session by sessionRepository.state.collectAsStateWithLifecycle(initialValue = null)
    val current = session?.let { it.entries.getOrNull(it.currentIndex)?.track }
    val scope = rememberCoroutineScope()
    var playbackError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Text("Downloads", style = MaterialTheme.typography.headlineMedium)
        Text("Keep the app open until downloads finish. Interrupted downloads can be retried. Saved audio plays offline and is kept until you remove it.")
        current?.let { track ->
            val existing = downloads.firstOrNull { it.track.sourceIdentity == track.sourceIdentity }
            Button(
                onClick = { repository.download(track) },
                enabled = existing == null || existing.status == DownloadStatus.FAILED,
            ) { Text("Download current audio") }
            Text(track.title, style = MaterialTheme.typography.bodyMedium)
        } ?: Text("Select a track in Search or a playlist, then return here to download it.")
        storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (downloads.isEmpty()) Text("No downloaded audio yet")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(downloads, key = { "${it.track.sourceType}:${it.track.sourceMediaId}" }) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.track.title, style = MaterialTheme.typography.titleMedium)
                    item.track.uploader?.let { Text(it) }
                    when (item.status) {
                        DownloadStatus.DOWNLOADING -> {
                            val total = item.totalBytes
                            if (total != null && total > 0) {
                                LinearProgressIndicator(
                                    progress = { (item.bytes.toFloat() / total).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Downloading · ${formatBytes(item.bytes)}")
                        }
                        DownloadStatus.SAVED -> Text("Available offline · ${formatBytes(item.bytes)}")
                        DownloadStatus.FAILED -> Text(item.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.status == DownloadStatus.SAVED) {
                            Button(onClick = {
                                scope.launch {
                                    playbackError = null
                                    runCatching { playback.playNow(item.track) }
                                        .onFailure { playbackError = "Could not start offline playback" }
                                }
                            }) { Text("Play") }
                        }
                        if (item.status == DownloadStatus.FAILED) {
                            Button(onClick = { repository.download(item.track) }) { Text("Retry") }
                        }
                        OutlinedButton(onClick = { repository.remove(item.track.sourceIdentity) }) {
                            Text(if (item.status == DownloadStatus.DOWNLOADING) "Cancel" else "Remove download")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        Text("Downloads use app storage and are excluded from library backups. Uninstalling removes them.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatBytes(bytes: Long): String = "%.1f MB".format(bytes / (1024.0 * 1024.0))
