package com.waytogo.ui.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.waytogo.core.model.FloorPlan
import com.waytogo.core.model.PositionFix
import com.waytogo.core.model.RegisteredBeacon
import kotlin.math.min

/**
 * Renders a floor plan with pinch-zoom / pan (Section 7.1) and overlays: the
 * animated user marker with accuracy circle, plus optional debug beacon markers.
 */
@Composable
fun FloorPlanCanvas(
    floor: FloorPlan,
    image: ImageBitmap,
    fix: PositionFix?,
    dimmed: Boolean,
    debugVisible: Boolean,
    beacons: List<RegisteredBeacon>,
    heardKeys: Set<com.waytogo.core.model.BeaconKey>,
    usedKeys: Set<com.waytogo.core.model.BeaconKey>,
    onUserPan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var userScale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // Animate the user marker between fixes (300 ms).
    val animX by animateFloatAsState(
        targetValue = fix?.x?.toFloat() ?: 0f,
        animationSpec = tween(300),
        label = "userX",
    )
    val animY by animateFloatAsState(
        targetValue = fix?.y?.toFloat() ?: 0f,
        animationSpec = tween(300),
        label = "userY",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        userScale = (userScale * zoomChange).coerceIn(0.5f, 5f)
                        pan += panChange
                        onUserPan()
                    }
                },
        ) {
            val imgW = image.width.toFloat()
            val imgH = image.height.toFloat()
            val baseScale = min(size.width / imgW, size.height / imgH)
            val effScale = baseScale * userScale
            val baseOffset = Offset(
                (size.width - imgW * effScale) / 2f,
                (size.height - imgH * effScale) / 2f,
            )
            val total = baseOffset + pan
            val ppm = (imgW / floor.widthM).toFloat() // image px per meter

            // Floor image in transformed space.
            withTransform({
                translate(total.x, total.y)
                scale(effScale, effScale, pivot = Offset.Zero)
            }) {
                drawImage(image)
            }

            fun screen(xMeters: Float, yMeters: Float): Offset =
                total + Offset(xMeters * ppm * effScale, yMeters * ppm * effScale)

            if (debugVisible) {
                drawBeacons(beacons, heardKeys, usedKeys) { bx, by -> screen(bx, by) }
            }

            if (fix != null) {
                val center = screen(animX, animY)
                val accuracyRadius = (fix.accuracyM * ppm * effScale).toFloat()
                val markerColor = if (dimmed) Color(0x662196F3) else Color(0xFF2196F3)
                drawCircle(
                    color = markerColor.copy(alpha = if (dimmed) 0.10f else 0.20f),
                    radius = accuracyRadius,
                    center = center,
                )
                drawCircle(color = markerColor, radius = 10.dp.toPx(), center = center)
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                )
            }
        }
    }
}

private fun DrawScope.drawBeacons(
    beacons: List<RegisteredBeacon>,
    heardKeys: Set<com.waytogo.core.model.BeaconKey>,
    usedKeys: Set<com.waytogo.core.model.BeaconKey>,
    project: (Float, Float) -> Offset,
) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 26f
        isAntiAlias = true
    }
    for (b in beacons) {
        val p = project(b.x.toFloat(), b.y.toFloat())
        val color = when {
            b.key in usedKeys -> Color(0xFF43A047)  // used for positioning
            b.key in heardKeys -> Color(0xFFFB8C00) // heard
            else -> Color(0xFF9E9E9E)               // registered, not heard
        }
        drawCircle(color = color, radius = 7.dp.toPx(), center = p)
        drawContext.canvas.nativeCanvas.drawText(b.label, p.x + 12f, p.y + 8f, paint)
    }
}
