package com.waytogo.core.model

/** Unique identity of a beacon: proximity UUID plus major/minor. */
data class BeaconKey(val uuid: String, val major: Int, val minor: Int)

/** A beacon known to the app, loaded from the registry asset. */
data class RegisteredBeacon(
    val key: BeaconKey,
    val label: String,
    val floorLevel: Int,
    val x: Double,          // meters, floor-local coordinate system
    val y: Double,          // meters
    val rssi1m: Int,        // dBm, calibrated measured power at 1 m
)

/** A single advertisement observation as reported by the platform. */
data class RawReading(
    val key: BeaconKey,
    val rssi: Int,                 // dBm, as reported by the OS
    val advertisedTxPower: Int?,   // dBm from the iBeacon frame, may be null
    val timestampMs: Long,         // monotonic clock (SystemClock.elapsedRealtime)
)

/** Output of the aggregator: a filtered, distance-estimated view of one beacon. */
data class BeaconObservation(
    val beacon: RegisteredBeacon,
    val filteredRssi: Double,
    val distanceM: Double,
    val sampleCount: Int,
    val lastSeenMs: Long,
)

enum class Method { PROXIMITY, CENTROID, TRILATERATION }

/** A computed position on a floor. */
data class PositionFix(
    val floorLevel: Int,
    val x: Double,
    val y: Double,
    val accuracyM: Double,
    val beaconsUsed: Int,
    val method: Method,
    val timestampMs: Long,
)

enum class ErrorKind {
    PERMISSION_DENIED,
    BLUETOOTH_OFF,
    LOCATION_SERVICES_OFF,
    SCAN_FAILED,
    REGISTRY_INVALID,
}

/** High-level state surfaced to the UI. */
sealed interface PositionState {
    data object Idle : PositionState
    data object Searching : PositionState
    data class Located(val fix: PositionFix) : PositionState
    data class Degraded(val lastFix: PositionFix, val reason: String) : PositionState
    data class Error(val kind: ErrorKind) : PositionState
}
