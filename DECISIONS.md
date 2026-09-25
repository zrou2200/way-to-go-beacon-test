# DECISIONS.md

Assumptions and choices made where the spec (BLE Beacon Indoor Positioning v1) was silent.
Requirement keywords followed as specified.

## Architecture & modules

- **Single Gradle module (`:app`)** rather than separate Gradle modules. The spec's package
  layout (Section 3.1) is preserved exactly under `com.waytogo.*`. The positioning core is
  kept free of Android imports so `./gradlew test` exercises it on the JVM, satisfying the
  "pure Kotlin, unit-testable" requirement without the overhead of extra modules.
- **`BeaconScanner` interface lives in `core/scan`** (not `platform`). It depends only on
  `kotlinx.coroutines.Flow` + `RawReading` (no Android), which keeps the engine and any
  fake scanners JVM-testable. The two required implementations still live in `platform`
  (`AndroidBeaconScanner`, `ReplayBeaconScanner`).
- **`Clock` abstraction** added to `core/scan` so time-based logic is testable. Android
  supplies `SystemClock.elapsedRealtime()`; the JVM default uses `System.nanoTime()`.

## Positioning core

- **Distance model** uses the registry `rssi_1m` as the reference and falls back to the
  advertised TX-power byte only when the registry value is missing (Section 2.1). In v1 all
  registry beacons carry `rssi_1m`, so the advertised byte is currently only surfaced in
  logs/debug.
- **Median filter** on an even-sized window returns the average of the two middle samples.
- **Trilateration** is weighted Gauss-Newton (weights `1/d^2`), initial guess = weighted
  centroid, max 10 iterations, stop step < 0.01 m. Fallback to centroid on: singular normal
  matrix, non-convergence, NaN/Inf, or a solution > 5 m outside the used beacons' bounding
  box. Final positions are clamped to floor bounds (via the post-processor chain).
- **Accuracy**: TRILATERATION = `max(minAccuracyM, RMS of residuals)` (unweighted RMS);
  CENTROID = `max(minAccuracyM, weightedMeanDistance * 0.5)`; PROXIMITY =
  `max(minAccuracyM, estimatedDistance)`.
- **Jump rejection order**: the raw estimate is first clamped to `maxSpeedMps * dt` from the
  previous position, then the EMA is applied. Net displacement is therefore ≤ `maxSpeedMps *
  dt` (the spec's intent — the dot nudges, never teleports, and never gets stuck).
- **Degraded vs. Located when beacons drop out**: the last fix is kept as `Located` until
  `positionStaleMs` elapses with no new fix, after which the state becomes `Degraded`
  (last dot shown dimmed). This keeps the map from flickering on a single missed tick.
- **Reading hand-off**: incoming readings are pushed through an unlimited `Channel` and
  drained on the single ticker coroutine, so the aggregator/resolver/smoother stay
  single-threaded without explicit locks.

## Registry & floors

- `pixels_per_meter` **axis-equality** validation (Section 2.3) happens at image-decode time
  in `FloorImages` (the pure core has no image dimensions), logging a warning on mismatch
  rather than crashing. Floor JSON validation in the core checks positive dimensions and
  duplicate levels.
- A missing top-level `uuid`, unparseable JSON, or zero valid floors is treated as a **fatal**
  load error → `ErrorKind.REGISTRY_INVALID`. Individual bad beacon/floor entries are skipped
  with warnings.
- Non-`active` beacons are silently excluded (not counted as warnings); structural problems
  (duplicate key, missing field, out-of-bounds, unknown floor) are warned.
- `positioning_config.json` is an **optional** asset; any missing field falls back to the
  spec default, and unparseable config falls back to all defaults.

## Platform / BLE

- Pre-flight checks in `AndroidBeaconScanner` map to explicit `ScanException` reasons
  (permission / Bluetooth-off / location-off / scan-failed), which the repository turns into
  the corresponding `ErrorKind`.
- `onScanFailed` closes the flow with a `SCAN_FAILED` exception instead of restarting, to
  honor "do not restart scans in a tight loop" (including *scanning too frequently*). The UI
  offers a manual retry.
- Scanning is **foreground-only**, tied to the Compose lifecycle (`ON_START`/`ON_STOP`).

## UI

- Marker movement is animated with a 300 ms `tween`. The user dot is a fixed screen-size
  blue disc; the accuracy circle scales with real-world meters and zoom.
- Manual pan/zoom disables follow mode; the recenter FAB (and floor "snap back" chip) restore
  it. When viewing a floor the user is not on, the dot is hidden.
- Debug overlay is toggled by the debug menu **and** a long-press on the status banner.
- Log export uses a `FileProvider` (`${applicationId}.fileprovider`).

## Assets provided

- `beacon_registry.json`: 5 active + 1 inactive beacon on floor 1, 3 on floor 2, plus the
  inactive one to exercise the `status` filter.
- `floors.json`: two floors (60×40 m and 40×30 m) at 20 px/m.
- Placeholder floor images (`floor1.png`, `floor2.png`) are generated as simple corridor
  plans (solid background, corridor band, beacon dots) — no third-party assets.
- `sample_walk.csv`: a synthetic walk down the floor-1 corridor past all 5 beacons, with mild
  Gaussian RSSI noise, ~2.5 Hz per beacon.

## Testing

- Section 10.1 unit tests are implemented and pass on the JVM (31 tests). A `ScanCsv`
  round-trip test was added beyond the spec list.
- Section 10.2 integration/UI tests (replay walk, permission/BT/location banners) are
  described but left as instrumented tests to be run on a device/emulator with the Android
  SDK; they are not part of the JVM `test` task.

## Tooling versions

- Android Gradle Plugin 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00, Gradle 8.9,
  `kotlinx-serialization-json` 1.6.3, `kotlinx-coroutines` 1.8.1.
