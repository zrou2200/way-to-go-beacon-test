package com.waytogo.di

import android.content.Context
import com.waytogo.core.engine.PositioningRepository
import com.waytogo.core.model.PositioningConfig
import com.waytogo.core.registry.PositioningConfigParser
import com.waytogo.core.registry.RegistryLoad
import com.waytogo.core.registry.RegistryParser
import com.waytogo.core.scan.BeaconScanner
import com.waytogo.core.scan.Clock
import com.waytogo.platform.ble.AndroidBeaconScanner
import com.waytogo.platform.logging.RawScanLogger
import com.waytogo.platform.sim.ReplayBeaconScanner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Scanner source selection, toggled from the debug menu (Section 9). */
sealed interface ScannerMode {
    data object Live : ScannerMode
    data class Replay(val speed: Double = 1.0, val asset: String = "sample_walk.csv") : ScannerMode
}

/**
 * Manual dependency container. Loads and validates the bundled assets once,
 * wires the positioning pipeline, and provides scanner implementations.
 */
class AppContainer(private val context: Context) {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val positioningDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    val config: PositioningConfig = PositioningConfigParser.parse(
        readAssetOrNull("positioning_config.json")
    )

    val registryLoad: RegistryLoad = loadRegistry()

    val logger: RawScanLogger = RawScanLogger(context)

    /** Monotonic clock backed by SystemClock.elapsedRealtime via reflection-free wrapper. */
    val clock: Clock = Clock { android.os.SystemClock.elapsedRealtime() }

    fun createRepository(): PositioningRepository? {
        val repo = registryLoad.repository ?: return null
        return PositioningRepository(
            registry = repo,
            config = config,
            clock = clock,
            readingSink = { logger.log(it) },
        )
    }

    fun createScanner(mode: ScannerMode): BeaconScanner = when (mode) {
        is ScannerMode.Live -> AndroidBeaconScanner(
            context = context,
            registryUuid = registryLoad.repository?.uuid ?: "",
        )
        is ScannerMode.Replay -> ReplayBeaconScanner(
            linesProvider = { readAssetLines(mode.asset) },
            speed = mode.speed,
        )
    }

    private fun loadRegistry(): RegistryLoad {
        val registryJson = readAssetOrNull("beacon_registry.json")
        val floorsJson = readAssetOrNull("floors.json")
        if (registryJson == null || floorsJson == null) {
            return RegistryLoad(null, emptyList(), "Missing registry or floors asset.")
        }
        return RegistryParser.parse(registryJson, floorsJson)
    }

    private fun readAssetOrNull(name: String): String? = try {
        context.assets.open(name).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }

    private fun readAssetLines(name: String): Sequence<String> = try {
        context.assets.open(name).bufferedReader().readLines().asSequence()
    } catch (e: Exception) {
        emptySequence()
    }
}
