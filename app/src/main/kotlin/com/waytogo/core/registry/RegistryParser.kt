package com.waytogo.core.registry

import com.waytogo.core.model.BeaconKey
import com.waytogo.core.model.FloorPlan
import com.waytogo.core.model.RegisteredBeacon
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses and validates the registry (Section 2.2) and floors (Section 2.3) assets.
 * Invalid entries are skipped with a warning; the app never crashes on bad input.
 * A fatal error (unparseable registry / missing UUID / no floors) yields
 * REGISTRY_INVALID upstream.
 */
object RegistryParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class RegistryDto(
        @SerialName("registry_version") val registryVersion: String? = null,
        val uuid: String? = null,
        val beacons: List<BeaconDto> = emptyList(),
    )

    @Serializable
    private data class BeaconDto(
        val major: Int? = null,
        val minor: Int? = null,
        val label: String? = null,
        @SerialName("floor_level") val floorLevel: Int? = null,
        val x: Double? = null,
        val y: Double? = null,
        @SerialName("rssi_1m") val rssi1m: Int? = null,
        val status: String? = null,
    )

    @Serializable
    private data class FloorsDto(val floors: List<FloorDto> = emptyList())

    @Serializable
    private data class FloorDto(
        val level: Int? = null,
        val name: String? = null,
        val image: String? = null,
        @SerialName("width_m") val widthM: Double? = null,
        @SerialName("height_m") val heightM: Double? = null,
    )

    fun parse(registryJson: String, floorsJson: String): RegistryLoad {
        val warnings = ArrayList<String>()

        val registryDto = try {
            json.decodeFromString<RegistryDto>(registryJson)
        } catch (e: Exception) {
            return RegistryLoad(null, warnings, "Registry JSON could not be parsed: ${e.message}")
        }
        val floorsDto = try {
            json.decodeFromString<FloorsDto>(floorsJson)
        } catch (e: Exception) {
            return RegistryLoad(null, warnings, "Floors JSON could not be parsed: ${e.message}")
        }

        val uuid = registryDto.uuid?.trim()?.replace("-", "")?.uppercase()
        if (uuid.isNullOrBlank()) {
            return RegistryLoad(null, warnings, "Registry is missing a top-level uuid.")
        }

        val floors = parseFloors(floorsDto, warnings)
        if (floors.isEmpty()) {
            return RegistryLoad(null, warnings, "No valid floors were loaded.")
        }
        val floorsByLevel = floors.associateBy { it.level }

        val beacons = parseBeacons(registryDto.beacons, uuid, floorsByLevel, warnings)

        val repo = InMemoryRegistryRepository(
            uuid = uuid,
            registryVersion = registryDto.registryVersion ?: "unknown",
            beacons = beacons,
            floors = floors,
        )
        return RegistryLoad(repo, warnings, null)
    }

    private fun parseFloors(dto: FloorsDto, warnings: MutableList<String>): List<FloorPlan> {
        val result = ArrayList<FloorPlan>()
        val seenLevels = HashSet<Int>()
        for ((index, f) in dto.floors.withIndex()) {
            val level = f.level
            val image = f.image
            val widthM = f.widthM
            val heightM = f.heightM
            if (level == null || image.isNullOrBlank() || widthM == null || heightM == null) {
                warnings += "Floor #$index skipped: missing required field(s)."
                continue
            }
            if (widthM <= 0 || heightM <= 0) {
                warnings += "Floor level $level skipped: non-positive dimensions."
                continue
            }
            if (!seenLevels.add(level)) {
                warnings += "Floor level $level skipped: duplicate level."
                continue
            }
            result += FloorPlan(
                level = level,
                name = f.name ?: "Floor $level",
                image = image,
                widthM = widthM,
                heightM = heightM,
            )
        }
        return result
    }

    private fun parseBeacons(
        dtos: List<BeaconDto>,
        uuid: String,
        floorsByLevel: Map<Int, FloorPlan>,
        warnings: MutableList<String>,
    ): List<RegisteredBeacon> {
        val result = ArrayList<RegisteredBeacon>()
        val seenKeys = HashSet<BeaconKey>()
        for ((index, b) in dtos.withIndex()) {
            val major = b.major
            val minor = b.minor
            val floorLevel = b.floorLevel
            val x = b.x
            val y = b.y
            val rssi1m = b.rssi1m
            val label = b.label
            val status = b.status

            if (major == null || minor == null || floorLevel == null || x == null ||
                y == null || rssi1m == null || label.isNullOrBlank() || status.isNullOrBlank()
            ) {
                warnings += "Beacon #$index skipped: missing required field(s)."
                continue
            }
            if (!status.equals("active", ignoreCase = true)) {
                // Non-active beacons are intentionally excluded from positioning.
                continue
            }
            val key = BeaconKey(uuid, major, minor)
            if (!seenKeys.add(key)) {
                warnings += "Beacon $label ($major/$minor) skipped: duplicate key."
                continue
            }
            val floor = floorsByLevel[floorLevel]
            if (floor == null) {
                warnings += "Beacon $label skipped: floor level $floorLevel not found."
                continue
            }
            if (!floor.contains(x, y)) {
                warnings += "Beacon $label skipped: coordinates ($x, $y) outside floor bounds."
                continue
            }
            result += RegisteredBeacon(
                key = key,
                label = label,
                floorLevel = floorLevel,
                x = x,
                y = y,
                rssi1m = rssi1m,
            )
        }
        return result
    }
}
