package com.waytogo.core

import com.waytogo.platform.ble.IBeaconParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IBeaconParserTest {

    private fun bytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        return ByteArray(clean.length / 2) {
            clean.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    @Test
    fun parsesTestVector() {
        val data = bytes("02 15 E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0 00 02 00 11 C5")
        val frame = IBeaconParser.parse(data)!!
        assertEquals("E2C56DB5-DFFB-48D2-B060-D0F5A71096E0", frame.uuid)
        assertEquals(2, frame.major)
        assertEquals(17, frame.minor)
        assertEquals(-59, frame.txPower)
    }

    @Test
    fun wrongTypeByteReturnsNull() {
        val data = bytes("01 15 E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0 00 02 00 11 C5")
        assertNull(IBeaconParser.parse(data))
    }

    @Test
    fun wrongLengthByteReturnsNull() {
        val data = bytes("02 14 E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0 00 02 00 11 C5")
        assertNull(IBeaconParser.parse(data))
    }

    @Test
    fun truncatedPayloadReturnsNull() {
        val data = bytes("02 15 E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0 00 02")
        assertNull(IBeaconParser.parse(data))
    }

    @Test
    fun nullPayloadReturnsNull() {
        assertNull(IBeaconParser.parse(null))
    }
}
