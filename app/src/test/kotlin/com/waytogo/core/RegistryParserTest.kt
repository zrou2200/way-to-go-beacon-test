package com.waytogo.core

import com.waytogo.core.model.BeaconKey
import com.waytogo.core.registry.RegistryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryParserTest {

    private val floorsJson = """
        { "floors": [ { "level": 1, "name": "G", "image": "f1.png", "width_m": 60.0, "height_m": 40.0 } ] }
    """.trimIndent()

    @Test
    fun skipsInvalidEntriesButKeepsValidOnes() {
        val registryJson = """
        {
          "registry_version": "t",
          "uuid": "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
          "beacons": [
            { "major": 1, "minor": 1, "label": "OK",   "floor_level": 1, "x": 5.0,   "y": 5.0, "rssi_1m": -59, "status": "active" },
            { "major": 1, "minor": 1, "label": "DUP",  "floor_level": 1, "x": 6.0,   "y": 6.0, "rssi_1m": -59, "status": "active" },
            { "major": 1, "minor": 2, "label": "MISS", "floor_level": 1, "x": 7.0,   "y": 7.0, "status": "active" },
            { "major": 1, "minor": 3, "label": "OOB",  "floor_level": 1, "x": 100.0, "y": 5.0, "rssi_1m": -59, "status": "active" },
            { "major": 1, "minor": 4, "label": "OFF",  "floor_level": 1, "x": 8.0,   "y": 8.0, "rssi_1m": -59, "status": "inactive" },
            { "major": 9, "minor": 1, "label": "NOFL", "floor_level": 9, "x": 1.0,   "y": 1.0, "rssi_1m": -59, "status": "active" }
          ]
        }
        """.trimIndent()

        val load = RegistryParser.parse(registryJson, floorsJson)
        val repo = load.repository
        assertNotNull(repo)
        repo!!
        // Only the single fully-valid, active, unique, in-bounds beacon survives.
        assertEquals(1, repo.activeBeacons().size)
        val key = BeaconKey("E2C56DB5DFFB48D2B060D0F5A71096E0", 1, 1)
        assertEquals("OK", repo.beacon(key)?.label)
        // Warnings recorded for duplicate, missing, out-of-bounds, and unknown floor.
        assertTrue(load.warnings.size >= 4)
    }

    @Test
    fun missingUuidIsFatal() {
        val registryJson = """{ "beacons": [] }"""
        val load = RegistryParser.parse(registryJson, floorsJson)
        assertNull(load.repository)
        assertFalse(load.isValid)
        assertNotNull(load.fatalError)
    }

    @Test
    fun unparseableRegistryIsFatalNotCrash() {
        val load = RegistryParser.parse("{ not json", floorsJson)
        assertNull(load.repository)
        assertNotNull(load.fatalError)
    }
}
