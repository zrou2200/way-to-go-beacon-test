import {ErrorKind, PositionFix, PositionState} from '../model/models';
import {PositioningConfig} from '../model/positioningConfig';
import {ReadingAggregator} from '../filter/readingAggregator';
import {FloorResolver} from '../position/floorResolver';
import {PositionEstimator} from '../position/positionEstimator';
import {WeightedPositionEstimator} from '../position/weightedPositionEstimator';
import {PositionSmoother} from '../position/positionSmoother';
import {
  ClampToBoundsProcessor,
  PositionPostProcessor,
} from '../position/positionPostProcessor';
import {RegistryRepository} from '../registry/registryRepository';
import {BeaconScanner, ScanSubscription} from '../scan/beaconScanner';
import {Clock, SystemClock} from '../scan/clock';
import {beaconKeyToString} from '../util/beaconKey';
import {RawReading} from '../model/models';
import {Store} from './store';
import {
  DebugSnapshot,
  EMPTY_DEBUG_SNAPSHOT,
  mapReason,
} from './debugSnapshot';

export interface PositioningEngineOptions {
  estimator?: PositionEstimator;
  postProcessors?: PositionPostProcessor[];
  clock?: Clock;
  readingSink?: (reading: RawReading) => void;
}

/**
 * Drives the positioning pipeline (Section 3):
 *   readings -> aggregator -> floor resolver + estimator -> smoother -> post-processors
 * and emits a [PositionState] at ~[PositioningConfig.emitIntervalMs].
 *
 * JavaScript is single-threaded, so — unlike the Kotlin coroutine version that
 * hands readings across a channel — readings are folded straight into the
 * aggregator on arrival and drained by a single `setInterval` ticker.
 */
export class PositioningEngine {
  readonly state = new Store<PositionState>(PositionState.idle());
  readonly debug = new Store<DebugSnapshot>(EMPTY_DEBUG_SNAPSHOT);

  private readonly config: PositioningConfig;
  private readonly registry: RegistryRepository;
  private readonly estimator: PositionEstimator;
  private readonly postProcessors: PositionPostProcessor[];
  private readonly clock: Clock;
  private readonly readingSink?: (reading: RawReading) => void;

  private readonly aggregator: ReadingAggregator;
  private readonly resolver: FloorResolver;
  private readonly smoother: PositionSmoother;

  private lastFix: PositionFix | null = null;
  private lastValidMs = 0;
  private fatal = false;

  private subscription: ScanSubscription | null = null;
  private timer: ReturnType<typeof setInterval> | null = null;
  private readTimestamps: number[] = [];

  constructor(
    registry: RegistryRepository,
    config: PositioningConfig,
    options: PositioningEngineOptions = {},
  ) {
    this.registry = registry;
    this.config = config;
    this.estimator = options.estimator ?? new WeightedPositionEstimator();
    this.postProcessors = options.postProcessors ?? [new ClampToBoundsProcessor()];
    this.clock = options.clock ?? SystemClock;
    this.readingSink = options.readingSink;

    this.aggregator = new ReadingAggregator(config, key => registry.beacon(key));
    this.resolver = new FloorResolver(config);
    this.smoother = new PositionSmoother(config);
  }

  /** Force an error state (e.g. permission denied, invalid registry). */
  onError(kind: ErrorKind): void {
    this.fatal = true;
    this.state.set(PositionState.error(kind));
  }

  /** Clear a forced error and resume searching. */
  clearError(): void {
    this.fatal = false;
    this.state.set(PositionState.searching());
  }

  /** Start the pipeline against [scanner]. Returns a stop function. */
  start(scanner: BeaconScanner): () => void {
    this.stop();
    this.reset();
    if (!this.fatal) this.state.set(PositionState.searching());

    this.readTimestamps = [];
    this.subscription = scanner.start({
      onReading: reading => {
        this.readingSink?.(reading);
        this.aggregator.add(reading);
        this.readTimestamps.push(reading.timestampMs);
      },
      onError: error => {
        this.fatal = true;
        this.state.set(PositionState.error(mapReason(error.reason)));
      },
    });

    this.timer = setInterval(() => this.tick(), this.config.emitIntervalMs);
    return () => this.stop();
  }

  stop(): void {
    if (this.timer != null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.subscription != null) {
      this.subscription.stop();
      this.subscription = null;
    }
  }

  private tick(): void {
    const now = this.clock.nowMs();

    // Scan rate = readings observed in the last second.
    while (this.readTimestamps.length > 0 && this.readTimestamps[0] < now - 1000) {
      this.readTimestamps.shift();
    }
    const scanRate = this.readTimestamps.length;

    const observations = this.aggregator.observations(now);
    const floor = this.resolver.resolve(observations, now);

    const floorObs =
      floor != null ? observations.filter(o => o.beacon.floorLevel === floor) : [];
    const usedKeys = new Set<string>(
      [...floorObs]
        .sort((a, b) => b.filteredRssi - a.filteredRssi)
        .slice(0, this.config.maxBeaconsUsed)
        .map(o => beaconKeyToString(o.beacon.key)),
    );

    const plan = floor != null ? this.registry.floor(floor) : null;
    let newFix: PositionFix | null = null;
    if (plan != null && floorObs.length > 0) {
      const raw = this.estimator.estimate(floorObs, plan, this.config, now);
      if (raw != null) {
        let smoothed = this.smoother.smooth(raw);
        for (const pp of this.postProcessors) smoothed = pp.process(smoothed, plan);
        newFix = smoothed;
      }
    }

    if (!this.fatal) {
      if (newFix != null) {
        this.lastFix = newFix;
        this.lastValidMs = now;
        this.state.set(PositionState.located(newFix));
      } else {
        const last = this.lastFix;
        if (last == null) {
          this.state.set(PositionState.searching());
        } else if (now - this.lastValidMs > this.config.positionStaleMs) {
          this.state.set(PositionState.degraded(last, 'No recent beacon fix'));
        } else {
          this.state.set(PositionState.located(last));
        }
      }
    }

    this.debug.set({
      heard: [...observations].sort((a, b) => b.filteredRssi - a.filteredRssi),
      usedKeys,
      method: newFix?.method ?? this.lastFix?.method ?? null,
      accuracyM: newFix?.accuracyM ?? this.lastFix?.accuracyM ?? null,
      beaconsUsed: newFix?.beaconsUsed ?? 0,
      scanRateHz: scanRate,
      resolvedFloor: floor,
    });
  }

  private reset(): void {
    this.aggregator.reset();
    this.resolver.reset();
    this.smoother.reset();
    this.lastFix = null;
    this.lastValidMs = 0;
  }
}
