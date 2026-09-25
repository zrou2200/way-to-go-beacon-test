package com.waytogo.core.model

/**
 * All positioning tunables in one place (Section 5). Defaults match the spec.
 * Loadable from a JSON asset; nothing here should be hard-coded elsewhere.
 */
data class PositioningConfig(
    val windowMs: Long = 3000,
    val staleMs: Long = 5000,
    val minSamplesPerBeacon: Int = 2,
    val pathLossExponent: Double = 2.5,
    val minDistanceM: Double = 0.5,
    val maxDistanceM: Double = 25.0,
    val maxBeaconsUsed: Int = 4,
    val centroidWeightPower: Double = 2.0,
    val emitIntervalMs: Long = 1000,
    val smoothingAlpha: Double = 0.35,
    val maxSpeedMps: Double = 2.5,
    val floorSwitchHoldMs: Long = 3000,
    val minAccuracyM: Double = 1.5,
    val positionStaleMs: Long = 8000,
)
