import {RegisteredBeacon} from '../model/models';
import {FloorPlan} from '../model/floorPlan';
import {makeBeaconKey, beaconKeyToString} from '../util/beaconKey';
import {
  InMemoryRegistryRepository,
  RegistryLoad,
} from './registryRepository';

/**
 * Parses and validates the registry (Section 2.2) and floors (Section 2.3) assets.
 * Invalid entries are skipped with a warning; the app never crashes on bad input.
 * A fatal error (unparseable registry / missing UUID / no floors) yields
 * REGISTRY_INVALID upstream.
 */
function errMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function normalizeUuid(uuid: string): string {
  return uuid.trim().replace(/-/g, '').toUpperCase();
}

function parseFloors(dto: any, warnings: string[]): FloorPlan[] {
  const result: FloorPlan[] = [];
  const seenLevels = new Set<number>();
  const arr: any[] = Array.isArray(dto?.floors) ? dto.floors : [];
  arr.forEach((f, index) => {
    const level = f?.level;
    const image = f?.image;
    const widthM = f?.width_m;
    const heightM = f?.height_m;
    if (
      level == null ||
      typeof image !== 'string' ||
      image.trim() === '' ||
      widthM == null ||
      heightM == null
    ) {
      warnings.push(`Floor #${index} skipped: missing required field(s).`);
      return;
    }
    if (widthM <= 0 || heightM <= 0) {
      warnings.push(`Floor level ${level} skipped: non-positive dimensions.`);
      return;
    }
    if (seenLevels.has(level)) {
      warnings.push(`Floor level ${level} skipped: duplicate level.`);
      return;
    }
    seenLevels.add(level);
    result.push(
      new FloorPlan(
        level,
        typeof f?.name === 'string' ? f.name : `Floor ${level}`,
        image,
        widthM,
        heightM,
      ),
    );
  });
  return result;
}

function parseBeacons(
  dtos: any[],
  uuid: string,
  floorsByLevel: Map<number, FloorPlan>,
  warnings: string[],
): RegisteredBeacon[] {
  const result: RegisteredBeacon[] = [];
  const seenKeys = new Set<string>();
  const arr: any[] = Array.isArray(dtos) ? dtos : [];
  arr.forEach((b, index) => {
    const major = b?.major;
    const minor = b?.minor;
    const floorLevel = b?.floor_level;
    const x = b?.x;
    const y = b?.y;
    const rssi1m = b?.rssi_1m;
    const label = b?.label;
    const status = b?.status;

    if (
      major == null ||
      minor == null ||
      floorLevel == null ||
      x == null ||
      y == null ||
      rssi1m == null ||
      typeof label !== 'string' ||
      label.trim() === '' ||
      typeof status !== 'string' ||
      status.trim() === ''
    ) {
      warnings.push(`Beacon #${index} skipped: missing required field(s).`);
      return;
    }
    if (status.toLowerCase() !== 'active') {
      // Non-active beacons are intentionally excluded from positioning.
      return;
    }
    const key = makeBeaconKey(uuid, major, minor);
    const ks = beaconKeyToString(key);
    if (seenKeys.has(ks)) {
      warnings.push(`Beacon ${label} (${major}/${minor}) skipped: duplicate key.`);
      return;
    }
    seenKeys.add(ks);
    const floor = floorsByLevel.get(floorLevel);
    if (floor == null) {
      warnings.push(`Beacon ${label} skipped: floor level ${floorLevel} not found.`);
      return;
    }
    if (!floor.contains(x, y)) {
      warnings.push(
        `Beacon ${label} skipped: coordinates (${x}, ${y}) outside floor bounds.`,
      );
      return;
    }
    result.push({key, label, floorLevel, x, y, rssi1m});
  });
  return result;
}

export const RegistryParser = {
  parse(registryJson: string, floorsJson: string): RegistryLoad {
    const warnings: string[] = [];

    let registryDto: any;
    try {
      registryDto = JSON.parse(registryJson);
    } catch (e) {
      return {
        repository: null,
        warnings,
        fatalError: `Registry JSON could not be parsed: ${errMessage(e)}`,
      };
    }
    let floorsDto: any;
    try {
      floorsDto = JSON.parse(floorsJson);
    } catch (e) {
      return {
        repository: null,
        warnings,
        fatalError: `Floors JSON could not be parsed: ${errMessage(e)}`,
      };
    }

    const rawUuid = registryDto?.uuid;
    const uuid = typeof rawUuid === 'string' ? normalizeUuid(rawUuid) : '';
    if (uuid === '') {
      return {repository: null, warnings, fatalError: 'Registry is missing a top-level uuid.'};
    }

    const floors = parseFloors(floorsDto, warnings);
    if (floors.length === 0) {
      return {repository: null, warnings, fatalError: 'No valid floors were loaded.'};
    }
    const floorsByLevel = new Map<number, FloorPlan>();
    for (const f of floors) floorsByLevel.set(f.level, f);

    const beacons = parseBeacons(registryDto?.beacons ?? [], uuid, floorsByLevel, warnings);

    const repo = new InMemoryRegistryRepository(
      uuid,
      typeof registryDto?.registry_version === 'string'
        ? registryDto.registry_version
        : 'unknown',
      beacons,
      floors,
    );
    return {repository: repo, warnings, fatalError: null};
  },
};
