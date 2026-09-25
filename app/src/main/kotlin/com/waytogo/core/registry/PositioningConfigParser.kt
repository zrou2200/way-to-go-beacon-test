package com.waytogo.core.registry

import com.waytogo.core.model.PositioningConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses an optional positioning_config.json asset into a [PositioningConfig].
 * Any missing field falls back to the spec default. Unparseable input yields
 * the full default config (positioning must still work).
 */
object PositioningConfigParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class ConfigDto(
        @SerialName("windowMs") val windowMs: Long? = null,
        @SerialName("staleMs") val staleMs: Long? = null,
        @SerialName("minSamplesPerBeacon") val minSamplesPerBeacon: Int? = null,
        @SerialName("pathLossExponent") val pathLossExponent: Double? = null,
        @SerialName("minDistanceM") val minDistanceM: Double? = null,
        @SerialName("maxDistanceM") val maxDistanceM: Double? = null,
        @SerialName("maxBeaconsUsed") val maxBeaconsUsed: Int? = null,
        @SerialName("centroidWeightPower") val centroidWeightPower: Double? = null,
        @SerialName("emitIntervalMs") val emitIntervalMs: Long? = null,
        @SerialName("smoothingAlpha") val smoothingAlpha: Double? = null,
        @SerialName("maxSpeedMps") val maxSpeedMps: Double? = null,
        @SerialName("floorSwitchHoldMs") val floorSwitchHoldMs: Long? = null,
        @SerialName("minAccuracyM") val minAccuracyM: Double? = null,
        @SerialName("positionStaleMs") val positionStaleMs: Long? = null,
    )

    fun parse(configJson: String?): PositioningConfig {
        if (configJson.isNullOrBlank()) return PositioningConfig()
        val dto = try {
            json.decodeFromString<ConfigDto>(configJson)
        } catch (e: Exception) {
            return PositioningConfig()
        }
        val d = PositioningConfig()
        return PositioningConfig(
            windowMs = dto.windowMs ?: d.windowMs,
            staleMs = dto.staleMs ?: d.staleMs,
            minSamplesPerBeacon = dto.minSamplesPerBeacon ?: d.minSamplesPerBeacon,
            pathLossExponent = dto.pathLossExponent ?: d.pathLossExponent,
            minDistanceM = dto.minDistanceM ?: d.minDistanceM,
            maxDistanceM = dto.maxDistanceM ?: d.maxDistanceM,
            maxBeaconsUsed = dto.maxBeaconsUsed ?: d.maxBeaconsUsed,
            centroidWeightPower = dto.centroidWeightPower ?: d.centroidWeightPower,
            emitIntervalMs = dto.emitIntervalMs ?: d.emitIntervalMs,
            smoothingAlpha = dto.smoothingAlpha ?: d.smoothingAlpha,
            maxSpeedMps = dto.maxSpeedMps ?: d.maxSpeedMps,
            floorSwitchHoldMs = dto.floorSwitchHoldMs ?: d.floorSwitchHoldMs,
            minAccuracyM = dto.minAccuracyM ?: d.minAccuracyM,
            positionStaleMs = dto.positionStaleMs ?: d.positionStaleMs,
        )
    }
}
