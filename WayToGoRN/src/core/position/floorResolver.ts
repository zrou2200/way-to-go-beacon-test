import {BeaconObservation} from '../model/models';
import {PositioningConfig} from '../model/positioningConfig';

/**
 * Resolves the reported floor from beacon observations (Section 5.3).
 *
 * - Candidate floor = majority floorLevel among the top 3 beacons by filtered RSSI,
 *   ties broken by the single strongest beacon.
 * - Hysteresis: the reported floor only changes after the candidate differs from the
 *   current floor continuously for at least [PositioningConfig.floorSwitchHoldMs].
 * - The very first resolution sets the floor immediately.
 *
 * Stateful; call from the single positioning dispatcher.
 */
export class FloorResolver {
  private _currentFloor: number | null = null;
  private pendingFloor: number | null = null;
  private pendingSinceMs = 0;

  constructor(private readonly config: PositioningConfig) {}

  get currentFloor(): number | null {
    return this._currentFloor;
  }

  /** Returns the currently reported floor, or null if none has been established. */
  resolve(observations: BeaconObservation[], nowMs: number): number | null {
    const candidate = this.candidateFloor(observations);
    if (candidate == null) return this._currentFloor;

    const current = this._currentFloor;
    if (current == null) {
      this._currentFloor = candidate;
      this.pendingFloor = null;
      return this._currentFloor;
    }

    if (candidate === current) {
      this.pendingFloor = null;
      return current;
    }

    if (this.pendingFloor !== candidate) {
      this.pendingFloor = candidate;
      this.pendingSinceMs = nowMs;
    }
    if (nowMs - this.pendingSinceMs >= this.config.floorSwitchHoldMs) {
      this._currentFloor = candidate;
      this.pendingFloor = null;
    }
    return this._currentFloor;
  }

  reset(): void {
    this._currentFloor = null;
    this.pendingFloor = null;
    this.pendingSinceMs = 0;
  }

  private candidateFloor(observations: BeaconObservation[]): number | null {
    if (observations.length === 0) return null;
    const top = [...observations]
      .sort((a, b) => b.filteredRssi - a.filteredRssi)
      .slice(0, 3);

    const counts = new Map<number, number>();
    for (const obs of top) {
      const floor = obs.beacon.floorLevel;
      counts.set(floor, (counts.get(floor) ?? 0) + 1);
    }
    let maxCount = 0;
    for (const c of counts.values()) if (c > maxCount) maxCount = c;
    const leaders = [...counts.entries()]
      .filter(([, c]) => c === maxCount)
      .map(([floor]) => floor);
    // Unique majority, or tie resolved by the strongest beacon (top[0]).
    return leaders.length === 1 ? leaders[0] : top[0].beacon.floorLevel;
  }
}
