package com.admin.mymusicplayer.ui.settings

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admin.mymusicplayer.BuildConfig

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::backupTo) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::inspectRestore) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setAutomaticLocation(it)
        }
    }
    val diagnosticsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportDiagnostics) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Library safety", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { backupLauncher.launch("my-music-player-backup.json") },
                enabled = !state.busy,
            ) { Text("Backup now") }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) },
                enabled = !state.busy,
            ) { Text("Restore backup") }
        }
        OutlinedButton(onClick = { folderLauncher.launch(null) }, enabled = !state.busy) {
            Text(if (state.automaticBackupLocation == null) "Choose automatic backup folder" else "Change backup folder")
        }
        Text(
            state.automaticBackupLocation?.let { "Rolling backups enabled (keeps 10)" }
                ?: "Automatic backups are not configured",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Storage", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = viewModel::clearCache, enabled = !state.busy) { Text("Clear playback cache") }
        Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("My Music Player last error", state.lastError ?: "No recorded error"),
                    )
                },
            ) { Text("Copy last error") }
            OutlinedButton(
                onClick = { diagnosticsLauncher.launch("my-music-player-diagnostics.json") },
                enabled = !state.busy,
            ) { Text("Export bundle") }
        }
        if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        state.message?.let { Text(it, modifier = Modifier.fillMaxWidth()) }
        Text("Build", style = MaterialTheme.typography.titleLarge)
        Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text("Commit ${BuildConfig.GIT_COMMIT}")
        Text("Room ${BuildConfig.DATABASE_VERSION} · Media3 ${BuildConfig.MEDIA3_VERSION} · NewPipe ${BuildConfig.NEWPIPE_VERSION}")
    }

    state.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Restore validated backup?") },
            text = {
                Text(
                    "Backup contains ${pending.summary.playlistCount} playlists, " +
                        "${pending.summary.trackCount} tracks, and ${pending.summary.entryCount} entries. " +
                        "This replaces the current playlist library.",
                )
            },
            confirmButton = { Button(onClick = viewModel::confirmRestore) { Text("Restore") } },
            dismissButton = { OutlinedButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }
}
