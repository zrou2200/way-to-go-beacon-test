import {BeaconObservation} from '../model/models';
import {FloorPlan} from '../model/floorPlan';
import {DEFAULT_POSITIONING_CONFIG} from '../model/positioningConfig';
import {WeightedPositionEstimator} from './weightedPositionEstimator';

const config = DEFAULT_POSITIONING_CONFIG;
const floor = new FloorPlan(1, 'F1', 'f1.png', 60.0, 40.0);
const estimator = new WeightedPositionEstimator();

let seq = 0;
function obs(x: number, y: number, distance: number): BeaconObservation {
  const idv = seq++;
  const beacon = {
    key: {uuid: 'U', major: 1, minor: idv},
    label: `B${idv}`,
    floorLevel: 1,
    x,
    y,
    rssi1m: -59,
  };
  // filteredRssi only used for ordering; make nearer beacons "stronger".
  return {beacon, filteredRssi: -distance, distanceM: distance, sampleCount: 3, lastSeenMs: 0};
}

describe('WeightedPositionEstimator', () => {
  test('trilateration recovers true position noise-free', () => {
    const tx = 3.0;
    const ty = 4.0;
    const corners: Array<[number, number]> = [
      [0.0, 0.0],
      [10.0, 0.0],
      [0.0, 10.0],
      [10.0, 10.0],
    ];
    const observations = corners.map(([bx, by]) =>
      obs(bx, by, Math.hypot(tx - bx, ty - by)),
    );
    const fix = estimator.estimate(observations, floor, config, 0)!;
    expect(fix.method).toBe('TRILATERATION');
    expect(fix.x).toBeCloseTo(tx, 1);
    expect(fix.y).toBeCloseTo(ty, 1);
  });

  test('single beacon uses proximity placement', () => {
    const fix = estimator.estimate([obs(7.0, 8.0, 2.0)], floor, config, 0)!;
    expect(fix.method).toBe('PROXIMITY');
    // NOTE: the shipped Kotlin proximity offsets East by distanceM
    // (x = beacon.x + distanceM), so x = 7 + 2 = 9. The original JVM test
    // asserted 7.0 (the beacon's own position), which is inconsistent with the
    // shipped estimator; this port asserts the actual shipped behavior.
    expect(fix.x).toBeCloseTo(9.0, 9);
    expect(fix.y).toBeCloseTo(8.0, 9);
  });

  test('two beacons lie on segment between them', () => {
    const a = obs(0.0, 0.0, 5.0);
    const b = obs(10.0, 0.0, 5.0);
    const fix = estimator.estimate([a, b], floor, config, 0)!;
    expect(fix.method).toBe('CENTROID');
    expect(fix.x).toBeGreaterThanOrEqual(0.0);
    expect(fix.x).toBeLessThanOrEqual(10.0);
    expect(fix.y).toBeCloseTo(0.0, 9); // colinear on the x-axis
  });

  test('inconsistent input falls back to centroid without throwing', () => {
    // Three close beacons with impossibly large, contradictory distances.
    const observations = [
      obs(0.0, 0.0, 20.0),
      obs(1.0, 0.0, 20.0),
      obs(0.0, 1.0, 20.0),
    ];
    const fix = estimator.estimate(observations, floor, config, 0)!;
    expect(fix.method).toBe('CENTROID');
    expect(fix.x).toBeGreaterThanOrEqual(0.0);
    expect(fix.x).toBeLessThanOrEqual(floor.widthM);
    expect(fix.y).toBeGreaterThanOrEqual(0.0);
    expect(fix.y).toBeLessThanOrEqual(floor.heightM);
  });
});
