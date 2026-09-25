package com.waytogo.core

import com.waytogo.core.model.BeaconKey
import com.waytogo.core.model.BeaconObservation
import com.waytogo.core.model.FloorPlan
import com.waytogo.core.model.Method
import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.model.RegisteredBeacon
import com.waytogo.core.position.WeightedPositionEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PositionEstimatorTest {

    private val config = PositioningConfig()
    private val floor = FloorPlan(1, "F1", "f1.png", 60.0, 40.0)
    private val estimator = WeightedPositionEstimator()

    private var seq = 0
    private fun obs(x: Double, y: Double, distance: Double): BeaconObservation {
        val id = seq++
        val beacon = RegisteredBeacon(BeaconKey("U", 1, id), "B$id", 1, x, y, -59)
        // filteredRssi only used for ordering; make nearer beacons "stronger".
        return BeaconObservation(beacon, filteredRssi = -distance, distanceM = distance, sampleCount = 3, lastSeenMs = 0)
    }

    @Test
    fun trilaterationRecoversTruePositionNoiseFree() {
        val tx = 3.0
        val ty = 4.0
        val corners = listOf(0.0 to 0.0, 10.0 to 0.0, 0.0 to 10.0, 10.0 to 10.0)
        val observations = corners.map { (bx, by) ->
            obs(bx, by, hypot(tx - bx, ty - by))
        }
        val fix = estimator.estimate(observations, floor, config, 0)!!
        assertEquals(Method.TRILATERATION, fix.method)
        assertEquals(tx, fix.x, 0.1)
        assertEquals(ty, fix.y, 0.1)
    }

    @Test
    fun singleBeaconReturnsItsPosition() {
        val fix = estimator.estimate(listOf(obs(7.0, 8.0, 2.0)), floor, config, 0)!!
        assertEquals(Method.PROXIMITY, fix.method)
        assertEquals(7.0, fix.x, 1e-9)
        assertEquals(8.0, fix.y, 1e-9)
    }

    @Test
    fun twoBeaconsLieOnSegmentBetweenThem() {
        val a = obs(0.0, 0.0, 5.0)
        val b = obs(10.0, 0.0, 5.0)
        val fix = estimator.estimate(listOf(a, b), floor, config, 0)!!
        assertEquals(Method.CENTROID, fix.method)
        assertTrue(fix.x in 0.0..10.0)
        assertEquals(0.0, fix.y, 1e-9) // colinear on the x-axis
    }

    @Test
    fun inconsistentInputFallsBackToCentroidWithoutThrowing() {
        // Three close beacons with impossibly large, contradictory distances.
        val observations = listOf(
            obs(0.0, 0.0, 20.0),
            obs(1.0, 0.0, 20.0),
            obs(0.0, 1.0, 20.0),
        )
        val fix = estimator.estimate(observations, floor, config, 0)!!
        assertEquals(Method.CENTROID, fix.method)
        assertTrue(fix.x in 0.0..floor.widthM)
        assertTrue(fix.y in 0.0..floor.heightM)
    }
}
