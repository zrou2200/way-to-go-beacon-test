# WayToGo — BLE Beacon Indoor Positioning (v1)

Android app that scans for Feasycom FSC-BP108 **iBeacon** advertisements, estimates
the user's **floor** and **(x, y) position in meters**, and shows a moving dot with an
accuracy circle on the floor plan. Implements the v1 spec (scanning, filtering,
distance/floor/position estimation, smoothing, map UI, debug overlay, raw-scan logging,
and CSV replay).

- **Platform:** Android, Kotlin, Jetpack Compose, `minSdk 26`, `compileSdk 34`.
- **Positioning core** (`com.waytogo.core.*`) is **pure Kotlin, no Android imports**, and
  is unit-tested on the JVM.

## Project layout

```
app/src/main/kotlin/com/waytogo/
  core/            # pure Kotlin (JVM-testable)
    model/         # data classes + PositioningConfig
    filter/        # ReadingAggregator (windowed median)
    distance/      # DistanceEstimator (log-distance path loss)
    position/      # FloorResolver, PositionEstimator, PositionSmoother, post-processors, Router
    registry/      # RegistryRepository, RegistryParser, PositioningConfigParser
    scan/          # BeaconScanner interface + Clock
    engine/        # PositioningRepository (StateFlow<PositionState>) + DebugSnapshot
  platform/
    ble/           # AndroidBeaconScanner, IBeaconParser
    sim/           # ReplayBeaconScanner (CSV replay), ScanCsv
    logging/       # RawScanLogger (CSV, rotation)
  ui/
    map/           # MapScreen, MapViewModel, FloorPlanCanvas, FloorImages
    debug/         # DebugOverlay
    permissions/   # PermissionGate
    theme/         # Compose theme
  di/              # AppContainer (manual DI)
app/src/main/assets/  # beacon_registry.json, floors.json, positioning_config.json, floorplans/, sample_walk.csv
app/src/test/kotlin/  # JVM unit tests (Section 10.1)
```

## Build & run

Requires the Android SDK (`compileSdk 34`) and JDK 17.

1. Create `local.properties` with your SDK path:
   ```
   sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```
2. Build / install:
   ```
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```
3. Run the unit tests (the positioning core):
   ```
   ./gradlew test
   ```

> The pure-Kotlin core does not need the Android SDK to be tested. The bundled tests in
> `app/src/test` were verified green on the JVM (31 tests).

## Using the app

- On first launch (Live scanner), `PermissionGate` explains why **Bluetooth** and
  **location** are needed, then requests them. If permanently denied, it deep-links to
  app settings. Bluetooth-off / location-off states show a banner with an enable action.
- The dot follows your resolved floor automatically. Tap another floor chip to inspect it
  (the dot is hidden when you're not on that floor); a **"You are on floor N"** chip snaps
  you back. Pinch to zoom, drag to pan; the **recenter** FAB restores follow mode.

## Debug menu (top-right ⋮)

- **Show/Hide debug overlay** — also toggled by long-pressing the status banner. Lists each
  heard beacon (filtered RSSI, sample count, distance, top-K), plus method, accuracy,
  beacons used, and scan rate. Registered beacons are drawn on the plan.
- **Start/Stop raw logging** — writes every reading to CSV (see below).
- **Export scan log** — shares the current CSV via the system share sheet.
- **Scanner: Live / Replay 1x/2x/4x** — switch between the real BLE scanner and replaying
  `assets/sample_walk.csv`, so the app runs without hardware.

## Updating the registry and floor plans

- **Beacons:** edit `app/src/main/assets/beacon_registry.json`. Keys are `(uuid, major,
  minor)`; `major` = floor level, `minor` = beacon number. Coordinates are **meters** in
  the floor's local system (origin top-left, +x right, +y down). Only `status: "active"`
  beacons are used. Invalid entries (duplicates, missing fields, out-of-bounds) are skipped
  with a logged warning — the app never crashes.
- **Floors:** edit `app/src/main/assets/floors.json` and drop the matching image into
  `app/src/main/assets/floorplans/`. `pixels_per_meter = image_width_px / width_m` must be
  equal on both axes; a mismatch is logged at load time.
- **Tunables:** edit `app/src/main/assets/positioning_config.json` (all Section 5 parameters;
  any omitted field falls back to its spec default).

## Raw-scan logging & replay

- Logs are written to app-private storage (`filesDir/scan_logs/scan.csv`) with columns
  `timestamp_ms,uuid,major,minor,rssi,adv_tx_power`, capped at 20 MB with single-generation
  rotation. This data feeds later calibration/fingerprinting and the replay scanner.
- To reproduce a walk: export a log, place it as `assets/sample_walk.csv` (or extend the
  replay source), and choose **Scanner: Replay** from the debug menu.

## Out of scope (v1)

Routing, destination search, fingerprinting, background scanning, backend sync, and
sensor fusion are not implemented; clean extension interfaces exist (`PositionEstimator`,
`PositionPostProcessor`, `Router`). See `DECISIONS.md`.
