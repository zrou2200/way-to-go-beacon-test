import {NativeEventEmitter, NativeModules} from 'react-native';
import {
  BeaconScanner,
  ScanException,
  ScanFailureReason,
  ScanHandlers,
  ScanSubscription,
} from '../../core/scan/beaconScanner';
import {Clock, SystemClock} from '../../core/scan/clock';
import {makeBeaconKey} from '../../core/util/beaconKey';

const LINKING_ERROR =
  "The 'WayToGoBeacon' native module is not linked. Rebuild the app after adding " +
  'the iOS (CoreLocation) / Android (BLE) modules described in the README.';

const WayToGoBeacon: any = NativeModules.WayToGoBeacon;

interface NativeBeaconEvent {
  uuid: string;
  major: number;
  minor: number;
  rssi: number;
  txPower?: number | null;
}

interface NativeErrorEvent {
  reason?: ScanFailureReason;
  message?: string;
}

function normalizeUuid(uuid: string): string {
  return uuid.trim().replace(/-/g, '').toUpperCase();
}

/**
 * Real scanner (Section 6). Emits [RawReading]s for iBeacon advertisements
 * matching [registryUuid], backed by a native module: CoreLocation ranging on
 * iOS and a BLE manufacturer-data scan on Android. Mirrors the Kotlin
 * `AndroidBeaconScanner`: UUID-filtered, monotonic timestamps, explicit error reasons.
 */
export class NativeBeaconScanner implements BeaconScanner {
  private readonly targetUuid: string;

  constructor(
    registryUuid: string,
    private readonly clock: Clock = SystemClock,
  ) {
    this.targetUuid = normalizeUuid(registryUuid);
  }

  start(handlers: ScanHandlers): ScanSubscription {
    if (WayToGoBeacon == null) {
      handlers.onError(new ScanException('SCAN_FAILED', LINKING_ERROR));
      return {stop: () => {}};
    }

    const emitter = new NativeEventEmitter(WayToGoBeacon);
    const readingSub = emitter.addListener(
      'WayToGoBeacon:reading',
      (event: NativeBeaconEvent) => {
        if (normalizeUuid(event.uuid) !== this.targetUuid) return;
        handlers.onReading({
          key: makeBeaconKey(normalizeUuid(event.uuid), event.major, event.minor),
          rssi: event.rssi,
          advertisedTxPower: event.txPower ?? null,
          timestampMs: this.clock.nowMs(),
        });
      },
    );
    const errorSub = emitter.addListener(
      'WayToGoBeacon:error',
      (event: NativeErrorEvent) => {
        handlers.onError(
          new ScanException(event.reason ?? 'SCAN_FAILED', event.message ?? 'Scan failed'),
        );
      },
    );

    Promise.resolve(WayToGoBeacon.startScan(this.targetUuid)).catch((err: any) => {
      handlers.onError(
        new ScanException('SCAN_FAILED', String(err?.message ?? err ?? 'startScan failed')),
      );
    });

    return {
      stop: () => {
        readingSub.remove();
        errorSub.remove();
        try {
          WayToGoBeacon.stopScan();
        } catch {
          // Native module already torn down; nothing more to do.
        }
      },
    };
  }
}
