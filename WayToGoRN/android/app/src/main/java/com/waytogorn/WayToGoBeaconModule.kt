package com.waytogorn

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.Locale

/**
 * React Native bridge for the real Android BLE scanner (Section 6). Emits
 * `WayToGoBeacon:reading` events shaped like the JS `RawReading`
 * ({uuid, major, minor, rssi, txPower}) and `WayToGoBeacon:error` with an
 * explicit reason. Mirrors the original Kotlin `AndroidBeaconScanner`.
 */
class WayToGoBeaconModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private var targetUuid: String = ""

    override fun getName(): String = "WayToGoBeacon"

    // Required so JS NativeEventEmitter attaches without warnings.
    @ReactMethod fun addListener(eventName: String) {}

    @ReactMethod fun removeListeners(count: Int) {}

    @ReactMethod
    fun startScan(uuid: String, promise: Promise) {
        targetUuid = normalizeUuid(uuid)

        if (!hasScanPermissions()) {
            emitError("PERMISSION_DENIED", "BLE scan permission not granted")
            promise.reject("PERMISSION_DENIED", "BLE scan permission not granted")
            return
        }
        val manager = reactContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            emitError("BLUETOOTH_OFF", "Bluetooth is off")
            promise.reject("BLUETOOTH_OFF", "Bluetooth is off")
            return
        }
        if (!isLocationEnabled()) {
            emitError("LOCATION_SERVICES_OFF", "Location services are off")
            promise.reject("LOCATION_SERVICES_OFF", "Location services are off")
            return
        }
        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            emitError("BLUETOOTH_OFF", "No BLE scanner available")
            promise.reject("BLUETOOTH_OFF", "No BLE scanner available")
            return
        }

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handle(it) }
            }
            override fun onScanFailed(errorCode: Int) {
                emitError("SCAN_FAILED", "Scan failed: code $errorCode")
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        val filters = listOf(buildIBeaconFilter(targetUuid))

        try {
            leScanner.startScan(filters, settings, cb)
            scanner = leScanner
            callback = cb
            promise.resolve(true)
        } catch (e: SecurityException) {
            emitError("PERMISSION_DENIED", "startScan denied: ${e.message}")
            promise.reject("PERMISSION_DENIED", e.message)
        }
    }

    @ReactMethod
    fun stopScan() {
        val s = scanner
        val cb = callback
        if (s != null && cb != null) {
            try {
                s.stopScan(cb)
            } catch (_: Exception) {
            }
        }
        scanner = null
        callback = null
    }

    private fun handle(result: ScanResult) {
        val record = result.scanRecord ?: return
        val mfg = record.getManufacturerSpecificData(APPLE_COMPANY_ID) ?: return
        val frame = parseIBeacon(mfg) ?: return
        if (normalizeUuid(frame.uuid) != targetUuid) return
        val map: WritableMap = Arguments.createMap().apply {
            putString("uuid", frame.uuid)
            putInt("major", frame.major)
            putInt("minor", frame.minor)
            putInt("rssi", result.rssi)
            putInt("txPower", frame.txPower)
            putDouble("timestampMs", SystemClock.elapsedRealtime().toDouble())
        }
        emit("WayToGoBeacon:reading", map)
    }

    private fun emitError(reason: String, message: String) {
        val map = Arguments.createMap().apply {
            putString("reason", reason)
            putString("message", message)
        }
        emit("WayToGoBeacon:error", map)
    }

    private fun emit(event: String, params: WritableMap) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(event, params)
    }

    private fun hasScanPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            reactContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val scan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                reactContext, Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return fine && scan
    }

    private fun isLocationEnabled(): Boolean {
        val lm = reactContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private data class Frame(val uuid: String, val major: Int, val minor: Int, val txPower: Int)

    private fun parseIBeacon(data: ByteArray): Frame? {
        if (data.size < 23) return null
        if ((data[0].toInt() and 0xFF) != 0x02) return null
        if ((data[1].toInt() and 0xFF) != 0x15) return null
        val hex = StringBuilder(36)
        for (i in 0 until 16) {
            hex.append(String.format(Locale.US, "%02X", data[2 + i].toInt() and 0xFF))
            if (i == 3 || i == 5 || i == 7 || i == 9) hex.append('-')
        }
        val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
        val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
        val txPower = data[22].toInt()
        return Frame(hex.toString(), major, minor, txPower)
    }

    companion object {
        const val APPLE_COMPANY_ID = 0x004C

        fun normalizeUuid(uuid: String): String = uuid.trim().replace("-", "").uppercase()

        fun buildIBeaconFilter(uuid: String): ScanFilter {
            val bytes = uuidToBytes(uuid)
            val data = ByteArray(18)
            data[0] = 0x02
            data[1] = 0x15
            System.arraycopy(bytes, 0, data, 2, 16)
            val mask = ByteArray(18) { 0xFF.toByte() }
            return ScanFilter.Builder()
                .setManufacturerData(APPLE_COMPANY_ID, data, mask)
                .build()
        }

        private fun uuidToBytes(uuid: String): ByteArray {
            val hex = uuid.replace("-", "")
            require(hex.length == 32) { "Invalid UUID: $uuid" }
            return ByteArray(16) { i ->
                ((Character.digit(hex[i * 2], 16) shl 4) or Character.digit(hex[i * 2 + 1], 16)).toByte()
            }
        }
    }
}
