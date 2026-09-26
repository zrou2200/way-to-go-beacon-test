import {BeaconObservation, PositionFix} from '../model/models';
import {FloorPlan} from '../model/floorPlan';
import {PositioningConfig} from '../model/positioningConfig';

/**
 * Estimates an (x, y) position from beacon observations on a single floor.
 * Implemented as an interface so alternative estimators (e.g. a future
 * FingerprintEstimator, Section 5.6) can be swapped in.
 */
export interface PositionEstimator {
  /**
   * @param observations observations already restricted to [floor].
   * @return a [PositionFix], or null if no fix is possible (0 usable beacons).
   */
  estimate(
    observations: BeaconObservation[],
    floor: FloorPlan,
    config: PositioningConfig,
    nowMs: number,
  ): PositionFix | null;
}
