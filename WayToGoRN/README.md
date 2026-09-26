# WayToGo (React Native) — BLE Beacon Indoor Positioning, v1

React Native / TypeScript port of the Android WayToGo app. Scans for Feasycom
FSC-BP108 **iBeacon** advertisements, estimates the user's **floor** and
**(x, y) position in meters**, and shows a moving dot with an accuracy circle on
the floor plan. It implements the same v1 spec as the original (scanning,
filtering, distance/floor/position estimation, smoothing, map UI, debug overlay,
raw-scan logging, and CSV replay).

- **Platform:** React Native `0.74`, TypeScript, iOS + Android.
- **Positioning core** (`src/core/**`) is **pure TypeScript, no React Native
  imports**, and is unit-tested on Node with Jest — the direct analogue of the
  original's JVM-tested Kotlin core.

## Why the split (Kotlin → TypeScript)

The original core is Android-import-free pure Kotlin behind a single
`BeaconScanner` seam. That seam is exactly what a native module feeds, so the
core was re-implemented 1:1 in TypeScript and the native layer reduced to a thin
BLE/CoreLocation bridge. See `DECISIONS.md`.

## Project layout

```
WayToGoRN/
  src/
    core/              # pure TypeScript (Node/Jest-testable) — mirrors com.waytogo.core
      model/           # models.ts, floorPlan.ts, positioningConfig.ts
      filter/          # readingAggregator.ts (windowed median)
      distance/        # distanceEstimator.ts (log-distance path loss)
      position/        # floorResolver, weightedPositionEstimator, positionSmoother,
                       #   positionPostProcessor, router, positionEstimator (interface)
      registry/        # registryRepository, registryParser, positioningConfigParser
      scan/            # beaconScanner (interface + ScanException) + clock
      engine/          # positioningEngine (StateFlow -> Store), debugSnapshot, store
      util/            # beaconKey (canonical Map/Set key)
    platform/
      ble/             # ibeaconParser.ts, nativeBeaconScanner.ts (JS bridge)
      sim/             # scanCsv.ts, replayBeaconScanner.ts
      logging/         # rawScanLogger.ts (react-native-fs, rotation)
    ui/
      map/             # MapScreen, FloorPlanCanvas (react-native-svg), useFloorImages
      debug/           # DebugOverlay
      permissions/     # PermissionGate
      theme/           # theme.ts
      hooks/           # useStore (subscribe to a Store)
    di/                # appContainer.ts (manual DI)
    assets/            # beacon_registry.json, floors.json, positioning_config.json,
                       #   sampleWalk.ts (+ sample_walk.csv), floorplans/*.png
  android/app/src/main/java/com/waytogorn/   # WayToGoBeaconModule.kt + package
  ios/WayToGoRN/                             # WayToGoBeacon.swift + .m + Info.plist.additions
  App.tsx, index.js, package.json, tsconfig*.json, jest.config.js, metro/babel config
```

## Getting a runnable app

This repository contains the **application source, assets, native modules, and
tests** — everything except the machine-generated iOS/Android shells. Generate
those once and drop the source in:

1. Scaffold a bare RN 0.74 TypeScript app with the same name:
   ```
   npx @react-native-community/cli@latest init WayToGoRN --version 0.74.5
   ```
2. Copy this repo's `App.tsx`, `src/`, `index.js`, `package.json`,
   `tsconfig.json`, `jest.config.js`, `metro.config.js`, and `babel.config.js`
   over the generated ones, then `npm install`.
3. Add the native beacon module:
   - **Android:** copy `android/app/src/main/java/com/waytogorn/*.kt` into the
     generated `android/app/src/main/java/<your.package>/` (fix the `package`
     line), and register it in `MainApplication` by adding
     `add(WayToGoBeaconPackage())` to `getPackages()`. Merge the permissions from
     the reference `android/app/src/main/AndroidManifest.xml`.
   - **iOS:** add `ios/WayToGoRN/WayToGoBeacon.swift` and `WayToGoBeacon.m` to the
     Xcode target (accept the bridging header prompt), merge the keys in
     `ios/WayToGoRN/Info.plist.additions.txt` into `Info.plist`, then
     `cd ios && pod install`.
4. Install the two runtime libraries and rebuild:
   ```
   npm install react-native-svg react-native-fs
   npx pod-install        # iOS
   ```
5. Run:
   ```
   npm run android
   npm run ios
   ```

> **No hardware?** Open the ⋮ menu → **Scanner: Replay 1x** to play the bundled
> `sampleWalk.ts` walk, exactly like the original.

## Testing (the positioning core)

The pure-TS core is tested with Jest — the 31 tests from the original JVM suite,
ported 1:1:

```
npm test
```

If you have not installed the full RN toolchain yet, the tests also run in an
isolated harness that needs only TypeScript + Jest:

```
cd .verify && npm install && npx jest --config jest.config.js
```

Type-check the core in isolation with `npm run typecheck:core`.

## Using the app

- On first launch, `PermissionGate` explains why **Bluetooth** and **location**
  are needed and requests them (Android runtime permissions; on iOS CoreLocation
  prompts when ranging starts). If permanently denied, it deep-links to settings.
- The dot follows your resolved floor automatically. Tap another floor chip to
  inspect it (the dot is hidden when you're not on that floor); a **"You are on
  floor N"** chip snaps you back. Pinch to zoom, drag to pan; the **recenter** FAB
  restores follow mode.

## Debug menu (top-right ⋮)

- **Show/Hide debug overlay** — also toggled by long-pressing the status banner.
  Lists each heard beacon (filtered RSSI, sample count, distance, top-K), plus
  method, accuracy, beacons used, and scan rate. Registered beacons are drawn on
  the plan.
- **Start/Stop raw logging** — writes every reading to CSV (`react-native-fs`).
- **Export scan log** — shares the current CSV via the system share sheet.
- **Scanner: Live / Replay 1x/2x/4x** — switch between the native BLE scanner and
  replaying the bundled walk, so the app runs without hardware.

## Updating the registry and floor plans

- **Beacons:** edit `src/assets/beacon_registry.json`. Keys are `(uuid, major,
  minor)`; `major` = floor level, `minor` = beacon number. Coordinates are
  **meters** (origin top-left, +x right, +y down). Only `status: "active"`
  beacons are used; invalid entries (duplicates, missing fields, out-of-bounds)
  are skipped with a logged warning — the app never crashes.
- **Floors:** edit `src/assets/floors.json`, drop the image into
  `src/assets/floorplans/`, and add its `require()` to
  `src/ui/map/useFloorImages.ts`. `pixels_per_meter = image_width_px / width_m`
  must match on both axes; a mismatch is logged at load time.
- **Tunables:** edit `src/assets/positioning_config.json` (all Section 5
  parameters; any omitted field falls back to its spec default).

## Raw-scan logging & replay

- Logs are written to app-private storage (`DocumentDirectory/scan_logs/scan.csv`)
  with columns `timestamp_ms,uuid,major,minor,rssi,adv_tx_power`, capped at 20 MB
  with single-generation rotation.
- The replay scanner reads the bundled `sampleWalk.ts` (same CSV schema),
  preserving relative timing scaled by 1x/2x/4x.

## Out of scope (v1)

Routing, destination search, fingerprinting, background scanning, backend sync,
and sensor fusion are not implemented; the same clean extension seams exist
(`PositionEstimator`, `PositionPostProcessor`, `Router`). See `DECISIONS.md`.
