package com.waytogo.ui.map

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.waytogo.core.model.PositionFix
import com.waytogo.core.model.PositionState
import com.waytogo.di.ScannerMode
import com.waytogo.ui.debug.DebugOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: MapViewModel) {
    val context = LocalContext.current
    val state by vm.positionState.collectAsState()
    val debug by vm.debug.collectAsState()
    val ui by vm.ui.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }

    val currentFix: PositionFix? = when (val s = state) {
        is PositionState.Located -> s.fix
        is PositionState.Degraded -> s.lastFix
        else -> null
    }
    val resolvedFloor = currentFix?.floorLevel ?: debug.resolvedFloor
    val displayedLevel = ui.displayedFloorOverride
        ?: resolvedFloor
        ?: vm.floors.firstOrNull()?.level
    val displayedFloor = vm.floors.firstOrNull { it.level == displayedLevel }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WayToGo") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (ui.debugVisible) "Hide debug overlay" else "Show debug overlay") },
                            onClick = { vm.toggleDebug(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text(if (ui.loggingEnabled) "Stop raw logging" else "Start raw logging") },
                            onClick = { vm.toggleLogging(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Export scan log") },
                            onClick = { menuOpen = false; shareLog(context, vm) },
                        )
                        DropdownMenuItem(
                            text = { Text("Scanner: Live") },
                            onClick = { vm.setScannerMode(ScannerMode.Live); menuOpen = false },
                        )
                        listOf(1.0, 2.0, 4.0).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("Scanner: Replay ${speed.toInt()}x") },
                                onClick = { vm.setScannerMode(ScannerMode.Replay(speed)); menuOpen = false },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!ui.followMode || ui.displayedFloorOverride != null) {
                FloatingActionButton(onClick = { vm.recenter() }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Recenter")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val image = displayedFloor?.let { FloorImages.load(context, it) }
            if (displayedFloor != null && image != null) {
                val onDisplayedFloor = currentFix != null && currentFix.floorLevel == displayedFloor.level
                val dimmed = state is PositionState.Degraded
                FloorPlanCanvas(
                    floor = displayedFloor,
                    image = image,
                    fix = if (onDisplayedFloor) currentFix else null,
                    dimmed = dimmed,
                    debugVisible = ui.debugVisible,
                    beacons = vm.registryRepository()?.beaconsOnFloor(displayedFloor.level) ?: emptyList(),
                    heardKeys = debug.heard.map { it.beacon.key }.toSet(),
                    usedKeys = debug.usedKeys,
                    onUserPan = { vm.onUserPanned() },
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No floor plan available")
                }
            }

            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                // Long-press the status area toggles the debug overlay.
                StatusBanner(
                    state = state,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { vm.toggleDebug() })
                    },
                    onEnableAction = { openAction(context, state) },
                )
                FloorSelector(
                    floors = vm.floors.map { it.level to it.name },
                    selected = displayedLevel,
                    resolved = resolvedFloor,
                    onSelect = { vm.selectFloor(it) },
                )
                if (resolvedFloor != null && displayedLevel != resolvedFloor) {
                    AssistChip(
                        onClick = { vm.recenter() },
                        label = { Text("You are on floor $resolvedFloor") },
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            if (ui.debugVisible) {
                DebugOverlay(snapshot = debug, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun FloorSelector(
    floors: List<Pair<Int, String>>,
    selected: Int?,
    resolved: Int?,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        floors.forEach { (level, name) ->
            FilterChip(
                selected = level == selected,
                onClick = { onSelect(level) },
                label = { Text(if (level == resolved) "$name ●" else name) },
            )
        }
    }
}

@Composable
private fun StatusBanner(
    state: PositionState,
    modifier: Modifier = Modifier,
    onEnableAction: () -> Unit,
) {
    val text: String? = when (state) {
        is PositionState.Searching -> "Looking for beacons..."
        is PositionState.Degraded -> "Signal weak - showing last known position"
        is PositionState.Idle -> "Idle"
        is PositionState.Error -> errorText(state)
        is PositionState.Located -> null
    }
    if (text == null) return
    val color = if (state is PositionState.Error) Color(0xFFB00020) else Color(0xCC333333)
    Row(
        modifier
            .fillMaxWidth()
            .background(color)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        if (state is PositionState.Error) {
            AssistChip(onClick = onEnableAction, label = { Text(actionLabel(state)) })
        }
    }
}

private fun errorText(state: PositionState.Error): String = when (state.kind) {
    com.waytogo.core.model.ErrorKind.PERMISSION_DENIED -> "Permission needed to scan for beacons"
    com.waytogo.core.model.ErrorKind.BLUETOOTH_OFF -> "Bluetooth is off"
    com.waytogo.core.model.ErrorKind.LOCATION_SERVICES_OFF -> "Location services are off"
    com.waytogo.core.model.ErrorKind.SCAN_FAILED -> "Bluetooth scan failed"
    com.waytogo.core.model.ErrorKind.REGISTRY_INVALID -> "Beacon registry could not be loaded"
}

private fun actionLabel(state: PositionState.Error): String = when (state.kind) {
    com.waytogo.core.model.ErrorKind.PERMISSION_DENIED -> "Settings"
    com.waytogo.core.model.ErrorKind.BLUETOOTH_OFF -> "Enable"
    com.waytogo.core.model.ErrorKind.LOCATION_SERVICES_OFF -> "Enable"
    else -> "Retry"
}

private fun openAction(context: android.content.Context, state: PositionState) {
    if (state !is PositionState.Error) return
    val intent = when (state.kind) {
        com.waytogo.core.model.ErrorKind.BLUETOOTH_OFF ->
            Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        com.waytogo.core.model.ErrorKind.LOCATION_SERVICES_OFF ->
            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        com.waytogo.core.model.ErrorKind.PERMISSION_DENIED ->
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null),
            )
        else -> return
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun shareLog(context: android.content.Context, vm: MapViewModel) {
    val file = vm.exportLogFile() ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export scan log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
