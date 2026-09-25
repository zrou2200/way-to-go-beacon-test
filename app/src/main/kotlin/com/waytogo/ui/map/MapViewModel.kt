package com.waytogo.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.waytogo.core.engine.DebugSnapshot
import com.waytogo.core.engine.PositioningRepository
import com.waytogo.core.model.ErrorKind
import com.waytogo.core.model.FloorPlan
import com.waytogo.core.model.PositionState
import com.waytogo.di.AppContainer
import com.waytogo.di.ScannerMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI-facing state that is independent of the positioning pipeline. */
data class MapUiState(
    val displayedFloorOverride: Int? = null,   // null = auto-follow resolved floor
    val followMode: Boolean = true,            // recenter on the user
    val debugVisible: Boolean = false,
    val loggingEnabled: Boolean = false,
    val scannerMode: ScannerMode = ScannerMode.Live,
)

class MapViewModel(private val container: AppContainer) : ViewModel() {

    private val repository: PositioningRepository? = container.createRepository()

    val floors: List<FloorPlan> = container.registryLoad.repository?.floors() ?: emptyList()
    val registryWarnings: List<String> = container.registryLoad.warnings
    val registryValid: Boolean = container.registryLoad.isValid

    val positionState: StateFlow<PositionState> =
        repository?.state ?: MutableStateFlow(PositionState.Error(ErrorKind.REGISTRY_INVALID))
    val debug: StateFlow<DebugSnapshot> =
        repository?.debug ?: MutableStateFlow(DebugSnapshot())

    private val _ui = MutableStateFlow(MapUiState())
    val ui: StateFlow<MapUiState> = _ui.asStateFlow()

    private var scanJob: Job? = null
    private var started = false

    fun registryRepository() = container.registryLoad.repository

    /** Called when the map screen becomes visible with permissions satisfied. */
    fun onStart() {
        if (!registryValid) {
            repository?.onError(ErrorKind.REGISTRY_INVALID)
            return
        }
        if (started) return
        started = true
        restartScanner()
    }

    fun onStop() {
        started = false
        scanJob?.cancel()
        scanJob = null
    }

    fun onPermissionDenied() {
        repository?.onError(ErrorKind.PERMISSION_DENIED)
    }

    fun onPermissionsGranted() {
        if (!registryValid) {
            repository?.onError(ErrorKind.REGISTRY_INVALID)
            return
        }
        repository?.clearError()
        started = true
        restartScanner()
    }

    private fun restartScanner() {
        val repo = repository ?: return
        scanJob?.cancel()
        val scanner = container.createScanner(_ui.value.scannerMode)
        scanJob = repo.start(viewModelScope, scanner)
    }

    fun setScannerMode(mode: ScannerMode) {
        _ui.value = _ui.value.copy(scannerMode = mode)
        if (started) restartScanner()
    }

    fun selectFloor(level: Int) {
        _ui.value = _ui.value.copy(displayedFloorOverride = level, followMode = false)
    }

    /** Snap the displayed floor back to the resolved floor and re-center. */
    fun recenter() {
        _ui.value = _ui.value.copy(displayedFloorOverride = null, followMode = true)
    }

    fun onUserPanned() {
        if (_ui.value.followMode) _ui.value = _ui.value.copy(followMode = false)
    }

    fun toggleDebug() {
        _ui.value = _ui.value.copy(debugVisible = !_ui.value.debugVisible)
    }

    fun toggleLogging() {
        val next = !_ui.value.loggingEnabled
        container.logger.setEnabled(next)
        _ui.value = _ui.value.copy(loggingEnabled = next)
    }

    fun exportLogFile() = container.logger.currentFile()

    override fun onCleared() {
        onStop()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MapViewModel(container) as T
    }
}
