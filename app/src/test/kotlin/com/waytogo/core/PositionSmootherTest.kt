package com.waytogo.core

import com.waytogo.core.model.Method
import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.model.PositionFix
import com.waytogo.core.position.PositionSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PositionSmootherTest {

    private val config = PositioningConfig() // maxSpeedMps = 2.5

    private fun fix(x: Double, y: Double, floor: Int, ts: Long) =
        PositionFix(floor, x, y, 2.0, 3, Method.CENTROID, ts)

    @Test
    fun firstFixPassesThrough() {
        val s = PositionSmoother(config)
        val f = fix(10.0, 5.0, 1, 0)
        assertEquals(f, s.smooth(f))
    }

    @Test
    fun jumpIsLimitedToMaxSpeedTimesDt() {
        val s = PositionSmoother(config)
        val prev = fix(0.0, 0.0, 1, 0)
        s.smooth(prev)
        val jumped = fix(50.0, 0.0, 1, 1000) // dt = 1s, 50 m jump
        val out = s.smooth(jumped)
        val moved = hypot(out.x - prev.x, out.y - prev.y)
        // Clamped to maxSpeed*dt = 2.5 m (EMA keeps it at or below that).
        assertTrue("moved=$moved", moved <= 2.5 + 1e-6)
    }

    @Test
    fun smoothingResetsOnFloorChange() {
        val s = PositionSmoother(config)
        s.smooth(fix(0.0, 0.0, 1, 0))
        val onFloor2 = fix(40.0, 20.0, 2, 1000)
        val out = s.smooth(onFloor2)
        // No smoothing across floors: passes through unchanged.
        assertEquals(40.0, out.x, 1e-9)
        assertEquals(20.0, out.y, 1e-9)
    }
}
