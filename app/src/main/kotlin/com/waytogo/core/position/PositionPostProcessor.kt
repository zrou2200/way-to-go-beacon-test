package com.waytogo.core.position

import com.waytogo.core.model.FloorPlan
import com.waytogo.core.model.PositionFix

/**
 * A post-processing step applied after the smoother (Section 5.6). Extension point
 * for a future walkable-area snapper or motion-sensor fusion step.
 */
fun interface PositionPostProcessor {
    fun process(fix: PositionFix, floor: FloorPlan): PositionFix
}

/** v1's only processor: clamp the position to the floor bounds. */
class ClampToBoundsProcessor : PositionPostProcessor {
    override fun process(fix: PositionFix, floor: FloorPlan): PositionFix =
        fix.copy(x = floor.clampX(fix.x), y = floor.clampY(fix.y))
}
