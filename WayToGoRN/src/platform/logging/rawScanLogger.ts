import {RawReading} from '../../core/model/models';
import {ScanCsv} from '../sim/scanCsv';

/**
 * Raw-scan logging (Section 8): appends every reading to a CSV in app-private
 * storage, capped at 20 MB with single-generation rotation. Feeds later
 * calibration/fingerprinting and the replay scanner.
 *
 * `react-native-fs` is required lazily so the rest of the app still runs (with
 * logging disabled) if the dependency is absent.
 */
const MAX_BYTES = 20 * 1024 * 1024;

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let RNFS: any = null;
try {
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  RNFS = require('react-native-fs');
} catch {
  RNFS = null;
}

export class RawScanLogger {
  private enabled = false;
  private headerWritten = false;
  private pending: string[] = [];
  private flushing = false;

  private readonly dir: string | null = RNFS ? `${RNFS.DocumentDirectoryPath}/scan_logs` : null;
  private readonly file: string | null = this.dir ? `${this.dir}/scan.csv` : null;
  private readonly prevFile: string | null = this.dir ? `${this.dir}/scan.prev.csv` : null;

  isEnabled(): boolean {
    return this.enabled;
  }

  async setEnabled(value: boolean): Promise<void> {
    this.enabled = value;
    if (value && RNFS && this.dir) {
      try {
        const exists = await RNFS.exists(this.dir);
        if (!exists) await RNFS.mkdir(this.dir);
        if (this.file && !(await RNFS.exists(this.file))) {
          await RNFS.writeFile(this.file, ScanCsv.HEADER + '\n', 'utf8');
        }
        this.headerWritten = true;
      } catch {
        // Storage unavailable; disable to avoid repeated failures.
        this.enabled = false;
      }
    }
  }

  /** Current log file path (for the "Export scan log" share action), or null. */
  currentFile(): string | null {
    return this.file;
  }

  log(reading: RawReading): void {
    if (!this.enabled || !RNFS || !this.file) return;
    this.pending.push(ScanCsv.format(reading));
    void this.flush();
  }

  private async flush(): Promise<void> {
    if (this.flushing || !RNFS || !this.file) return;
    this.flushing = true;
    try {
      if (!this.headerWritten) await this.setEnabled(true);
      while (this.pending.length > 0) {
        const batch = this.pending.splice(0, this.pending.length).join('\n') + '\n';
        await RNFS.appendFile(this.file, batch, 'utf8');
        await this.rotateIfNeeded();
      }
    } catch {
      // Best-effort logging; drop the batch on error.
    } finally {
      this.flushing = false;
    }
  }

  private async rotateIfNeeded(): Promise<void> {
    if (!RNFS || !this.file || !this.prevFile) return;
    try {
      const stat = await RNFS.stat(this.file);
      if (Number(stat.size) < MAX_BYTES) return;
      if (await RNFS.exists(this.prevFile)) await RNFS.unlink(this.prevFile);
      await RNFS.moveFile(this.file, this.prevFile);
      await RNFS.writeFile(this.file, ScanCsv.HEADER + '\n', 'utf8');
    } catch {
      // Ignore rotation failures.
    }
  }
}
