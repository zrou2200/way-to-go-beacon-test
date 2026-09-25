package com.waytogo.core.position

import com.waytogo.core.model.BeaconObservation
import com.waytogo.core.model.FloorPlan
import com.waytogo.core.model.Method
import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.model.PositionFix
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Default estimator (Section 5.4): proximity / weighted centroid / weighted
 * nonlinear least-squares trilateration with centroid fallback.
 */
class WeightedPositionEstimator : PositionEstimator {

    override fun estimate(
        observations: List<BeaconObservation>,
        floor: FloorPlan,
        config: PositioningConfig,
        nowMs: Long,
    ): PositionFix? {
        val used = observations
            .sortedByDescending { it.filteredRssi }
            .take(config.maxBeaconsUsed)

        return when (used.size) {
            0 -> null
            1 -> proximity(used[0], floor, config, nowMs)
            2 -> centroidFix(used, floor, config, nowMs)
            else -> trilaterate(used, floor, config, nowMs)
                ?: centroidFix(used, floor, config, nowMs)
        }
    }

    private fun proximity(
        obs: BeaconObservation,
        floor: FloorPlan,
        config: PositioningConfig,
        nowMs: Long,
    ): PositionFix {
        // Place user at the measured distance from the beacon (East direction as default bearing)
        val x = floor.clampX(obs.beacon.x + obs.distanceM)
        val y = floor.clampY(obs.beacon.y)
        val accuracy = maxOf(config.minAccuracyM, obs.distanceM * 0.5)
        return PositionFix(
            floorLevel = floor.level,
            x = x,
            y = y,
            accuracyM = accuracy,
            beaconsUsed = 1,
            method = Method.PROXIMITY,
            timestampMs = nowMs,
        )
    }

    private fun centroidFix(
        used: List<BeaconObservation>,
        floor: FloorPlan,
        config: PositioningConfig,
        nowMs: Long,
    ): PositionFix {
        val (cx, cy) = weightedCentroid(used, config.centroidWeightPower)
        // Accuracy = weighted mean distance to used beacons * 0.5.
        var wSum = 0.0
        var wdSum = 0.0
        for (o in used) {
            val w = weight(o.distanceM, config.centroidWeightPower)
            wSum += w
            wdSum += w * o.distanceM
        }
        val weightedMeanDistance = if (wSum > 0) wdSum / wSum else config.maxDistanceM
        val accuracy = maxOf(config.minAccuracyM, weightedMeanDistance * 0.5)
        return PositionFix(
            floorLevel = floor.level,
            x = floor.clampX(cx),
            y = floor.clampY(cy),
            accuracyM = accuracy,
            beaconsUsed = used.size,
            method = Method.CENTROID,
            timestampMs = nowMs,
        )
    }

    private fun trilaterate(
        used: List<BeaconObservation>,
        floor: FloorPlan,
        config: PositioningConfig,
        nowMs: Long,
    ): PositionFix? {
        val (startX, startY) = weightedCentroid(used, config.centroidWeightPower)
        var x = startX
        var y = startY
        var converged = false

        repeat(10) {
            var a00 = 0.0; var a01 = 0.0; var a11 = 0.0
            var b0 = 0.0; var b1 = 0.0
            for (o in used) {
                val dx = x - o.beacon.x
                val dy = y - o.beacon.y
                val dist = hypot(dx, dy)
                if (dist < 1e-6) return@repeat
                val jx = dx / dist
                val jy = dy / dist
                val r = dist - o.distanceM
                val w = 1.0 / (o.distanceM * o.distanceM)
                a00 += w * jx * jx
                a01 += w * jx * jy
                a11 += w * jy * jy
                b0 += w * jx * r
                b1 += w * jy * r
            }
            val det = a00 * a11 - a01 * a01
            if (kotlin.math.abs(det) < 1e-12) return null // singular -> fallback
            val stepX = (-a11 * b0 + a01 * b1) / det
            val stepY = (a01 * b0 - a00 * b1) / det
            x += stepX
            y += stepY
            if (hypot(stepX, stepY) < 0.01) {
                converged = true
                return@repeat
            }
        }

        if (!converged) return null
        if (x.isNaN() || y.isNaN() || x.isInfinite() || y.isInfinite()) return null

        // Reject solutions far outside the bounding box of the used beacons.
        val minX = used.minOf { it.beacon.x }
        val maxX = used.maxOf { it.beacon.x }
        val minY = used.minOf { it.beacon.y }
        val maxY = used.maxOf { it.beacon.y }
        if (x < minX - 5 || x > maxX + 5 || y < minY - 5 || y > maxY + 5) return null

        // Residual RMS for accuracy.
        var sq = 0.0
        for (o in used) {
            val r = hypot(x - o.beacon.x, y - o.beacon.y) - o.distanceM
            sq += r * r
        }
        val rms = sqrt(sq / used.size)
        val accuracy = maxOf(config.minAccuracyM, rms)

        return PositionFix(
            floorLevel = floor.level,
            x = floor.clampX(x),
            y = floor.clampY(y),
            accuracyM = accuracy,
            beaconsUsed = used.size,
            method = Method.TRILATERATION,
            timestampMs = nowMs,
        )
    }

    private fun weightedCentroid(
        used: List<BeaconObservation>,
        power: Double,
    ): Pair<Double, Double> {
        var wSum = 0.0
        var xSum = 0.0
        var ySum = 0.0
        for (o in used) {
            val w = weight(o.distanceM, power)
            wSum += w
            xSum += w * o.beacon.x
            ySum += w * o.beacon.y
        }
        if (wSum <= 0.0) {
            return Pair(used.first().beacon.x, used.first().beacon.y)
        }
        return Pair(xSum / wSum, ySum / wSum)
    }

    private fun weight(distance: Double, power: Double): Double {
        val d = distance.coerceAtLeast(1e-3)
        return 1.0 / d.pow(power)
    }
}