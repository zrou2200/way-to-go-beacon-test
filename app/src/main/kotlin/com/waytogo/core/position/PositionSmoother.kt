package com.waytogo.core.position

import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.model.PositionFix
import kotlin.math.hypot

/**
 * Temporal smoothing (Section 5.5): per-axis EMA with jump rejection.
 *
 * - The first fix (and the first fix after a floor change) passes through unchanged.
 * - Jump rejection caps the raw estimate to maxSpeedMps * dt away from the previous
 *   position before applying the EMA, so a wild reading nudges rather than teleports
 *   the dot.
 *
 * Stateful; call from the single positioning dispatcher.
 */
class PositionSmoother(private val config: PositioningConfig) {

    private var previous: PositionFix? = null

    fun smooth(fix: PositionFix): PositionFix {
        val prev = previous
        if (prev == null || prev.floorLevel != fix.floorLevel) {
            previous = fix
            return fix
        }

        val dt = (fix.timestampMs - prev.timestampMs) / 1000.0
        var targetX = fix.x
        var targetY = fix.y

        if (dt > 0) {
            val dx = fix.x - prev.x
            val dy = fix.y - prev.y
            val dist = hypot(dx, dy)
            val maxStep = config.maxSpeedMps * dt
            if (dist > maxStep && dist > 0) {
                val scale = maxStep / dist
                targetX = prev.x + dx * scale
                targetY = prev.y + dy * scale
            }
        }

        val a = config.smoothingAlpha
        val smoothedX = a * targetX + (1 - a) * prev.x
        val smoothedY = a * targetY + (1 - a) * prev.y

        val result = fix.copy(x = smoothedX, y = smoothedY)
        previous = result
        return result
    }

    fun reset() {
        previous = null
    }
}
