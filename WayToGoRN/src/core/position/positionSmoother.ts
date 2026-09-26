import {PositionFix} from '../model/models';
import {PositioningConfig} from '../model/positioningConfig';

/**
 * Temporal smoothing (Section 5.5): per-axis EMA with jump rejection.
 *
 * - The first fix (and the first fix after a floor change) passes through unchanged.
 * - Jump rejection caps the raw estimate to maxSpeedMps * dt away from the previous
 *   position before applying the EMA, so a wild reading nudges rather than teleports
 *   the dot.
 *
 * Stateful; call from the single positioning dispatcher.
 */
export class PositionSmoother {
  private previous: PositionFix | null = null;

  constructor(private readonly config: PositioningConfig) {}

  smooth(fix: PositionFix): PositionFix {
    const prev = this.previous;
    if (prev == null || prev.floorLevel !== fix.floorLevel) {
      this.previous = fix;
      return fix;
    }

    const dt = (fix.timestampMs - prev.timestampMs) / 1000.0;
    let targetX = fix.x;
    let targetY = fix.y;

    if (dt > 0) {
      const dx = fix.x - prev.x;
      const dy = fix.y - prev.y;
      const dist = Math.hypot(dx, dy);
      const maxStep = this.config.maxSpeedMps * dt;
      if (dist > maxStep && dist > 0) {
        const scale = maxStep / dist;
        targetX = prev.x + dx * scale;
        targetY = prev.y + dy * scale;
      }
    }

    const a = this.config.smoothingAlpha;
    const smoothedX = a * targetX + (1 - a) * prev.x;
    const smoothedY = a * targetY + (1 - a) * prev.y;

    const result: PositionFix = {...fix, x: smoothedX, y: smoothedY};
    this.previous = result;
    return result;
  }

  reset(): void {
    this.previous = null;
  }
}
