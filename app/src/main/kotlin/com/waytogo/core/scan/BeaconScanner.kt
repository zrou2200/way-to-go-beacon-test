package com.waytogo.core.scan

import com.waytogo.core.model.RawReading
import kotlinx.coroutines.flow.Flow

/**
 * Source of beacon readings. Two implementations exist (Section 3.1):
 * the real Android BLE scanner and the CSV replay scanner. The interface is
 * pure Kotlin (coroutines only) so the positioning core stays JVM-testable.
 */
interface BeaconScanner {
    /**
     * Cold flow of readings. Collecting starts scanning; cancelling stops it.
     * Implementations should surface fatal problems by throwing a
     * [ScanException] so the repository can map it to an error state.
     */
    fun readings(): Flow<RawReading>
}

/** Reasons a scan cannot proceed, mapped to [com.waytogo.core.model.ErrorKind]. */
class ScanException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { BLUETOOTH_OFF, LOCATION_SERVICES_OFF, PERMISSION_DENIED, SCAN_FAILED }
}
