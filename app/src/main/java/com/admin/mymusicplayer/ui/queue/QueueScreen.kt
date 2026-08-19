package com.admin.mymusicplayer.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun QueueScreen(viewModel: QueueViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Queue", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { viewModel.onEvent(QueueEvent.ClearUpcoming) }, enabled = snapshot.upcoming.isNotEmpty()) {
                Text("Clear upcoming")
            }
        }
        Text("Current", style = MaterialTheme.typography.labelLarge)
        snapshot.current?.let { current ->
            Text(current.title, style = MaterialTheme.typography.titleLarge)
            Text(current.uploader.orEmpty())
        } ?: Text("Queue is empty")
        HorizontalDivider()
        Text("Upcoming", style = MaterialTheme.typography.labelLarge)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(
                snapshot.entries,
                key = { _, track -> "${track.sourceType}:${track.sourceMediaId}" },
            ) { index, track ->
                if (index > snapshot.currentIndex) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(track.title, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.onEvent(QueueEvent.MoveUp(index)) }) { Text("↑") }
                        TextButton(onClick = { viewModel.onEvent(QueueEvent.MoveDown(index)) }) { Text("↓") }
                        TextButton(onClick = { viewModel.onEvent(QueueEvent.Remove(index)) }) { Text("Remove") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

