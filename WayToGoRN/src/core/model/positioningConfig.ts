/**
 * All positioning tunables in one place (Section 5). Defaults match the spec.
 * Loadable from a JSON asset; nothing here should be hard-coded elsewhere.
 */
export interface PositioningConfig {
  readonly windowMs: number;
  readonly staleMs: number;
  readonly minSamplesPerBeacon: number;
  readonly pathLossExponent: number;
  readonly minDistanceM: number;
  readonly maxDistanceM: number;
  readonly maxBeaconsUsed: number;
  readonly centroidWeightPower: number;
  readonly emitIntervalMs: number;
  readonly smoothingAlpha: number;
  readonly maxSpeedMps: number;
  readonly floorSwitchHoldMs: number;
  readonly minAccuracyM: number;
  readonly positionStaleMs: number;
}

export const DEFAULT_POSITIONING_CONFIG: PositioningConfig = {
  windowMs: 3000,
  staleMs: 5000,
  minSamplesPerBeacon: 2,
  pathLossExponent: 2.5,
  minDistanceM: 0.5,
  maxDistanceM: 25.0,
  maxBeaconsUsed: 4,
  centroidWeightPower: 2.0,
  emitIntervalMs: 1000,
  smoothingAlpha: 0.35,
  maxSpeedMps: 2.5,
  floorSwitchHoldMs: 3000,
  minAccuracyM: 1.5,
  positionStaleMs: 8000,
};
