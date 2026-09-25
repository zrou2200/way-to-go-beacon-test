package com.waytogo.core

import com.waytogo.core.model.BeaconKey
import com.waytogo.core.model.RawReading
import com.waytogo.platform.sim.ScanCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanCsvTest {

    @Test
    fun formatThenParseRoundTrips() {
        val r = RawReading(BeaconKey("E2C56DB5DFFB48D2B060D0F5A71096E0", 1, 2), -70, -59, 12345L)
        val parsed = ScanCsv.parse(ScanCsv.format(r))!!
        assertEquals(r, parsed)
    }

    @Test
    fun parsesMissingTxPowerAsNull() {
        val parsed = ScanCsv.parse("100,U,1,2,-70,")!!
        assertNull(parsed.advertisedTxPower)
        assertEquals(-70, parsed.rssi)
    }

    @Test
    fun ignoresHeaderAndMalformed() {
        assertNull(ScanCsv.parse(ScanCsv.HEADER))
        assertNull(ScanCsv.parse("garbage,row"))
        assertNull(ScanCsv.parse(""))
    }
}
