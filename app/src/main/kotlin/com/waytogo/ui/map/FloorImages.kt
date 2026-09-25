package com.waytogo.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.waytogo.core.model.FloorPlan
import kotlin.math.abs

/** Loads floor-plan bitmaps from assets and validates pixels-per-meter (Section 2.3). */
object FloorImages {

    private val cache = HashMap<String, ImageBitmap?>()

    fun load(context: Context, floor: FloorPlan): ImageBitmap? {
        cache[floor.image]?.let { return it }
        val bitmap = try {
            context.assets.open(floor.image).use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.w("FloorImages", "Could not load floor image ${floor.image}: ${e.message}")
            null
        }
        if (bitmap != null) {
            val ppmX = bitmap.width / floor.widthM
            val ppmY = bitmap.height / floor.heightM
            if (abs(ppmX - ppmY) / maxOf(ppmX, ppmY) > 0.02) {
                Log.w(
                    "FloorImages",
                    "Floor ${floor.level}: pixels_per_meter differs by axis " +
                        "(x=$ppmX, y=$ppmY); map scaling may be distorted.",
                )
            }
        }
        val image = bitmap?.asImageBitmap()
        cache[floor.image] = image
        return image
    }

    /** Pixels-per-meter for a loaded image (uses the x axis as reference). */
    fun pixelsPerMeter(image: ImageBitmap, floor: FloorPlan): Float =
        (image.width / floor.widthM).toFloat()
}
