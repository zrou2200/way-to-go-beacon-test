import {RawReading} from '../model/models';

/** Reasons a scan cannot proceed, mapped to [ErrorKind]. */
export type ScanFailureReason =
  | 'BLUETOOTH_OFF'
  | 'LOCATION_SERVICES_OFF'
  | 'PERMISSION_DENIED'
  | 'SCAN_FAILED';

export class ScanException extends Error {
  constructor(readonly reason: ScanFailureReason, message: string) {
    super(message);
    this.name = 'ScanException';
  }
}

/** Callbacks a [BeaconScanner] delivers readings and fatal errors through. */
export interface ScanHandlers {
  onReading(reading: RawReading): void;
  onError(error: ScanException): void;
}

/** Cancels an active scan subscription. */
export interface ScanSubscription {
  stop(): void;
}

/**
 * Source of beacon readings. Two implementations exist (Section 3.1): the real
 * native BLE/CoreLocation scanner and the CSV replay scanner. The Kotlin version
 * exposed a cold `Flow<RawReading>`; here we use an explicit subscription so it
 * maps cleanly onto React Native's NativeEventEmitter and timers.
 */
export interface BeaconScanner {
  /**
   * Begin scanning. Readings/errors are delivered via [handlers]; the returned
   * subscription stops scanning when [ScanSubscription.stop] is called.
   * Implementations surface fatal problems via [ScanHandlers.onError] with a
   * [ScanException] so the engine can map it to an error state.
   */
  start(handlers: ScanHandlers): ScanSubscription;
}
