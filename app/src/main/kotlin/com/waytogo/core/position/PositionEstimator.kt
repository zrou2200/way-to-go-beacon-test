package com.waytogo.core.position

import com.waytogo.core.model.BeaconObservation
import com.waytogo.core.model.FloorPlan
import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.model.PositionFix

/**
 * Estimates an (x, y) position from beacon observations on a single floor.
 * Implemented as an interface so alternative estimators (e.g. a future
 * FingerprintEstimator, Section 5.6) can be swapped in.
 */
interface PositionEstimator {
    /**
     * @param observations observations already restricted to [floor].
     * @return a [PositionFix], or null if no fix is possible (0 usable beacons).
     */
    fun estimate(
        observations: List<BeaconObservation>,
        floor: FloorPlan,
        config: PositioningConfig,
        nowMs: Long,
    ): PositionFix?
}
