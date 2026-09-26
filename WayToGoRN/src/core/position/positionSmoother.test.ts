import {Method, PositionFix} from '../model/models';
import {DEFAULT_POSITIONING_CONFIG} from '../model/positioningConfig';
import {PositionSmoother} from './positionSmoother';

const config = DEFAULT_POSITIONING_CONFIG; // maxSpeedMps = 2.5

function fix(x: number, y: number, floor: number, ts: number): PositionFix {
  return {
    floorLevel: floor,
    x,
    y,
    accuracyM: 2.0,
    beaconsUsed: 3,
    method: 'CENTROID' as Method,
    timestampMs: ts,
  };
}

describe('PositionSmoother', () => {
  test('first fix passes through', () => {
    const s = new PositionSmoother(config);
    const f = fix(10.0, 5.0, 1, 0);
    expect(s.smooth(f)).toEqual(f);
  });

  test('jump is limited to maxSpeed * dt', () => {
    const s = new PositionSmoother(config);
    const prev = fix(0.0, 0.0, 1, 0);
    s.smooth(prev);
    const jumped = fix(50.0, 0.0, 1, 1000); // dt = 1s, 50 m jump
    const out = s.smooth(jumped);
    const moved = Math.hypot(out.x - prev.x, out.y - prev.y);
    // Clamped to maxSpeed*dt = 2.5 m (EMA keeps it at or below that).
    expect(moved).toBeLessThanOrEqual(2.5 + 1e-6);
  });

  test('smoothing resets on floor change', () => {
    const s = new PositionSmoother(config);
    s.smooth(fix(0.0, 0.0, 1, 0));
    const onFloor2 = fix(40.0, 20.0, 2, 1000);
    const out = s.smooth(onFloor2);
    // No smoothing across floors: passes through unchanged.
    expect(out.x).toBeCloseTo(40.0, 9);
    expect(out.y).toBeCloseTo(20.0, 9);
  });
});
