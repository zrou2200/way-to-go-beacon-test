import {BeaconObservation, PositionFix} from '../model/models';
import {FloorPlan} from '../model/floorPlan';
import {PositioningConfig} from '../model/positioningConfig';
import {PositionEstimator} from './positionEstimator';

/**
 * Default estimator (Section 5.4): proximity / weighted centroid / weighted
 * nonlinear least-squares trilateration with centroid fallback.
 */
export class WeightedPositionEstimator implements PositionEstimator {
  estimate(
    observations: BeaconObservation[],
    floor: FloorPlan,
    config: PositioningConfig,
    nowMs: number,
  ): PositionFix | null {
    const used = [...observations]
      .sort((a, b) => b.filteredRssi - a.filteredRssi)
      .slice(0, config.maxBeaconsUsed);

    switch (used.length) {
      case 0:
        return null;
      case 1:
        return this.proximity(used[0], floor, config, nowMs);
      case 2:
        return this.centroidFix(used, floor, config, nowMs);
      default:
        return (
          this.trilaterate(used, floor, config, nowMs) ??
          this.centroidFix(used, floor, config, nowMs)
        );
    }
  }

  private proximity(
    obs: BeaconObservation,
    floor: FloorPlan,
    config: PositioningConfig,
    nowMs: number,
  ): PositionFix {
    // Place user at the measured distance from the beacon (East default bearing).
    const x = floor.clampX(obs.beacon.x + obs.distanceM);
    const y = floor.clampY(obs.beacon.y);
    const accuracy = Math.max(config.minAccuracyM, obs.distanceM * 0.5);
    return {
      floorLevel: floor.level,
      x,
      y,
      accuracyM: accuracy,
      beaconsUsed: 1,
      method: 'PROXIMITY',
      timestampMs: nowMs,
    };
  }

  private centroidFix(
    used: BeaconObservation[],
    floor: FloorPlan,
    config: PositioningConfig,
    nowMs: number,
  ): PositionFix {
    const [cx, cy] = this.weightedCentroid(used, config.centroidWeightPower);
    let wSum = 0.0;
    let wdSum = 0.0;
    for (const o of used) {
      const w = this.weight(o.distanceM, config.centroidWeightPower);
      wSum += w;
      wdSum += w * o.distanceM;
    }
    const weightedMeanDistance = wSum > 0 ? wdSum / wSum : config.maxDistanceM;
    const accuracy = Math.max(config.minAccuracyM, weightedMeanDistance * 0.5);
    return {
      floorLevel: floor.level,
      x: floor.clampX(cx),
      y: floor.clampY(cy),
      accuracyM: accuracy,
      beaconsUsed: used.length,
      method: 'CENTROID',
      timestampMs: nowMs,
    };
  }

  private trilaterate(
    used: BeaconObservation[],
    floor: FloorPlan,
    config: PositioningConfig,
    nowMs: number,
  ): PositionFix | null {
    const [startX, startY] = this.weightedCentroid(used, config.centroidWeightPower);
    let x = startX;
    let y = startY;
    let converged = false;

    for (let iter = 0; iter < 10; iter++) {
      let a00 = 0.0;
      let a01 = 0.0;
      let a11 = 0.0;
      let b0 = 0.0;
      let b1 = 0.0;
      let degenerate = false;
      for (const o of used) {
        const dx = x - o.beacon.x;
        const dy = y - o.beacon.y;
        const dist = Math.hypot(dx, dy);
        if (dist < 1e-6) {
          degenerate = true;
          break;
        }
        const jx = dx / dist;
        const jy = dy / dist;
        const r = dist - o.distanceM;
        const w = 1.0 / (o.distanceM * o.distanceM);
        a00 += w * jx * jx;
        a01 += w * jx * jy;
        a11 += w * jy * jy;
        b0 += w * jx * r;
        b1 += w * jy * r;
      }
      if (degenerate) continue;
      const det = a00 * a11 - a01 * a01;
      if (Math.abs(det) < 1e-12) return null; // singular -> fallback
      const stepX = (-a11 * b0 + a01 * b1) / det;
      const stepY = (a01 * b0 - a00 * b1) / det;
      x += stepX;
      y += stepY;
      if (Math.hypot(stepX, stepY) < 0.01) {
        converged = true;
        continue;
      }
    }

    if (!converged) return null;
    if (Number.isNaN(x) || Number.isNaN(y) || !Number.isFinite(x) || !Number.isFinite(y)) {
      return null;
    }

    // Reject solutions far outside the bounding box of the used beacons.
    const minX = Math.min(...used.map(o => o.beacon.x));
    const maxX = Math.max(...used.map(o => o.beacon.x));
    const minY = Math.min(...used.map(o => o.beacon.y));
    const maxY = Math.max(...used.map(o => o.beacon.y));
    if (x < minX - 5 || x > maxX + 5 || y < minY - 5 || y > maxY + 5) return null;

    // Residual RMS for accuracy.
    let sq = 0.0;
    for (const o of used) {
      const r = Math.hypot(x - o.beacon.x, y - o.beacon.y) - o.distanceM;
      sq += r * r;
    }
    const rms = Math.sqrt(sq / used.length);
    const accuracy = Math.max(config.minAccuracyM, rms);

    return {
      floorLevel: floor.level,
      x: floor.clampX(x),
      y: floor.clampY(y),
      accuracyM: accuracy,
      beaconsUsed: used.length,
      method: 'TRILATERATION',
      timestampMs: nowMs,
    };
  }

  private weightedCentroid(
    used: BeaconObservation[],
    power: number,
  ): [number, number] {
    let wSum = 0.0;
    let xSum = 0.0;
    let ySum = 0.0;
    for (const o of used) {
      const w = this.weight(o.distanceM, power);
      wSum += w;
      xSum += w * o.beacon.x;
      ySum += w * o.beacon.y;
    }
    if (wSum <= 0.0) {
      return [used[0].beacon.x, used[0].beacon.y];
    }
    return [xSum / wSum, ySum / wSum];
  }

  private weight(distance: number, power: number): number {
    const d = Math.max(distance, 1e-3);
    return 1.0 / Math.pow(d, power);
  }
}
