package com.waytogo.platform.sim

import com.waytogo.core.model.BeaconKey
import com.waytogo.core.model.RawReading

/** Shared CSV schema for raw-scan logs and replay (Sections 8 & 9). */
object ScanCsv {
    const val HEADER = "timestamp_ms,uuid,major,minor,rssi,adv_tx_power"

    fun format(reading: RawReading): String =
        "${reading.timestampMs},${reading.key.uuid},${reading.key.major}," +
            "${reading.key.minor},${reading.rssi},${reading.advertisedTxPower ?: ""}"

    /** Parses one data line; returns null for the header or malformed rows. */
    fun parse(line: String): RawReading? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("timestamp_ms")) return null
        val parts = trimmed.split(",")
        if (parts.size < 6) return null
        return try {
            val ts = parts[0].trim().toLong()
            val uuid = parts[1].trim().replace("-", "").uppercase()
            val major = parts[2].trim().toInt()
            val minor = parts[3].trim().toInt()
            val rssi = parts[4].trim().toInt()
            val txRaw = parts[5].trim()
            val tx = if (txRaw.isEmpty()) null else txRaw.toInt()
            RawReading(BeaconKey(uuid, major, minor), rssi, tx, ts)
        } catch (e: NumberFormatException) {
            null
        }
    }
}
