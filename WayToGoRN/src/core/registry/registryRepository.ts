import {BeaconKey, RegisteredBeacon} from '../model/models';
import {FloorPlan} from '../model/floorPlan';
import {beaconKeyToString} from '../util/beaconKey';

/**
 * Read-only access to the loaded beacon registry and floor catalog.
 * Lookup key is (uuid, major, minor); unknown beacons return null.
 */
export interface RegistryRepository {
  readonly uuid: string;
  readonly registryVersion: string;
  beacon(key: BeaconKey): RegisteredBeacon | null;
  activeBeacons(): RegisteredBeacon[];
  beaconsOnFloor(level: number): RegisteredBeacon[];
  floors(): FloorPlan[];
  floor(level: number): FloorPlan | null;
}

/** Result of loading + validating the registry assets. */
export interface RegistryLoad {
  readonly repository: RegistryRepository | null;
  readonly warnings: string[];
  readonly fatalError: string | null;
}

export function isRegistryValid(load: RegistryLoad): boolean {
  return load.repository != null;
}

/** Simple in-memory implementation backed by validated maps. */
export class InMemoryRegistryRepository implements RegistryRepository {
  private readonly byKey = new Map<string, RegisteredBeacon>();
  private readonly allBeacons: RegisteredBeacon[];
  private readonly floorsByLevel = new Map<number, FloorPlan>();
  private readonly allFloors: FloorPlan[];

  constructor(
    readonly uuid: string,
    readonly registryVersion: string,
    beacons: RegisteredBeacon[],
    floors: FloorPlan[],
  ) {
    this.allBeacons = beacons;
    for (const b of beacons) this.byKey.set(beaconKeyToString(b.key), b);
    this.allFloors = [...floors].sort((a, b) => a.level - b.level);
    for (const f of floors) this.floorsByLevel.set(f.level, f);
  }

  beacon(key: BeaconKey): RegisteredBeacon | null {
    return this.byKey.get(beaconKeyToString(key)) ?? null;
  }
  activeBeacons(): RegisteredBeacon[] {
    return this.allBeacons;
  }
  beaconsOnFloor(level: number): RegisteredBeacon[] {
    return this.allBeacons.filter(b => b.floorLevel === level);
  }
  floors(): FloorPlan[] {
    return this.allFloors;
  }
  floor(level: number): FloorPlan | null {
    return this.floorsByLevel.get(level) ?? null;
  }
}
