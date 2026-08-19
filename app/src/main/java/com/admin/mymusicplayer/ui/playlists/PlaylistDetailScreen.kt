package com.admin.mymusicplayer.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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

private enum class TransferKind { MOVE, COPY }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(viewModel: PlaylistDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var transferKind by remember { mutableStateOf<TransferKind?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Column(Modifier.weight(1f)) {
                Text(state.name, style = MaterialTheme.typography.headlineSmall)
                Text("${state.tracks.size} tracks")
            }
        }
        if (state.selected.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${state.selected.size} selected", modifier = Modifier.align(Alignment.CenterVertically))
                TextButton(onClick = { viewModel.onEvent(PlaylistDetailEvent.SelectAll) }) { Text("All") }
                TextButton(onClick = { viewModel.onEvent(PlaylistDetailEvent.ClearSelection) }) { Text("Clear") }
                TextButton(onClick = { transferKind = TransferKind.MOVE }) { Text("Move") }
                TextButton(onClick = { transferKind = TransferKind.COPY }) { Text("Copy") }
                TextButton(onClick = { viewModel.onEvent(PlaylistDetailEvent.Remove) }) { Text("Remove") }
            }
        } else {
            Text("Long-press a track to select it", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = state.filter,
            onValueChange = { viewModel.onEvent(PlaylistDetailEvent.FilterChanged(it)) },
            label = { Text("Search in playlist") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.message?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, modifier = Modifier.weight(1f))
                if (state.canUndo) Button(onClick = { viewModel.onEvent(PlaylistDetailEvent.Undo) }) { Text("Undo") }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(
                state.visibleTracks,
                key = { _, track -> "${track.sourceType}:${track.sourceMediaId}" },
            ) { index, track ->
                val selected = track.sourceIdentity in state.selected
                val actualIndex = state.tracks.indexOfFirst { it.sourceIdentity == track.sourceIdentity }
                Row(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = {
                            if (state.selected.isNotEmpty()) {
                                viewModel.onEvent(PlaylistDetailEvent.ToggleSelection(track.sourceIdentity))
                            } else {
                                viewModel.onEvent(PlaylistDetailEvent.PlayTrack(track))
                            }
                        },
                        onLongClick = { viewModel.onEvent(PlaylistDetailEvent.ToggleSelection(track.sourceIdentity)) },
                    ).padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(if (selected) "✓" else "${index + 1}")
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium)
                        Text(track.uploader.orEmpty())
                    }
                    if (state.selected.isEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.onEvent(PlaylistDetailEvent.Reorder(track.sourceIdentity, actualIndex - 1))
                            },
                            enabled = actualIndex > 0,
                        ) { Text("↑") }
                        TextButton(
                            onClick = {
                                viewModel.onEvent(PlaylistDetailEvent.Reorder(track.sourceIdentity, actualIndex + 1))
                            },
                            enabled = actualIndex in 0 until state.tracks.lastIndex,
                        ) { Text("↓") }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    transferKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { transferKind = null },
            title = { Text("${if (kind == TransferKind.MOVE) "Move" else "Copy"} to") },
            text = {
                Column {
                    state.targetPlaylists.forEach { (id, name) ->
                        TextButton(onClick = {
                            viewModel.onEvent(
                                if (kind == TransferKind.MOVE) PlaylistDetailEvent.MoveTo(id)
                                else PlaylistDetailEvent.CopyTo(id),
                            )
                            transferKind = null
                        }) { Text(name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { OutlinedButton(onClick = { transferKind = null }) { Text("Cancel") } },
        )
    }
}
