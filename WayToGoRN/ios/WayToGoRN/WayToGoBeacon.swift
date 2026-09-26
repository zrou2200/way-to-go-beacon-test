import Foundation
import CoreLocation

/**
 * React Native bridge for iBeacon ranging on iOS (Section 6). iOS delivers
 * *already-parsed* CLBeacons via CoreLocation (CoreBluetooth cannot see iBeacon
 * frames), so there is no manufacturer-data parsing here. Emits
 * `WayToGoBeacon:reading` ({uuid, major, minor, rssi, txPower}) and
 * `WayToGoBeacon:error` events, matching the JS NativeBeaconScanner contract.
 */
@objc(WayToGoBeacon)
class WayToGoBeacon: RCTEventEmitter, CLLocationManagerDelegate {

  private let manager = CLLocationManager()
  private var constraint: CLBeaconIdentityConstraint?
  private var region: CLBeaconRegion?
  private var hasListeners = false
  private var pendingUuid: String?

  override init() {
    super.init()
    manager.delegate = self
  }

  override static func requiresMainQueueSetup() -> Bool { true }

  override func supportedEvents() -> [String]! {
    ["WayToGoBeacon:reading", "WayToGoBeacon:error"]
  }

  override func startObserving() { hasListeners = true }
  override func stopObserving() { hasListeners = false }

  @objc(startScan:resolver:rejecter:)
  func startScan(_ uuidStr: String,
                 resolver resolve: @escaping RCTPromiseResolveBlock,
                 rejecter reject: @escaping RCTPromiseRejectBlock) {
    guard let uuid = UUID(uuidString: uuidStr) else {
      emitError("SCAN_FAILED", "Invalid UUID: \(uuidStr)")
      reject("SCAN_FAILED", "Invalid UUID", nil)
      return
    }
    pendingUuid = uuidStr

    switch manager.authorizationStatus {
    case .notDetermined:
      manager.requestWhenInUseAuthorization()
    case .denied, .restricted:
      emitError("PERMISSION_DENIED", "Location permission denied")
      reject("PERMISSION_DENIED", "Location permission denied", nil)
      return
    default:
      break
    }

    let constraint = CLBeaconIdentityConstraint(uuid: uuid)
    let region = CLBeaconRegion(beaconIdentityConstraint: constraint, identifier: "WayToGo")
    self.constraint = constraint
    self.region = region
    manager.startRangingBeacons(satisfying: constraint)
    resolve(true)
  }

  @objc func stopScan() {
    if let constraint = constraint {
      manager.stopRangingBeacons(satisfying: constraint)
    }
    constraint = nil
    region = nil
  }

  func locationManager(_ manager: CLLocationManager,
                       didRange beacons: [CLBeacon],
                       satisfying constraint: CLBeaconIdentityConstraint) {
    guard hasListeners else { return }
    let nowMs = Date().timeIntervalSince1970 * 1000.0
    for beacon in beacons where beacon.rssi != 0 {
      sendEvent(withName: "WayToGoBeacon:reading", body: [
        "uuid": beacon.uuid.uuidString,
        "major": beacon.major.intValue,
        "minor": beacon.minor.intValue,
        "rssi": beacon.rssi,
        "txPower": NSNull(),
        "timestampMs": nowMs,
      ])
    }
  }

  func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    switch manager.authorizationStatus {
    case .authorizedWhenInUse, .authorizedAlways:
      if let constraint = constraint {
        manager.startRangingBeacons(satisfying: constraint)
      }
    case .denied, .restricted:
      emitError("PERMISSION_DENIED", "Location permission denied")
    default:
      break
    }
  }

  private func emitError(_ reason: String, _ message: String) {
    guard hasListeners else { return }
    sendEvent(withName: "WayToGoBeacon:error", body: ["reason": reason, "message": message])
  }
}
