package com.waytogorn

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/**
 * Registers [WayToGoBeaconModule]. Add `WayToGoBeaconPackage()` to the list
 * returned by `getPackages()` in the RN-generated `MainApplication`.
 */
class WayToGoBeaconPackage : ReactPackage {
    override fun createNativeModules(
        reactContext: ReactApplicationContext,
    ): List<NativeModule> = listOf(WayToGoBeaconModule(reactContext))

    override fun createViewManagers(
        reactContext: ReactApplicationContext,
    ): List<ViewManager<*, *>> = emptyList()
}
