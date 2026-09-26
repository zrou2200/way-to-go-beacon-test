import {FloorPoint} from '../model/floorPlan';

/** A planned route between two floor points. Unimplemented in v1 (Section 5.6). */
export interface Route {
  readonly points: FloorPoint[];
}

/** Routing extension point. No implementation ships in v1. */
export interface Router {
  route(from: FloorPoint, to: FloorPoint): Route;
}
