package com.waytogo.core

import com.waytogo.core.distance.DistanceEstimator
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceEstimatorTest {

    @Test
    fun atReferenceRssiReturnsOneMeter() {
        val d = DistanceEstimator.estimate(
            filteredRssi = -59.0, rssi1m = -59,
            pathLossExponent = 2.5, minDistanceM = 0.5, maxDistanceM = 25.0,
        )
        assertEquals(1.0, d, 1e-9)
    }

    @Test
    fun twentyDbBelowWithN2ReturnsTenMeters() {
        val d = DistanceEstimator.estimate(
            filteredRssi = -79.0, rssi1m = -59,
            pathLossExponent = 2.0, minDistanceM = 0.5, maxDistanceM = 100.0,
        )
        assertEquals(10.0, d, 1e-9)
    }

    @Test
    fun clampsToMax() {
        val d = DistanceEstimator.estimate(
            filteredRssi = -110.0, rssi1m = -59,
            pathLossExponent = 2.5, minDistanceM = 0.5, maxDistanceM = 25.0,
        )
        assertEquals(25.0, d, 1e-9)
    }

    @Test
    fun clampsToMin() {
        val d = DistanceEstimator.estimate(
            filteredRssi = -20.0, rssi1m = -59,
            pathLossExponent = 2.5, minDistanceM = 0.5, maxDistanceM = 25.0,
        )
        assertEquals(0.5, d, 1e-9)
    }
}
