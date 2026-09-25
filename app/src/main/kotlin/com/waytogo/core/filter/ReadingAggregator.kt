package com.waytogo.core.filter

import com.waytogo.core.distance.DistanceEstimator
import com.waytogo.core.model.BeaconKey
import com.waytogo.core.model.BeaconObservation
import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.model.RawReading
import com.waytogo.core.model.RegisteredBeacon

/**
 * Per-beacon windowed RSSI filtering (Section 5.1).
 *
 * - Keeps a sliding window of (timestampMs, rssi) per beacon key.
 * - Filtered RSSI is the median over the window (robust to outliers).
 * - Beacons with fewer than [PositioningConfig.minSamplesPerBeacon] samples are excluded.
 * - Readings with rssi >= 0 or rssi < -110 are ignored as invalid/sentinel.
 * - Only beacons present in the supplied resolver (known + active) produce observations.
 *
 * Not thread-safe; call from a single positioning dispatcher.
 */
class ReadingAggregator(
    private val config: PositioningConfig,
    private val lookup: (BeaconKey) -> RegisteredBeacon?,
) {
    private data class Sample(val timestampMs: Long, val rssi: Int)

    private val windows = HashMap<BeaconKey, ArrayDeque<Sample>>()

    fun add(reading: RawReading) {
        if (reading.rssi >= 0 || reading.rssi < -110) return
        if (lookup(reading.key) == null) return
        val window = windows.getOrPut(reading.key) { ArrayDeque() }
        window.addLast(Sample(reading.timestampMs, reading.rssi))
    }

    /**
     * Produce observations for the current tick. Expires samples older than
     * windowMs and drops beacons unseen for longer than staleMs.
     */
    fun observations(nowMs: Long): List<BeaconObservation> {
        val result = ArrayList<BeaconObservation>()
        val cutoff = nowMs - config.windowMs
        val staleCutoff = nowMs - config.staleMs

        val iterator = windows.entries.iterator()
        while (iterator.hasNext()) {
            val (key, window) = iterator.next()
            while (window.isNotEmpty() && window.first().timestampMs < cutoff) {
                window.removeFirst()
            }
            val lastSeen = window.lastOrNull()?.timestampMs
            if (lastSeen == null || lastSeen < staleCutoff) {
                iterator.remove()
                continue
            }
            if (window.size < config.minSamplesPerBeacon) continue

            val beacon = lookup(key) ?: continue
            val filteredRssi = median(window.map { it.rssi })
            val distance = DistanceEstimator.estimate(
                filteredRssi = filteredRssi,
                rssi1m = beacon.rssi1m,
                pathLossExponent = config.pathLossExponent,
                minDistanceM = config.minDistanceM,
                maxDistanceM = config.maxDistanceM,
            )
            result.add(
                BeaconObservation(
                    beacon = beacon,
                    filteredRssi = filteredRssi,
                    distanceM = distance,
                    sampleCount = window.size,
                    lastSeenMs = lastSeen,
                )
            )
        }
        return result
    }

    fun reset() = windows.clear()

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2].toDouble()
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        }
    }
}
