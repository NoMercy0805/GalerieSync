package de.example.nasphotosync.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FileProgress(val name: String, val status: FileStatus)

enum class FileStatus { WAITING, SYNCING, DONE, ERROR }

sealed class SyncUiState {
    object Idle : SyncUiState()
    data class Running(val profileName: String, val files: List<FileProgress>) : SyncUiState()
    data class Done(val profileName: String, val uploaded: Int, val downloaded: Int) : SyncUiState()
    data class Failed(val profileName: String, val message: String) : SyncUiState()
}

/**
 * Haelt den aktuellen Sync-Fortschritt im Speicher, damit der
 * Fortschritts-Screen live mitlesen kann, egal ob der Sync gerade von der
 * UI direkt oder im Hintergrund per WorkManager angestossen wurde.
 */
object SyncStatusBus {
    private val _state = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val state: StateFlow<SyncUiState> = _state

    fun update(newState: SyncUiState) {
        _state.value = newState
    }
}
