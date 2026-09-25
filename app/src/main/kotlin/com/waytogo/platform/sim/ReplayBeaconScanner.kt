package com.waytogo.platform.sim

import android.os.SystemClock
import com.waytogo.core.model.RawReading
import com.waytogo.core.scan.BeaconScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Replays a CSV log (same schema as [ScanCsv]) as a [BeaconScanner], preserving
 * the original relative timing scaled by [speed] (Section 9). Lets the app be
 * developed and demoed without hardware.
 *
 * @param linesProvider yields raw CSV lines (from an asset or a picked file).
 * @param speed playback multiplier (1x, 2x, 4x, ...).
 * @param loop restart from the top when the file ends.
 */
class ReplayBeaconScanner(
    private val linesProvider: () -> Sequence<String>,
    private val speed: Double = 1.0,
    private val loop: Boolean = true,
) : BeaconScanner {

    override fun readings(): Flow<RawReading> = flow {
        val safeSpeed = if (speed <= 0) 1.0 else speed
        do {
            var previousTs: Long? = null
            for (line in linesProvider()) {
                val parsed = ScanCsv.parse(line) ?: continue
                val prev = previousTs
                if (prev != null) {
                    val gapMs = ((parsed.timestampMs - prev) / safeSpeed).toLong()
                    if (gapMs > 0) delay(gapMs)
                }
                previousTs = parsed.timestampMs
                // Re-timestamp against the monotonic clock so windowing stays consistent.
                emit(parsed.copy(timestampMs = SystemClock.elapsedRealtime()))
            }
        } while (loop)
    }
}
