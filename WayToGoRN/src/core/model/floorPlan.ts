/** A point on a specific floor, used by extension interfaces (routing, snapping). */
export interface FloorPoint {
  readonly floorLevel: number;
  readonly x: number;
  readonly y: number;
}

/** Metadata + pixel mapping for one floor plan image. */
export class FloorPlan {
  constructor(
    readonly level: number,
    readonly name: string,
    readonly image: string,
    readonly widthM: number,
    readonly heightM: number,
  ) {}

  contains(x: number, y: number): boolean {
    return x >= 0 && x <= this.widthM && y >= 0 && y <= this.heightM;
  }

  clampX(x: number): number {
    return Math.min(Math.max(x, 0), this.widthM);
  }

  clampY(y: number): number {
    return Math.min(Math.max(y, 0), this.heightM);
  }
}
