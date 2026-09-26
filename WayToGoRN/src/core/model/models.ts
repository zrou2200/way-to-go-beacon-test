/**
 * Core domain models (Section 2/5), ported 1:1 from the Kotlin `core.model`.
 * Kotlin data classes become plain TypeScript interfaces so structural equality
 * (Jest `toEqual`) matches the original JVM unit tests.
 */

/** Unique identity of a beacon: proximity UUID plus major/minor. */
export interface BeaconKey {
  readonly uuid: string;
  readonly major: number;
  readonly minor: number;
}

/** A beacon known to the app, loaded from the registry asset. */
export interface RegisteredBeacon {
  readonly key: BeaconKey;
  readonly label: string;
  readonly floorLevel: number;
  readonly x: number; // meters, floor-local coordinate system
  readonly y: number; // meters
  readonly rssi1m: number; // dBm, calibrated measured power at 1 m
}

/** A single advertisement observation as reported by the platform. */
export interface RawReading {
  readonly key: BeaconKey;
  readonly rssi: number; // dBm, as reported by the OS
  readonly advertisedTxPower: number | null; // dBm from the iBeacon frame, may be null
  readonly timestampMs: number; // monotonic clock
}

/** Output of the aggregator: a filtered, distance-estimated view of one beacon. */
export interface BeaconObservation {
  readonly beacon: RegisteredBeacon;
  readonly filteredRssi: number;
  readonly distanceM: number;
  readonly sampleCount: number;
  readonly lastSeenMs: number;
}

export type Method = 'PROXIMITY' | 'CENTROID' | 'TRILATERATION';

/** A computed position on a floor. */
export interface PositionFix {
  readonly floorLevel: number;
  readonly x: number;
  readonly y: number;
  readonly accuracyM: number;
  readonly beaconsUsed: number;
  readonly method: Method;
  readonly timestampMs: number;
}

export type ErrorKind =
  | 'PERMISSION_DENIED'
  | 'BLUETOOTH_OFF'
  | 'LOCATION_SERVICES_OFF'
  | 'SCAN_FAILED'
  | 'REGISTRY_INVALID';

/** High-level state surfaced to the UI (sealed interface -> discriminated union). */
export type PositionState =
  | {readonly kind: 'Idle'}
  | {readonly kind: 'Searching'}
  | {readonly kind: 'Located'; readonly fix: PositionFix}
  | {readonly kind: 'Degraded'; readonly lastFix: PositionFix; readonly reason: string}
  | {readonly kind: 'Error'; readonly error: ErrorKind};

/** Factories mirroring the Kotlin sealed-interface constructors. */
export const PositionState = {
  idle: (): PositionState => ({kind: 'Idle'}),
  searching: (): PositionState => ({kind: 'Searching'}),
  located: (fix: PositionFix): PositionState => ({kind: 'Located', fix}),
  degraded: (lastFix: PositionFix, reason: string): PositionState => ({
    kind: 'Degraded',
    lastFix,
    reason,
  }),
  error: (error: ErrorKind): PositionState => ({kind: 'Error', error}),
};
