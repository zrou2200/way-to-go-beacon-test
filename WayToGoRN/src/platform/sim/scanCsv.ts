import {RawReading} from '../../core/model/models';

/** Strict integer parse that throws (like Kotlin's toInt/toLong) on bad input. */
function parseIntStrict(s: string): number {
  if (!/^-?\d+$/.test(s)) throw new Error(`Not an integer: ${s}`);
  return parseInt(s, 10);
}

/** Shared CSV schema for raw-scan logs and replay (Sections 8 & 9). */
export const ScanCsv = {
  HEADER: 'timestamp_ms,uuid,major,minor,rssi,adv_tx_power',

  format(reading: RawReading): string {
    const tx = reading.advertisedTxPower == null ? '' : String(reading.advertisedTxPower);
    return (
      `${reading.timestampMs},${reading.key.uuid},${reading.key.major},` +
      `${reading.key.minor},${reading.rssi},${tx}`
    );
  },

  /** Parses one data line; returns null for the header or malformed rows. */
  parse(line: string): RawReading | null {
    const trimmed = line.trim();
    if (trimmed === '' || trimmed.startsWith('timestamp_ms')) return null;
    const parts = trimmed.split(',');
    if (parts.length < 6) return null;
    try {
      const ts = parseIntStrict(parts[0].trim());
      const uuid = parts[1].trim().replace(/-/g, '').toUpperCase();
      const major = parseIntStrict(parts[2].trim());
      const minor = parseIntStrict(parts[3].trim());
      const rssi = parseIntStrict(parts[4].trim());
      const txRaw = parts[5].trim();
      const tx = txRaw === '' ? null : parseIntStrict(txRaw);
      return {key: {uuid, major, minor}, rssi, advertisedTxPower: tx, timestampMs: ts};
    } catch {
      return null;
    }
  },
};
