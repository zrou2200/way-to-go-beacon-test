import {BeaconKey} from '../../core/model/models';
import {makeBeaconKey} from '../../core/util/beaconKey';

/** Result of parsing an iBeacon manufacturer-data payload. */
export interface IBeaconFrame {
  readonly uuid: string;
  readonly major: number;
  readonly minor: number;
  readonly txPower: number;
}

const TYPE_IBEACON = 0x02;
const LENGTH_IBEACON = 0x15;
const MIN_LENGTH = 23;

type Bytes = Uint8Array | number[];

function u8(data: Bytes, i: number): number {
  return data[i] & 0xff;
}

function signedByte(value: number): number {
  const x = value & 0xff;
  return x >= 128 ? x - 256 : x;
}

function formatUuid(data: Bytes, offset: number): string {
  let hex = '';
  for (let i = 0; i < 16; i++) {
    hex += u8(data, offset + i).toString(16).padStart(2, '0').toUpperCase();
    if (i === 3 || i === 5 || i === 7 || i === 9) hex += '-';
  }
  return hex;
}

/**
 * Parses the Apple iBeacon layout (Section 6.3) from the manufacturer-specific
 * data for company id 0x004C (the bytes *after* the two company-id bytes).
 * Returns null for anything malformed.
 *
 * | Offset | Len | Field                          |
 * |--------|-----|--------------------------------|
 * | 0      | 1   | Type = 0x02                    |
 * | 1      | 1   | Length = 0x15 (21)             |
 * | 2      | 16  | Proximity UUID (big-endian)    |
 * | 18     | 2   | Major (big-endian, unsigned)   |
 * | 20     | 2   | Minor (big-endian, unsigned)   |
 * | 22     | 1   | Measured TX power (signed dBm) |
 */
export const IBeaconParser = {
  parse(data: Bytes | null | undefined): IBeaconFrame | null {
    if (data == null || data.length < MIN_LENGTH) return null;
    if (u8(data, 0) !== TYPE_IBEACON) return null;
    if (u8(data, 1) !== LENGTH_IBEACON) return null;

    const uuid = formatUuid(data, 2);
    const major = (u8(data, 18) << 8) | u8(data, 19);
    const minor = (u8(data, 20) << 8) | u8(data, 21);
    const txPower = signedByte(data[22]); // signed

    return {uuid, major, minor, txPower};
  },

  /** Convenience: parse into a [BeaconKey] (uuid uppercased, hyphenated). */
  parseKey(data: Bytes | null | undefined): [BeaconKey, number] | null {
    const frame = this.parse(data);
    if (frame == null) return null;
    return [makeBeaconKey(frame.uuid, frame.major, frame.minor), frame.txPower];
  },
};
