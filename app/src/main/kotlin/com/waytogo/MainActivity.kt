package com.waytogo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waytogo.di.ScannerMode
import com.waytogo.ui.map.MapScreen
import com.waytogo.ui.map.MapViewModel
import com.waytogo.ui.permissions.PermissionGate
import com.waytogo.ui.theme.WayToGoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Navigation-style screen: keep it awake while visible (Section 7.3).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val container = (application as WayToGoApp).container

        setContent {
            WayToGoTheme {
                val vm: MapViewModel = viewModel(factory = MapViewModel.Factory(container))
                val ui by vm.ui.collectAsState()

                // Foreground-only scanning tied to the lifecycle.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, ui.scannerMode) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> vm.onStart()
                            Lifecycle.Event.ON_STOP -> vm.onStop()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (ui.scannerMode is ScannerMode.Live) {
                    PermissionGate(
                        onGranted = { vm.onPermissionsGranted() },
                        onDenied = { vm.onPermissionDenied() },
                    ) {
                        MapScreen(vm)
                    }
                } else {
                    MapScreen(vm)
                }
            }
        }
    }
}
