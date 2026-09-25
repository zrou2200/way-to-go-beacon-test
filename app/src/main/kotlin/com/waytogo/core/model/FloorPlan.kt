package com.waytogo.core.model

/** A point on a specific floor, used by extension interfaces (routing, snapping). */
data class FloorPoint(val floorLevel: Int, val x: Double, val y: Double)

/** Metadata + pixel mapping for one floor plan image. */
data class FloorPlan(
    val level: Int,
    val name: String,
    val image: String,
    val widthM: Double,
    val heightM: Double,
) {
    fun contains(x: Double, y: Double): Boolean =
        x in 0.0..widthM && y in 0.0..heightM

    fun clampX(x: Double): Double = x.coerceIn(0.0, widthM)
    fun clampY(y: Double): Double = y.coerceIn(0.0, heightM)
}
