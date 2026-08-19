package com.admin.mymusicplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.admin.mymusicplayer.domain.RepeatMode

@Composable
fun PlayerScreen(viewModel: PlayerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Now Playing", style = MaterialTheme.typography.labelLarge)
        Text(state.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp))
        Text(state.uploader, style = MaterialTheme.typography.bodyLarge)
        if (state.isBuffering) Text("Buffering…", modifier = Modifier.padding(top = 8.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Slider(
            value = state.positionMs.coerceIn(0, state.durationMs.coerceAtLeast(1)).toFloat(),
            onValueChange = { viewModel.onEvent(PlayerEvent.Seek(it.toLong())) },
            valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(state.positionMs))
            Text(formatTime(state.durationMs))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 20.dp)) {
            OutlinedButton(onClick = { viewModel.onEvent(PlayerEvent.Previous) }) { Text("Previous") }
            Button(onClick = { viewModel.onEvent(PlayerEvent.TogglePlay) }) {
                Text(if (state.isPlaying) "Pause" else "Play")
            }
            OutlinedButton(onClick = { viewModel.onEvent(PlayerEvent.Next) }) { Text("Next") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
            OutlinedButton(onClick = { viewModel.onEvent(PlayerEvent.ToggleShuffle) }) {
                Text(if (state.shuffleEnabled) "Shuffle On" else "Shuffle Off")
            }
            if (state.shuffleEnabled) {
                OutlinedButton(onClick = { viewModel.onEvent(PlayerEvent.Reshuffle) }) { Text("Reshuffle") }
            }
            OutlinedButton(onClick = { viewModel.onEvent(PlayerEvent.CycleRepeat) }) {
                Text(
                    when (state.repeatMode) {
                        RepeatMode.PLAY_ONCE -> "① Play Once"
                        RepeatMode.REPEAT_ONCE -> "② Repeat Once"
                        RepeatMode.REPEAT_FOREVER -> "∞ Repeat Forever"
                    },
                )
            }
        }
        Text("Native background playback", modifier = Modifier.padding(top = 24.dp))
    }
}

private fun formatTime(ms: Long): String {
    val seconds = ms / 1_000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
