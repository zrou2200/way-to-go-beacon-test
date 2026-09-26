import {
  DEFAULT_POSITIONING_CONFIG,
  PositioningConfig,
} from '../model/positioningConfig';

/**
 * Parses an optional positioning_config.json asset into a [PositioningConfig].
 * Any missing field falls back to the spec default. Unparseable input yields
 * the full default config (positioning must still work).
 */
export const PositioningConfigParser = {
  parse(configJson: string | null | undefined): PositioningConfig {
    if (configJson == null || configJson.trim() === '') {
      return {...DEFAULT_POSITIONING_CONFIG};
    }
    let dto: any;
    try {
      dto = JSON.parse(configJson);
    } catch {
      return {...DEFAULT_POSITIONING_CONFIG};
    }
    const d = DEFAULT_POSITIONING_CONFIG;
    const num = (v: unknown, def: number): number =>
      typeof v === 'number' && Number.isFinite(v) ? v : def;
    return {
      windowMs: num(dto?.windowMs, d.windowMs),
      staleMs: num(dto?.staleMs, d.staleMs),
      minSamplesPerBeacon: num(dto?.minSamplesPerBeacon, d.minSamplesPerBeacon),
      pathLossExponent: num(dto?.pathLossExponent, d.pathLossExponent),
      minDistanceM: num(dto?.minDistanceM, d.minDistanceM),
      maxDistanceM: num(dto?.maxDistanceM, d.maxDistanceM),
      maxBeaconsUsed: num(dto?.maxBeaconsUsed, d.maxBeaconsUsed),
      centroidWeightPower: num(dto?.centroidWeightPower, d.centroidWeightPower),
      emitIntervalMs: num(dto?.emitIntervalMs, d.emitIntervalMs),
      smoothingAlpha: num(dto?.smoothingAlpha, d.smoothingAlpha),
      maxSpeedMps: num(dto?.maxSpeedMps, d.maxSpeedMps),
      floorSwitchHoldMs: num(dto?.floorSwitchHoldMs, d.floorSwitchHoldMs),
      minAccuracyM: num(dto?.minAccuracyM, d.minAccuracyM),
      positionStaleMs: num(dto?.positionStaleMs, d.positionStaleMs),
    };
  },
};
