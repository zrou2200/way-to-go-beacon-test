import {PositionFix} from '../model/models';
import {FloorPlan} from '../model/floorPlan';

/**
 * A post-processing step applied after the smoother (Section 5.6). Extension point
 * for a future walkable-area snapper or motion-sensor fusion step.
 */
export interface PositionPostProcessor {
  process(fix: PositionFix, floor: FloorPlan): PositionFix;
}

/** v1's only processor: clamp the position to the floor bounds. */
export class ClampToBoundsProcessor implements PositionPostProcessor {
  process(fix: PositionFix, floor: FloorPlan): PositionFix {
    return {...fix, x: floor.clampX(fix.x), y: floor.clampY(fix.y)};
  }
}
