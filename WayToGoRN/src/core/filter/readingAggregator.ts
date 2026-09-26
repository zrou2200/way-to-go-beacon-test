import {
  BeaconKey,
  BeaconObservation,
  RawReading,
  RegisteredBeacon,
} from '../model/models';
import {PositioningConfig} from '../model/positioningConfig';
import {DistanceEstimator} from '../distance/distanceEstimator';
import {beaconKeyToString} from '../util/beaconKey';

/**
 * Per-beacon windowed RSSI filtering (Section 5.1).
 *
 * - Keeps a sliding window of (timestampMs, rssi) per beacon key.
 * - Filtered RSSI is the median over the window (robust to outliers).
 * - Beacons with fewer than [PositioningConfig.minSamplesPerBeacon] samples are excluded.
 * - Readings with rssi >= 0 or rssi < -110 are ignored as invalid/sentinel.
 * - Only beacons present in the supplied resolver (known + active) produce observations.
 *
 * Not thread-safe; call from a single positioning dispatcher.
 */
interface Sample {
  readonly timestampMs: number;
  readonly rssi: number;
}

interface Window {
  readonly key: BeaconKey;
  readonly samples: Sample[];
}

export class ReadingAggregator {
  private readonly windows = new Map<string, Window>();

  constructor(
    private readonly config: PositioningConfig,
    private readonly lookup: (key: BeaconKey) => RegisteredBeacon | null,
  ) {}

  add(reading: RawReading): void {
    if (reading.rssi >= 0 || reading.rssi < -110) return;
    if (this.lookup(reading.key) == null) return;
    const ks = beaconKeyToString(reading.key);
    let window = this.windows.get(ks);
    if (window == null) {
      window = {key: reading.key, samples: []};
      this.windows.set(ks, window);
    }
    window.samples.push({timestampMs: reading.timestampMs, rssi: reading.rssi});
  }

  /**
   * Produce observations for the current tick. Expires samples older than
   * windowMs and drops beacons unseen for longer than staleMs.
   */
  observations(nowMs: number): BeaconObservation[] {
    const result: BeaconObservation[] = [];
    const cutoff = nowMs - this.config.windowMs;
    const staleCutoff = nowMs - this.config.staleMs;

    for (const [ks, window] of this.windows) {
      const samples = window.samples;
      while (samples.length > 0 && samples[0].timestampMs < cutoff) {
        samples.shift();
      }
      const lastSeen = samples.length > 0 ? samples[samples.length - 1].timestampMs : null;
      if (lastSeen == null || lastSeen < staleCutoff) {
        this.windows.delete(ks);
        continue;
      }
      if (samples.length < this.config.minSamplesPerBeacon) continue;

      const beacon = this.lookup(window.key);
      if (beacon == null) continue;
      const filteredRssi = median(samples.map(s => s.rssi));
      const distance = DistanceEstimator.estimate(
        filteredRssi,
        beacon.rssi1m,
        this.config.pathLossExponent,
        this.config.minDistanceM,
        this.config.maxDistanceM,
      );
      result.push({
        beacon,
        filteredRssi,
        distanceM: distance,
        sampleCount: samples.length,
        lastSeenMs: lastSeen,
      });
    }
    return result;
  }

  reset(): void {
    this.windows.clear();
  }
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const n = sorted.length;
  const mid = Math.floor(n / 2);
  return n % 2 === 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
}
