import {DistanceEstimator} from './distanceEstimator';

describe('DistanceEstimator', () => {
  test('at reference rssi returns one meter', () => {
    const d = DistanceEstimator.estimate(-59.0, -59, 2.5, 0.5, 25.0);
    expect(d).toBeCloseTo(1.0, 9);
  });

  test('twenty dB below with n=2 returns ten meters', () => {
    const d = DistanceEstimator.estimate(-79.0, -59, 2.0, 0.5, 100.0);
    expect(d).toBeCloseTo(10.0, 9);
  });

  test('clamps to max', () => {
    const d = DistanceEstimator.estimate(-110.0, -59, 2.5, 0.5, 25.0);
    expect(d).toBeCloseTo(25.0, 9);
  });

  test('clamps to min', () => {
    const d = DistanceEstimator.estimate(-20.0, -59, 2.5, 0.5, 25.0);
    expect(d).toBeCloseTo(0.5, 9);
  });
});
