package com.admin.mymusicplayer.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.admin.mymusicplayer.backup.BackupManager
import com.admin.mymusicplayer.backup.ValidatedBackup
import com.admin.mymusicplayer.playback.PlaybackClient
import com.admin.mymusicplayer.diagnostics.DiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val automaticBackupLocation: String? = null,
    val pendingRestore: ValidatedBackup? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val lastError: String? = null,
)

class SettingsViewModel(
    private val backupManager: BackupManager,
    private val playbackClient: PlaybackClient,
    private val diagnostics: DiagnosticLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            backupManager.automaticLocation.collect { location ->
                _state.value = _state.value.copy(automaticBackupLocation = location)
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(lastError = diagnostics.lastError())
        }
    }

    fun backupTo(uri: Uri) = launchAction {
        val summary = backupManager.writeManualBackup(uri)
        "Backup created: ${summary.playlistCount} playlists, ${summary.trackCount} tracks"
    }

    fun inspectRestore(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching { backupManager.inspectRestore(uri) }
                .onSuccess { _state.value = _state.value.copy(busy = false, pendingRestore = it) }
                .onFailure { _state.value = _state.value.copy(busy = false, message = it.message ?: "Invalid backup") }
        }
    }

    fun confirmRestore() {
        val pending = _state.value.pendingRestore ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching { backupManager.restore(pending) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        busy = false,
                        pendingRestore = null,
                        message = "Restore complete: ${pending.summary.playlistCount} playlists",
                    )
                }
                .onFailure { _state.value = _state.value.copy(busy = false, message = it.message ?: "Restore failed") }
        }
    }

    fun cancelRestore() {
        _state.value = _state.value.copy(pendingRestore = null)
    }

    fun setAutomaticLocation(uri: Uri) {
        backupManager.setAutomaticLocation(uri)
        _state.value = _state.value.copy(message = "Automatic rolling backups enabled")
    }

    fun clearCache() = launchAction {
        val resources = playbackClient.clearPlaybackCache()
        "Playback cache cleared ($resources resources)"
    }

    fun exportDiagnostics(uri: Uri) = launchAction {
        diagnostics.export(uri)
        "Diagnostic bundle exported"
    }

    private fun launchAction(action: suspend () -> String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching { action() }
                .onSuccess { _state.value = _state.value.copy(busy = false, message = it) }
                .onFailure { _state.value = _state.value.copy(busy = false, message = it.message ?: "Action failed") }
        }
    }
}
