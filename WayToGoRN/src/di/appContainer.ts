import registryJson from '../assets/beacon_registry.json';
import floorsJson from '../assets/floors.json';
import positioningConfigJson from '../assets/positioning_config.json';
import {SAMPLE_WALK_LINES} from '../assets/sampleWalk';

import {PositioningConfig} from '../core/model/positioningConfig';
import {PositioningConfigParser} from '../core/registry/positioningConfigParser';
import {RegistryLoad} from '../core/registry/registryRepository';
import {RegistryParser} from '../core/registry/registryParser';
import {PositioningEngine} from '../core/engine/positioningEngine';
import {BeaconScanner} from '../core/scan/beaconScanner';
import {SystemClock} from '../core/scan/clock';
import {NativeBeaconScanner} from '../platform/ble/nativeBeaconScanner';
import {ReplayBeaconScanner} from '../platform/sim/replayBeaconScanner';
import {RawScanLogger} from '../platform/logging/rawScanLogger';

/** Scanner source selection, toggled from the debug menu (Section 9). */
export type ScannerMode =
  | {readonly kind: 'live'}
  | {readonly kind: 'replay'; readonly speed: number};

export const ScannerMode = {
  live: {kind: 'live'} as ScannerMode,
  replay: (speed = 1.0): ScannerMode => ({kind: 'replay', speed}),
};

/**
 * Manual dependency container. Loads and validates the bundled assets once,
 * wires the positioning pipeline, and provides scanner implementations.
 */
export class AppContainer {
  readonly config: PositioningConfig = PositioningConfigParser.parse(
    JSON.stringify(positioningConfigJson),
  );

  readonly registryLoad: RegistryLoad = RegistryParser.parse(
    JSON.stringify(registryJson),
    JSON.stringify(floorsJson),
  );

  readonly logger = new RawScanLogger();

  createEngine(): PositioningEngine | null {
    const repo = this.registryLoad.repository;
    if (repo == null) return null;
    return new PositioningEngine(repo, this.config, {
      clock: SystemClock,
      readingSink: reading => this.logger.log(reading),
    });
  }

  createScanner(mode: ScannerMode): BeaconScanner {
    if (mode.kind === 'replay') {
      return new ReplayBeaconScanner(() => SAMPLE_WALK_LINES, mode.speed);
    }
    return new NativeBeaconScanner(this.registryLoad.repository?.uuid ?? '');
  }
}
