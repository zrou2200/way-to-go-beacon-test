package com.waytogo.platform.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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
import com.waytogo.core.model.RawReading
import com.waytogo.core.scan.BeaconScanner
import com.waytogo.core.scan.ScanException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Real BLE scanner (Section 6). Emits [RawReading]s for iBeacon advertisements
 * matching [registryUuid]. Uses a manufacturer-data scan filter to keep callback
 * volume low, SCAN_MODE_LOW_LATENCY, and elapsedRealtime timestamps.
 */
class AndroidBeaconScanner(
    private val context: Context,
    private val registryUuid: String,
) : BeaconScanner {

    // Registry UUIDs may or may not contain dashes; normalize both sides before comparing.
    private val targetUuid = normalizeUuid(registryUuid)

    override fun readings(): Flow<RawReading> = callbackFlow {
        // Pre-flight checks map to explicit error reasons.
        if (!hasScanPermissions()) {
            throw ScanException(ScanException.Reason.PERMISSION_DENIED, "BLE scan permission not granted")
        }
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            throw ScanException(ScanException.Reason.BLUETOOTH_OFF, "Bluetooth is off")
        }
        if (!isLocationEnabled()) {
            throw ScanException(ScanException.Reason.LOCATION_SERVICES_OFF, "Location services are off")
        }
        val scanner = adapter.bluetoothLeScanner
            ?: throw ScanException(ScanException.Reason.BLUETOOTH_OFF, "No BLE scanner available")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                emit(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { emit(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                // Do not restart in a tight loop; surface the failure and let the UI retry.
                close(ScanException(ScanException.Reason.SCAN_FAILED, "Scan failed: code $errorCode"))
            }

            private fun emit(result: ScanResult) {
                val record = result.scanRecord ?: return
                val mfg = record.getManufacturerSpecificData(APPLE_COMPANY_ID) ?: return
                val frame = IBeaconParser.parse(mfg) ?: return
                if (normalizeUuid(frame.uuid) != targetUuid) return
                trySend(
                    RawReading(
                        key = com.waytogo.core.model.BeaconKey(normalizeUuid(frame.uuid), frame.major, frame.minor),
                        rssi = result.rssi,
                        advertisedTxPower = frame.txPower,
                        timestampMs = SystemClock.elapsedRealtime(),
                    )
                )
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        val filters = listOf(buildIBeaconFilter(targetUuid))

        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            throw ScanException(ScanException.Reason.PERMISSION_DENIED, "startScan denied: ${e.message}")
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission revoked mid-session; nothing more we can do.
            } catch (_: IllegalStateException) {
                // Adapter turned off; scan already stopped.
            }
        }
    }

    private fun hasScanPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val scan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return fineLocation && scan
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    companion object {
        const val APPLE_COMPANY_ID = 0x004C

        /** Strips dashes and uppercases so registry values with or without dashes compare equal. */
        fun normalizeUuid(uuid: String): String = uuid.trim().replace("-", "").uppercase()

        /** Builds a filter matching iBeacon type/length + the 16 UUID bytes. */
        fun buildIBeaconFilter(uuid: String): ScanFilter {
            val uuidBytes = uuidToBytes(uuid)
            val data = ByteArray(18)
            data[0] = 0x02
            data[1] = 0x15
            System.arraycopy(uuidBytes, 0, data, 2, 16)
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
