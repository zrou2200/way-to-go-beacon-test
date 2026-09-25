package com.waytogo.platform.ble

import com.waytogo.core.model.BeaconKey
import java.util.Locale

/** Result of parsing an iBeacon manufacturer-data payload. */
data class IBeaconFrame(
    val uuid: String,
    val major: Int,
    val minor: Int,
    val txPower: Int,
)

/**
 * Parses the Apple iBeacon layout (Section 6.3) from the manufacturer-specific
 * data for company id 0x004C (the bytes *after* the two company-id bytes).
 * Returns null for anything malformed.
 *
 * | Offset | Len | Field                          |
 * |--------|-----|--------------------------------|
 * | 0      | 1   | Type = 0x02                    |
 * | 1      | 1   | Length = 0x15 (21)             |
 * | 2      | 16  | Proximity UUID (big-endian)    |
 * | 18     | 2   | Major (big-endian, unsigned)   |
 * | 20     | 2   | Minor (big-endian, unsigned)   |
 * | 22     | 1   | Measured TX power (signed dBm) |
 */
object IBeaconParser {

    private const val TYPE_IBEACON = 0x02
    private const val LENGTH_IBEACON = 0x15
    private const val MIN_LENGTH = 23

    fun parse(data: ByteArray?): IBeaconFrame? {
        if (data == null || data.size < MIN_LENGTH) return null
        if ((data[0].toInt() and 0xFF) != TYPE_IBEACON) return null
        if ((data[1].toInt() and 0xFF) != LENGTH_IBEACON) return null

        val uuid = formatUuid(data, 2)
        val major = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
        val minor = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
        val txPower = data[22].toInt() // signed

        return IBeaconFrame(uuid = uuid, major = major, minor = minor, txPower = txPower)
    }

    /** Convenience: parse into a [BeaconKey] (uuid uppercased, hyphenated). */
    fun parseKey(data: ByteArray?): Pair<BeaconKey, Int>? {
        val frame = parse(data) ?: return null
        return BeaconKey(frame.uuid, frame.major, frame.minor) to frame.txPower
    }

    private fun formatUuid(data: ByteArray, offset: Int): String {
        val hex = StringBuilder(36)
        for (i in 0 until 16) {
            hex.append(String.format(Locale.US, "%02X", data[offset + i].toInt() and 0xFF))
            when (i) {
                3, 5, 7, 9 -> hex.append('-')
            }
        }
        return hex.toString()
    }
}
