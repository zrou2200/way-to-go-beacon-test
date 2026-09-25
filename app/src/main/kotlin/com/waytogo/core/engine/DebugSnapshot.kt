package com.waytogo.core.engine

import com.waytogo.core.model.BeaconKey
import com.waytogo.core.model.BeaconObservation
import com.waytogo.core.model.ErrorKind
import com.waytogo.core.model.Method

/** Snapshot of live diagnostics for the debug overlay (Section 7.2). */
data class DebugSnapshot(
    val heard: List<BeaconObservation> = emptyList(),
    val usedKeys: Set<BeaconKey> = emptySet(),
    val method: Method? = null,
    val accuracyM: Double? = null,
    val beaconsUsed: Int = 0,
    val scanRateHz: Double = 0.0,
    val resolvedFloor: Int? = null,
)

internal fun mapReason(reason: com.waytogo.core.scan.ScanException.Reason): ErrorKind =
    when (reason) {
        com.waytogo.core.scan.ScanException.Reason.BLUETOOTH_OFF -> ErrorKind.BLUETOOTH_OFF
        com.waytogo.core.scan.ScanException.Reason.LOCATION_SERVICES_OFF -> ErrorKind.LOCATION_SERVICES_OFF
        com.waytogo.core.scan.ScanException.Reason.PERMISSION_DENIED -> ErrorKind.PERMISSION_DENIED
        com.waytogo.core.scan.ScanException.Reason.SCAN_FAILED -> ErrorKind.SCAN_FAILED
    }
