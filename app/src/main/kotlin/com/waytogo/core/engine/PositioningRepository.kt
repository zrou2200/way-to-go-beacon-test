package com.waytogo.core.engine

import com.waytogo.core.filter.ReadingAggregator
import com.waytogo.core.model.ErrorKind
import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.model.PositionFix
import com.waytogo.core.model.PositionState
import com.waytogo.core.model.RawReading
import com.waytogo.core.position.ClampToBoundsProcessor
import com.waytogo.core.position.FloorResolver
import com.waytogo.core.position.PositionEstimator
import com.waytogo.core.position.PositionPostProcessor
import com.waytogo.core.position.PositionSmoother
import com.waytogo.core.position.WeightedPositionEstimator
import com.waytogo.core.registry.RegistryRepository
import com.waytogo.core.scan.BeaconScanner
import com.waytogo.core.scan.Clock
import com.waytogo.core.scan.ScanException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the positioning pipeline (Section 3):
 *   readings -> aggregator -> floor resolver + estimator -> smoother -> post-processors
 * and emits a [PositionState] at ~[PositioningConfig.emitIntervalMs].
 *
 * All mutable filtering state is confined to a single ticker coroutine; incoming
 * readings are handed over through a channel so no locking is required and the
 * pipeline components stay single-threaded (as they assume).
 */
class PositioningRepository(
    private val registry: RegistryRepository,
    private val config: PositioningConfig,
    private val estimator: PositionEstimator = WeightedPositionEstimator(),
    private val postProcessors: List<PositionPostProcessor> = listOf(ClampToBoundsProcessor()),
    private val clock: Clock = Clock.SYSTEM,
    private val readingSink: ((RawReading) -> Unit)? = null,
) {
    private val _state = MutableStateFlow<PositionState>(PositionState.Idle)
    val state: StateFlow<PositionState> = _state.asStateFlow()

    private val _debug = MutableStateFlow(DebugSnapshot())
    val debug: StateFlow<DebugSnapshot> = _debug.asStateFlow()

    private val aggregator = ReadingAggregator(config) { key -> registry.beacon(key) }
    private val resolver = FloorResolver(config)
    private val smoother = PositionSmoother(config)

    private var lastFix: PositionFix? = null
    private var lastValidMs: Long = 0
    private var fatal = false

    /** Force an error state (e.g. permission denied, invalid registry). */
    fun onError(kind: ErrorKind) {
        fatal = true
        _state.value = PositionState.Error(kind)
    }

    /** Clear a forced error and resume searching. */
    fun clearError() {
        fatal = false
        _state.value = PositionState.Searching
    }

    /**
     * Start the pipeline against [scanner]. Returns a [Job] that stops scanning
     * and emission when cancelled.
     */
    fun start(scope: CoroutineScope, scanner: BeaconScanner): Job = scope.launch {
        reset()
        if (!fatal) _state.value = PositionState.Searching

        val channel = Channel<RawReading>(Channel.UNLIMITED)
        val readTimestamps = ArrayDeque<Long>()

        val collector = launch {
            try {
                scanner.readings().collect { reading ->
                    readingSink?.invoke(reading)
                    channel.trySend(reading)
                }
            } catch (e: ScanException) {
                fatal = true
                _state.value = PositionState.Error(mapReason(e.reason))
            } finally {
                channel.close()
            }
        }

        try {
            while (isActive) {
                delay(config.emitIntervalMs)
                // Drain everything the scanner produced since the last tick.
                while (true) {
                    val r = channel.tryReceive().getOrNull() ?: break
                    aggregator.add(r)
                    readTimestamps.addLast(r.timestampMs)
                }
                tick(readTimestamps)
            }
        } finally {
            collector.cancel()
        }
    }

    private fun tick(readTimestamps: ArrayDeque<Long>) {
        val now = clock.nowMs()

        // Scan rate = readings observed in the last second.
        while (readTimestamps.isNotEmpty() && readTimestamps.first() < now - 1000) {
            readTimestamps.removeFirst()
        }
        val scanRate = readTimestamps.size.toDouble()

        val observations = aggregator.observations(now)
        val floor = resolver.resolve(observations, now)

        val floorObs = if (floor != null) observations.filter { it.beacon.floorLevel == floor } else emptyList()
        val usedKeys = floorObs
            .sortedByDescending { it.filteredRssi }
            .take(config.maxBeaconsUsed)
            .map { it.beacon.key }
            .toSet()

        val plan = floor?.let { registry.floor(it) }
        var newFix: PositionFix? = null
        if (plan != null && floorObs.isNotEmpty()) {
            val raw = estimator.estimate(floorObs, plan, config, now)
            if (raw != null) {
                var smoothed = smoother.smooth(raw)
                for (pp in postProcessors) smoothed = pp.process(smoothed, plan)
                newFix = smoothed
            }
        }

        if (!fatal) {
            if (newFix != null) {
                lastFix = newFix
                lastValidMs = now
                _state.value = PositionState.Located(newFix)
            } else {
                val last = lastFix
                _state.value = when {
                    last == null -> PositionState.Searching
                    now - lastValidMs > config.positionStaleMs ->
                        PositionState.Degraded(last, "No recent beacon fix")
                    else -> PositionState.Located(last)
                }
            }
        }

        _debug.value = DebugSnapshot(
            heard = observations.sortedByDescending { it.filteredRssi },
            usedKeys = usedKeys,
            method = newFix?.method ?: lastFix?.method,
            accuracyM = newFix?.accuracyM ?: lastFix?.accuracyM,
            beaconsUsed = newFix?.beaconsUsed ?: 0,
            scanRateHz = scanRate,
            resolvedFloor = floor,
        )
    }

    private fun reset() {
        aggregator.reset()
        resolver.reset()
        smoother.reset()
        lastFix = null
        lastValidMs = 0
    }
}
