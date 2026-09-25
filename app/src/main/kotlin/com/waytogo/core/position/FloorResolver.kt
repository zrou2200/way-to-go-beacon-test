package com.waytogo.core.position

import com.waytogo.core.model.BeaconObservation
import com.waytogo.core.model.PositioningConfig

/**
 * Resolves the reported floor from beacon observations (Section 5.3).
 *
 * - Candidate floor = majority floorLevel among the top 3 beacons by filtered RSSI,
 *   ties broken by the single strongest beacon.
 * - Hysteresis: the reported floor only changes after the candidate differs from the
 *   current floor continuously for at least [PositioningConfig.floorSwitchHoldMs].
 * - The very first resolution sets the floor immediately.
 *
 * Stateful; call from the single positioning dispatcher.
 */
class FloorResolver(private val config: PositioningConfig) {

    var currentFloor: Int? = null
        private set

    private var pendingFloor: Int? = null
    private var pendingSinceMs: Long = 0

    /** Returns the currently reported floor, or null if none has been established. */
    fun resolve(observations: List<BeaconObservation>, nowMs: Long): Int? {
        val candidate = candidateFloor(observations) ?: return currentFloor

        val current = currentFloor
        if (current == null) {
            currentFloor = candidate
            pendingFloor = null
            return currentFloor
        }

        if (candidate == current) {
            pendingFloor = null
            return current
        }

        if (pendingFloor != candidate) {
            pendingFloor = candidate
            pendingSinceMs = nowMs
        }
        if (nowMs - pendingSinceMs >= config.floorSwitchHoldMs) {
            currentFloor = candidate
            pendingFloor = null
        }
        return currentFloor
    }

    fun reset() {
        currentFloor = null
        pendingFloor = null
        pendingSinceMs = 0
    }

    private fun candidateFloor(observations: List<BeaconObservation>): Int? {
        if (observations.isEmpty()) return null
        val top = observations.sortedByDescending { it.filteredRssi }.take(3)

        val counts = LinkedHashMap<Int, Int>()
        for (obs in top) {
            val floor = obs.beacon.floorLevel
            counts[floor] = (counts[floor] ?: 0) + 1
        }
        val maxCount = counts.values.max()
        val leaders = counts.filterValues { it == maxCount }.keys
        // Unique majority, or tie resolved by the strongest beacon (top[0]).
        return if (leaders.size == 1) leaders.first() else top.first().beacon.floorLevel
    }
}
