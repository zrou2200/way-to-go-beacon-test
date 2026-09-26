import {BeaconObservation, ErrorKind, Method} from '../model/models';
import {ScanFailureReason} from '../scan/beaconScanner';

/**
 * Snapshot of live diagnostics for the debug overlay (Section 7.2).
 *
 * `usedKeys` holds canonical beacon-key strings (see `beaconKeyToString`) because
 * JavaScript Sets compare objects by reference; the UI tests membership with the
 * same canonicalization.
 */
export interface DebugSnapshot {
  readonly heard: BeaconObservation[];
  readonly usedKeys: Set<string>;
  readonly method: Method | null;
  readonly accuracyM: number | null;
  readonly beaconsUsed: number;
  readonly scanRateHz: number;
  readonly resolvedFloor: number | null;
}

export const EMPTY_DEBUG_SNAPSHOT: DebugSnapshot = {
  heard: [],
  usedKeys: new Set<string>(),
  method: null,
  accuracyM: null,
  beaconsUsed: 0,
  scanRateHz: 0,
  resolvedFloor: null,
};

export function mapReason(reason: ScanFailureReason): ErrorKind {
  switch (reason) {
    case 'BLUETOOTH_OFF':
      return 'BLUETOOTH_OFF';
    case 'LOCATION_SERVICES_OFF':
      return 'LOCATION_SERVICES_OFF';
    case 'PERMISSION_DENIED':
      return 'PERMISSION_DENIED';
    case 'SCAN_FAILED':
      return 'SCAN_FAILED';
  }
}
