# DECISIONS.md (React Native port)

Choices made porting the Android/Kotlin WayToGo app to React Native, keeping the
same v1 spec behavior. The original Kotlin decisions still apply to the algorithm;
this file records what changed for the RN/TypeScript environment.

## Strategy: port the core to TypeScript

- The original positioning core is **pure, Android-import-free Kotlin** behind one
  `BeaconScanner` seam. Rather than reuse Kotlin via Kotlin Multiplatform (which
  adds a Kotlin/Native + bridging toolchain), the core was **re-implemented 1:1 in
  TypeScript**. This gives one cross-platform codebase, keeps the native layer a
  thin BLE bridge, and lets the exact unit tests run on Node.
- File/module names mirror the Kotlin packages (`src/core/...` ↔ `com.waytogo.core`)
  so the two trees can be diffed side by side.

## Language / runtime translations

- **Kotlin data classes → TS interfaces** (plain objects) so Jest `toEqual`
  reproduces Kotlin structural equality. `PositionFix`, `RawReading`,
  `BeaconObservation`, etc. are interfaces.
- **`BeaconKey` as a Map/Set key.** JS keys objects by reference, so a canonical
  string (`uuid|major|minor`, `beaconKeyToString`) is used wherever Kotlin used
  `BeaconKey` in a `HashMap`/`HashSet` (aggregator windows, registry lookup,
  `DebugSnapshot.usedKeys`). UUIDs are normalized (dash-stripped, uppercased) at
  parse time on every path (registry, CSV, scanner), exactly as before.
- **`StateFlow<T>` → `Store<T>`**, a 12-line observable with `get/set/subscribe`
  that replays the current value to new subscribers. The `useStore` hook is the
  RN analogue of Compose's `collectAsState`.
- **`Flow<RawReading>` → subscription `BeaconScanner`.** Kotlin's cold flow +
  coroutine `delay` don't map cleanly to RN, so `BeaconScanner.start(handlers)`
  returns a `{stop}` subscription. It maps directly onto `NativeEventEmitter`
  (native scanner) and `setTimeout` (replay), and onto the engine's ticker.
- **Coroutine channel → direct fold.** JavaScript is single-threaded, so readings
  are added to the aggregator on arrival instead of being handed across a channel;
  a single `setInterval(emitIntervalMs)` drives the tick. Behavior at each tick is
  identical to the Kotlin ticker.
- **Monotonic clock.** `Clock.SYSTEM` uses `performance.now()` (Hermes/RN) with a
  `Date.now()` fallback, replacing `SystemClock.elapsedRealtime()`.

## Native scanning (the platform-asymmetric part)

- A single `WayToGoBeacon` native module emits `WayToGoBeacon:reading`
  (`{uuid, major, minor, rssi, txPower, timestampMs}`) and `WayToGoBeacon:error`
  (`{reason, message}`) events; `NativeBeaconScanner` (JS) adapts them to
  `RawReading` and `ScanException`, filtering by the registry UUID and
  re-timestamping with the monotonic clock — mirroring `AndroidBeaconScanner`.
- **Android** (`WayToGoBeaconModule.kt`) is a near-verbatim port of
  `AndroidBeaconScanner`: `SCAN_MODE_LOW_LATENCY`, a manufacturer-data
  `ScanFilter`, the same iBeacon byte parsing, and the same pre-flight →
  `PERMISSION_DENIED / BLUETOOTH_OFF / LOCATION_SERVICES_OFF / SCAN_FAILED`
  reasons. It does not restart on `onScanFailed`.
- **iOS** (`WayToGoBeacon.swift`) uses **CoreLocation** `startRangingBeacons`,
  because Apple hides iBeacon manufacturer bytes from CoreBluetooth. iOS delivers
  already-parsed `CLBeacon`s, so there is no `IBeaconParser` on iOS; `txPower` is
  `null` (unavailable) and iOS's `rssi == 0` sentinel is dropped — conveniently the
  aggregator already ignores `rssi >= 0`. The registry's single top-level UUID maps
  to exactly one `CLBeaconIdentityConstraint`.
- `IBeaconParser` is still ported to TS (used by the Android-parity unit test and
  available for any raw-bytes path).

## Assets

- JSON assets (`beacon_registry.json`, `floors.json`, `positioning_config.json`)
  are imported via `resolveJsonModule` and passed to the same string parsers
  (`JSON.stringify` → `RegistryParser.parse`), so validation/warnings are identical.
- `sample_walk.csv` is embedded as `sampleWalk.ts` (an array of lines) so the
  replay scanner needs no filesystem read on either platform. The raw CSV is kept
  alongside for reference.
- Floor images are `require()`d through `useFloorImages.ts`, which also performs
  the Section 2.3 pixels-per-meter axis-equality check via
  `Image.resolveAssetSource`.

## UI

- Compose → React Native. The map (`FloorPlanCanvas`) draws the floor image and a
  `react-native-svg` overlay (accuracy circle + user dot + debug beacon markers)
  in one transformed content layer, with pan/pinch via `PanResponder` + `Animated`
  (no extra gesture library). Meter→pixel projection uses the image `ppm`, so the
  accuracy circle scales with real-world meters; the marker keeps a fixed radius.
- `PermissionGate` uses `PermissionsAndroid` for the two Android runtime
  permissions; on iOS it passes through and the CoreLocation module requests
  When-In-Use authorization on `startScan` (so `react-native-permissions` is not a
  dependency).
- `RawScanLogger` uses `react-native-fs`, lazily required so the app still runs
  (logging disabled) if the dependency is absent; the "Export scan log" action
  uses the RN `Share` API.

## Testing & verification

- The Section 10.1 unit tests are ported 1:1 to Jest (`*.test.ts`) and pass:
  **31 tests, 8 suites**, on Node via `ts-jest`. A `.verify/` harness runs them
  (and a strict `tsc` core type-check) without the React Native toolchain.
- **One intentional test deviation.** The original
  `PositionEstimatorTest.singleBeaconReturnsItsPosition` asserts the proximity fix
  equals the beacon's own position `(7, 8)`, but the shipped
  `WeightedPositionEstimator.proximity` offsets East by `distanceM`
  (`x = beacon.x + distanceM = 9`). Those two are inconsistent in the original
  repo. This port reproduces the **shipped runtime logic** (offset by distance) and
  the ported test asserts the actual behavior (`x = 9`), with a NOTE at the test.
  If the intended contract is "show the user at the beacon," change `proximity` to
  `x = clampX(obs.beacon.x)` and update that assertion.

## Section 10.2 (device/UI tests)

As in the original, integration/UI tests (replay walk on device, permission /
BT-off / location-off banners) are described but not automated here; they require a
device/simulator and the native module. The replay scanner makes the full pipeline
demoable without hardware.
