import {RawReading} from '../../core/model/models';
import {
  BeaconScanner,
  ScanHandlers,
  ScanSubscription,
} from '../../core/scan/beaconScanner';
import {Clock, SystemClock} from '../../core/scan/clock';
import {ScanCsv} from './scanCsv';

/**
 * Replays a CSV log (same schema as [ScanCsv]) as a [BeaconScanner], preserving
 * the original relative timing scaled by [speed] (Section 9). Lets the app be
 * developed and demoed without hardware.
 *
 * The Kotlin version used coroutine `delay`; here we schedule each reading with
 * `setTimeout` at its offset from the first row, re-timestamping against the
 * monotonic clock so windowing stays consistent.
 */
export class ReplayBeaconScanner implements BeaconScanner {
  constructor(
    private readonly linesProvider: () => Iterable<string>,
    private readonly speed: number = 1.0,
    private readonly loop: boolean = true,
    private readonly clock: Clock = SystemClock,
  ) {}

  start(handlers: ScanHandlers): ScanSubscription {
    const safeSpeed = this.speed <= 0 ? 1.0 : this.speed;
    let stopped = false;
    const timers: Array<ReturnType<typeof setTimeout>> = [];

    const runPass = (): void => {
      if (stopped) return;
      const parsed: RawReading[] = [];
      for (const line of this.linesProvider()) {
        const reading = ScanCsv.parse(line);
        if (reading != null) parsed.push(reading);
      }
      if (parsed.length === 0) {
        if (this.loop && !stopped) {
          timers.push(setTimeout(runPass, 1000)); // avoid a tight loop on empty input
        }
        return;
      }

      const baseTs = parsed[0].timestampMs;
      let lastOffset = 0;
      for (const reading of parsed) {
        const offset = Math.max(0, Math.trunc((reading.timestampMs - baseTs) / safeSpeed));
        lastOffset = Math.max(lastOffset, offset);
        timers.push(
          setTimeout(() => {
            if (stopped) return;
            handlers.onReading({...reading, timestampMs: this.clock.nowMs()});
          }, offset),
        );
      }

      if (this.loop) {
        const gap = Math.max(1, Math.trunc(1000 / safeSpeed));
        timers.push(setTimeout(runPass, lastOffset + gap));
      }
    };

    runPass();
    return {
      stop: () => {
        stopped = true;
        for (const t of timers) clearTimeout(t);
      },
    };
  }
}
