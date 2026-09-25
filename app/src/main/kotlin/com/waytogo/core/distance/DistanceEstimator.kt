package com.waytogo.core.distance

import kotlin.math.pow

/**
 * Log-distance path-loss model (Section 5.2):
 *   distance = 10 ^ ((rssi1m - filteredRssi) / (10 * n))
 * Result is clamped to [minDistanceM, maxDistanceM].
 */
object DistanceEstimator {

    fun estimate(
        filteredRssi: Double,
        rssi1m: Int,
        pathLossExponent: Double,
        minDistanceM: Double,
        maxDistanceM: Double,
    ): Double {
        val exponent = (rssi1m - filteredRssi) / (10.0 * pathLossExponent)
        val raw = 10.0.pow(exponent)
        return raw.coerceIn(minDistanceM, maxDistanceM)
    }
}
