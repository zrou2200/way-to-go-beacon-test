import {BeaconKey} from '../model/models';

/**
 * JavaScript objects cannot be used as value-equal Map keys the way Kotlin data
 * classes can, so we canonicalize a [BeaconKey] to a string for use in Maps/Sets.
 * UUIDs are already normalized (dash-stripped, uppercased) upstream at parse time.
 */
export function beaconKeyToString(key: BeaconKey): string {
  return `${key.uuid}|${key.major}|${key.minor}`;
}

export function beaconKeyEquals(a: BeaconKey, b: BeaconKey): boolean {
  return a.uuid === b.uuid && a.major === b.major && a.minor === b.minor;
}

export function makeBeaconKey(uuid: string, major: number, minor: number): BeaconKey {
  return {uuid, major, minor};
}
