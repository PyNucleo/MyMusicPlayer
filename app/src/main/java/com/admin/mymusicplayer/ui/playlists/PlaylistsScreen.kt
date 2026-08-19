package com.admin.mymusicplayer.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (Long) -> Unit,
    viewModel: PlaylistsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var createDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistSummaryUi?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Playlists", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { createDialog = true }) { Text("Create") }
        }
        OutlinedTextField(
            value = state.filter,
            onValueChange = { viewModel.onEvent(PlaylistsEvent.FilterChanged(it)) },
            label = { Text("Filter playlists") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f)) {
            items(state.playlists, key = { it.playlist.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenPlaylist(item.playlist.id) }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.playlist.name, style = MaterialTheme.typography.titleMedium)
                        Text("${item.trackCount} tracks · revision ${item.playlist.revision}")
                    }
                    TextButton(onClick = { renameTarget = item }) { Text("Rename") }
                    TextButton(onClick = { viewModel.onEvent(PlaylistsEvent.Delete(item.playlist.id)) }) {
                        Text("Delete")
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (createDialog) {
        NameDialog(
            title = "Create playlist",
            initial = "",
            onConfirm = { name ->
                viewModel.onEvent(PlaylistsEvent.Create(name))
                createDialog = false
            },
            onDismiss = { createDialog = false },
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = "Rename playlist",
            initial = target.playlist.name,
            onConfirm = { name ->
                viewModel.onEvent(PlaylistsEvent.Rename(target.playlist.id, name))
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        },
        confirmButton = { Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
