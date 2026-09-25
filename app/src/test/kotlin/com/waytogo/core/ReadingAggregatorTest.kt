package com.waytogo.core

import com.waytogo.core.filter.ReadingAggregator
import com.waytogo.core.model.BeaconKey
import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.model.RawReading
import com.waytogo.core.model.RegisteredBeacon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingAggregatorTest {

    private val key = BeaconKey("U", 1, 1)
    private val beacon = RegisteredBeacon(key, "B1", 1, 5.0, 5.0, -59)
    private val config = PositioningConfig()
    private fun aggregator() = ReadingAggregator(config) { if (it == key) beacon else null }

    private fun reading(rssi: Int, ts: Long) = RawReading(key, rssi, null, ts)

    @Test
    fun medianIgnoresOutliers() {
        val agg = aggregator()
        agg.add(reading(-60, 1000))
        agg.add(reading(-61, 1100))
        agg.add(reading(-59, 1200))
        agg.add(reading(-100, 1300)) // outlier
        val obs = agg.observations(1400).single()
        // sorted: -100,-61,-60,-59 -> median of middle two = -60.5
        assertEquals(-60.5, obs.filteredRssi, 1e-9)
        assertEquals(4, obs.sampleCount)
    }

    @Test
    fun expiresStaleSamplesFromWindow() {
        val agg = aggregator()
        agg.add(reading(-60, 0))     // older than windowMs at now=4000
        agg.add(reading(-62, 3500))
        agg.add(reading(-64, 3600))
        val obs = agg.observations(4000).single()
        assertEquals(2, obs.sampleCount) // the t=0 sample expired (window 3000)
        assertEquals(-63.0, obs.filteredRssi, 1e-9)
    }

    @Test
    fun excludesBeaconWithTooFewSamples() {
        val agg = aggregator()
        agg.add(reading(-60, 1000)) // only one sample, min is 2
        assertTrue(agg.observations(1100).isEmpty())
    }

    @Test
    fun dropsBeaconUnseenLongerThanStale() {
        val agg = aggregator()
        agg.add(reading(-60, 0))
        agg.add(reading(-61, 100))
        // now far beyond staleMs (5000)
        assertTrue(agg.observations(10_000).isEmpty())
    }

    @Test
    fun ignoresInvalidRssi() {
        val agg = aggregator()
        agg.add(reading(0, 1000))    // >= 0 invalid
        agg.add(reading(-120, 1050)) // < -110 invalid
        agg.add(reading(-60, 1100))
        agg.add(reading(-62, 1150))
        val obs = agg.observations(1200).single()
        assertEquals(2, obs.sampleCount)
    }

    @Test
    fun ignoresUnknownBeacon() {
        val agg = aggregator()
        agg.add(RawReading(BeaconKey("U", 9, 9), -60, null, 1000))
        agg.add(RawReading(BeaconKey("U", 9, 9), -61, null, 1100))
        assertTrue(agg.observations(1200).isEmpty())
    }
}
