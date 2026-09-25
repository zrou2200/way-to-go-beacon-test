package com.waytogo.core.position

import com.waytogo.core.model.FloorPoint

/** A planned route between two floor points. Unimplemented in v1 (Section 5.6). */
data class Route(val points: List<FloorPoint>)

/** Routing extension point. No implementation ships in v1. */
interface Router {
    fun route(from: FloorPoint, to: FloorPoint): Route
}
