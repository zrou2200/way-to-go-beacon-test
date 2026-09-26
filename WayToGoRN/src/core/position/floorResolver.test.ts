import {BeaconObservation} from '../model/models';
import {DEFAULT_POSITIONING_CONFIG} from '../model/positioningConfig';
import {FloorResolver} from './floorResolver';

const config = DEFAULT_POSITIONING_CONFIG; // floorSwitchHoldMs = 3000

let id = 0;
function obs(floor: number, rssi: number): BeaconObservation {
  const beacon = {
    key: {uuid: 'U', major: floor, minor: id++},
    label: 'B',
    floorLevel: floor,
    x: 0.0,
    y: 0.0,
    rssi1m: -59,
  };
  return {beacon, filteredRssi: rssi, distanceM: 2.0, sampleCount: 3, lastSeenMs: 0};
}

function floorObs(floor: number): BeaconObservation[] {
  return [obs(floor, -60.0), obs(floor, -62.0), obs(floor, -64.0)];
}

describe('FloorResolver', () => {
  test('first fix sets floor immediately', () => {
    const r = new FloorResolver(config);
    expect(r.resolve(floorObs(2), 0)).toBe(2);
  });

  test('does not switch before hold then switches after', () => {
    const r = new FloorResolver(config);
    expect(r.resolve(floorObs(1), 0)).toBe(1);
    // Candidate is floor 2 continuously, but hold is 3000 ms.
    expect(r.resolve(floorObs(2), 1000)).toBe(1);
    expect(r.resolve(floorObs(2), 2000)).toBe(1);
    expect(r.resolve(floorObs(2), 3500)).toBe(1); // 3500-1000 = 2500 < 3000
    expect(r.resolve(floorObs(2), 4000)).toBe(2); // 4000-1000 = 3000 >= 3000
  });

  test('flapping candidate resets hold', () => {
    const r = new FloorResolver(config);
    expect(r.resolve(floorObs(1), 0)).toBe(1);
    expect(r.resolve(floorObs(2), 1000)).toBe(1); // pending 2 since 1000
    expect(r.resolve(floorObs(1), 2000)).toBe(1); // back to 1, pending cleared
    expect(r.resolve(floorObs(2), 2500)).toBe(1); // pending 2 since 2500
    expect(r.resolve(floorObs(2), 4000)).toBe(1); // 4000-2500 = 1500 < 3000
  });
});
