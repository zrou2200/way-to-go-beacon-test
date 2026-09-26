import {makeBeaconKey} from '../util/beaconKey';
import {isRegistryValid} from './registryRepository';
import {RegistryParser} from './registryParser';

const floorsJson = `
{ "floors": [ { "level": 1, "name": "G", "image": "f1.png", "width_m": 60.0, "height_m": 40.0 } ] }
`;

describe('RegistryParser', () => {
  test('skips invalid entries but keeps valid ones', () => {
    const registryJson = `
    {
      "registry_version": "t",
      "uuid": "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
      "beacons": [
        { "major": 1, "minor": 1, "label": "OK",   "floor_level": 1, "x": 5.0,   "y": 5.0, "rssi_1m": -59, "status": "active" },
        { "major": 1, "minor": 1, "label": "DUP",  "floor_level": 1, "x": 6.0,   "y": 6.0, "rssi_1m": -59, "status": "active" },
        { "major": 1, "minor": 2, "label": "MISS", "floor_level": 1, "x": 7.0,   "y": 7.0, "status": "active" },
        { "major": 1, "minor": 3, "label": "OOB",  "floor_level": 1, "x": 100.0, "y": 5.0, "rssi_1m": -59, "status": "active" },
        { "major": 1, "minor": 4, "label": "OFF",  "floor_level": 1, "x": 8.0,   "y": 8.0, "rssi_1m": -59, "status": "inactive" },
        { "major": 9, "minor": 1, "label": "NOFL", "floor_level": 9, "x": 1.0,   "y": 1.0, "rssi_1m": -59, "status": "active" }
      ]
    }
    `;

    const load = RegistryParser.parse(registryJson, floorsJson);
    const repo = load.repository;
    expect(repo).not.toBeNull();
    // Only the single fully-valid, active, unique, in-bounds beacon survives.
    expect(repo!.activeBeacons()).toHaveLength(1);
    const key = makeBeaconKey('E2C56DB5DFFB48D2B060D0F5A71096E0', 1, 1);
    expect(repo!.beacon(key)?.label).toBe('OK');
    // Warnings recorded for duplicate, missing, out-of-bounds, and unknown floor.
    expect(load.warnings.length).toBeGreaterThanOrEqual(4);
  });

  test('missing uuid is fatal', () => {
    const registryJson = `{ "beacons": [] }`;
    const load = RegistryParser.parse(registryJson, floorsJson);
    expect(load.repository).toBeNull();
    expect(isRegistryValid(load)).toBe(false);
    expect(load.fatalError).not.toBeNull();
  });

  test('unparseable registry is fatal not crash', () => {
    const load = RegistryParser.parse('{ not json', floorsJson);
    expect(load.repository).toBeNull();
    expect(load.fatalError).not.toBeNull();
  });
});
