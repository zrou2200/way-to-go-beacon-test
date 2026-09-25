package com.waytogo.core.registry

import com.waytogo.core.model.BeaconKey
import com.waytogo.core.model.FloorPlan
import com.waytogo.core.model.RegisteredBeacon

/**
 * Read-only access to the loaded beacon registry and floor catalog.
 * Lookup key is (uuid, major, minor); unknown beacons return null.
 */
interface RegistryRepository {
    val uuid: String
    val registryVersion: String
    fun beacon(key: BeaconKey): RegisteredBeacon?
    fun activeBeacons(): List<RegisteredBeacon>
    fun beaconsOnFloor(level: Int): List<RegisteredBeacon>
    fun floors(): List<FloorPlan>
    fun floor(level: Int): FloorPlan?
}

/** Result of loading + validating the registry assets. */
data class RegistryLoad(
    val repository: RegistryRepository?,
    val warnings: List<String>,
    val fatalError: String?,
) {
    val isValid: Boolean get() = repository != null
}

/** Simple in-memory implementation backed by validated maps. */
class InMemoryRegistryRepository(
    override val uuid: String,
    override val registryVersion: String,
    beacons: List<RegisteredBeacon>,
    floors: List<FloorPlan>,
) : RegistryRepository {

    private val byKey: Map<BeaconKey, RegisteredBeacon> = beacons.associateBy { it.key }
    private val allBeacons: List<RegisteredBeacon> = beacons
    private val floorsByLevel: Map<Int, FloorPlan> = floors.associateBy { it.level }
    private val allFloors: List<FloorPlan> = floors.sortedBy { it.level }

    override fun beacon(key: BeaconKey): RegisteredBeacon? = byKey[key]
    override fun activeBeacons(): List<RegisteredBeacon> = allBeacons
    override fun beaconsOnFloor(level: Int): List<RegisteredBeacon> =
        allBeacons.filter { it.floorLevel == level }
    override fun floors(): List<FloorPlan> = allFloors
    override fun floor(level: Int): FloorPlan? = floorsByLevel[level]
}
