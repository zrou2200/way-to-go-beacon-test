package com.waytogo.core

import com.waytogo.core.model.BeaconKey
import com.waytogo.core.model.BeaconObservation
import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.model.RegisteredBeacon
import com.waytogo.core.position.FloorResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class FloorResolverTest {

    private val config = PositioningConfig() // floorSwitchHoldMs = 3000

    private var id = 0
    private fun obs(floor: Int, rssi: Double): BeaconObservation {
        val b = RegisteredBeacon(BeaconKey("U", floor, id++), "B", floor, 0.0, 0.0, -59)
        return BeaconObservation(b, rssi, 2.0, 3, 0)
    }

    private fun floorObs(floor: Int) =
        listOf(obs(floor, -60.0), obs(floor, -62.0), obs(floor, -64.0))

    @Test
    fun firstFixSetsFloorImmediately() {
        val r = FloorResolver(config)
        assertEquals(2, r.resolve(floorObs(2), 0))
    }

    @Test
    fun doesNotSwitchBeforeHoldThenSwitchesAfter() {
        val r = FloorResolver(config)
        assertEquals(1, r.resolve(floorObs(1), 0))
        // Candidate is floor 2 continuously, but hold is 3000 ms.
        assertEquals(1, r.resolve(floorObs(2), 1000))
        assertEquals(1, r.resolve(floorObs(2), 2000))
        assertEquals(1, r.resolve(floorObs(2), 3500)) // 3500-1000 = 2500 < 3000
        assertEquals(2, r.resolve(floorObs(2), 4000)) // 4000-1000 = 3000 >= 3000
    }

    @Test
    fun flappingCandidateResetsHold() {
        val r = FloorResolver(config)
        assertEquals(1, r.resolve(floorObs(1), 0))
        assertEquals(1, r.resolve(floorObs(2), 1000)) // pending 2 since 1000
        assertEquals(1, r.resolve(floorObs(1), 2000)) // back to 1, pending cleared
        assertEquals(1, r.resolve(floorObs(2), 2500)) // pending 2 since 2500
        assertEquals(1, r.resolve(floorObs(2), 4000)) // 4000-2500 = 1500 < 3000
    }
}
