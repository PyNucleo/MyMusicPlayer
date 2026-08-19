package com.admin.mymusicplayer.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.admin.mymusicplayer.search.SearchResultItem
import coil3.compose.AsyncImage

@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var addTarget by remember { mutableStateOf<SearchResultItem?>(null) }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onEvent(SearchEvent.NoticeShown)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onEvent(SearchEvent.QueryChanged(it)) },
                label = { Text("Song, artist, title, or YouTube URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.onEvent(SearchEvent.Submit) }),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.onEvent(SearchEvent.Submit) },
                enabled = !state.isLoading,
                modifier = Modifier.align(Alignment.CenterVertically),
            ) { Text("Search") }
        }

        state.error?.let { error ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { viewModel.onEvent(SearchEvent.Retry) }) { Text("Retry") }
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(state.results, key = { "${it.sourceType}:${it.sourceMediaId}" }) { result ->
                    SearchResultRow(
                        result = result,
                        onPlayNow = { viewModel.onEvent(SearchEvent.PlayNow(result)) },
                        onPlayNext = { viewModel.onEvent(SearchEvent.PlayNext(result)) },
                        onQueue = { viewModel.onEvent(SearchEvent.AddToQueue(result)) },
                        onAdd = { addTarget = result },
                    )
                    HorizontalDivider()
                }
                if (state.nextPage != null) {
                    item {
                        OutlinedButton(
                            onClick = { viewModel.onEvent(SearchEvent.LoadMore) },
                            enabled = !state.isLoadingMore,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        ) {
                            Text(if (state.isLoadingMore) "Loading…" else "Load More")
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    addTarget?.let { result ->
        AlertDialog(
            onDismissRequest = { addTarget = null },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    if (state.playlistTargets.isEmpty()) {
                        Text("Create a playlist first")
                    } else {
                        state.playlistTargets.forEach { (id, name) ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.onEvent(SearchEvent.AddToPlaylist(result, id))
                                    addTarget = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(name) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { OutlinedButton(onClick = { addTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResultItem,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = "Thumbnail for ${result.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(112.dp).height(63.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium)
                Text(result.uploader ?: "Unknown uploader", style = MaterialTheme.typography.bodyMedium)
                Text(formatDuration(result.durationMs), style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onPlayNow) { Text("Play") }
            OutlinedButton(onClick = onPlayNext) { Text("Next") }
            OutlinedButton(onClick = onQueue) { Text("Queue") }
            OutlinedButton(onClick = onAdd) { Text("Playlist") }
        }
    }
}

private fun formatDuration(durationMs: Long?): String {
    if (durationMs == null) return "Duration unavailable"
    val seconds = durationMs / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
