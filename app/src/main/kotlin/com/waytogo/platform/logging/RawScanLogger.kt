package com.waytogo.platform.logging

import android.content.Context
import com.waytogo.core.model.RawReading
import com.waytogo.platform.sim.ScanCsv
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Writes every [RawReading] to a CSV in app-private storage (Section 8),
 * with a size cap (default 20 MB) and single-generation rotation. Toggleable
 * from the debug menu; the current file can be shared/exported.
 */
class RawScanLogger(
    context: Context,
    private val maxBytes: Long = 20L * 1024 * 1024,
) {
    private val dir = File(context.filesDir, "scan_logs").apply { mkdirs() }
    private val current = File(dir, "scan.csv")
    private val rotated = File(dir, "scan-1.csv")

    private val enabled = AtomicBoolean(false)
    private val lock = Any()

    fun isEnabled(): Boolean = enabled.get()

    fun setEnabled(value: Boolean) {
        enabled.set(value)
    }

    fun log(reading: RawReading) {
        if (!enabled.get()) return
        synchronized(lock) {
            if (!current.exists()) {
                current.writeText(ScanCsv.HEADER + "\n")
            } else if (current.length() >= maxBytes) {
                rotate()
            }
            current.appendText(ScanCsv.format(reading) + "\n")
        }
    }

    private fun rotate() {
        if (rotated.exists()) rotated.delete()
        current.renameTo(rotated)
        current.writeText(ScanCsv.HEADER + "\n")
    }

    /** The file to export/share. Null if nothing has been logged yet. */
    fun currentFile(): File? = synchronized(lock) { if (current.exists()) current else null }

    fun clear() {
        synchronized(lock) {
            current.delete()
            rotated.delete()
        }
    }
}
