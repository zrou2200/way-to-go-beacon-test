import React, {useCallback, useEffect, useState} from 'react';
import {
  Linking,
  PermissionsAndroid,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {Colors} from '../theme/theme';

/** Runtime permissions required for BLE scanning (Section 6.1). */
async function requestAndroidPermissions(): Promise<boolean> {
  const perms: string[] = [PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION];
  if (Number(Platform.Version) >= 31) {
    perms.unshift(PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN);
  }
  const result = await PermissionsAndroid.requestMultiple(perms);
  return perms.every(p => result[p] === PermissionsAndroid.RESULTS.GRANTED);
}

interface Props {
  onGranted: () => void;
  onDenied: () => void;
  children: React.ReactNode;
}

/**
 * Explains why Bluetooth + location are needed, requests them, and handles
 * "denied"/"don't ask again" (deep link to app settings). On iOS, CoreLocation
 * authorization is requested by the native ranging module, so the gate passes through.
 */
export function PermissionGate({onGranted, onDenied, children}: Props): React.JSX.Element {
  const [granted, setGranted] = useState(Platform.OS !== 'android');
  const [permanentlyDenied, setPermanentlyDenied] = useState(false);

  const request = useCallback(async () => {
    if (Platform.OS !== 'android') {
      setGranted(true);
      return;
    }
    const ok = await requestAndroidPermissions();
    setGranted(ok);
    if (!ok) {
      setPermanentlyDenied(true);
      onDenied();
    }
  }, [onDenied]);

  useEffect(() => {
    if (granted) onGranted();
    else void request();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [granted]);

  if (granted) return <>{children}</>;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Location &amp; Bluetooth needed</Text>
      <Text style={styles.body}>
        WayToGo scans for nearby Bluetooth beacons to show your position on the floor plan.
        Android requires Bluetooth and location permissions to detect beacons. Your location
        is never stored or sent anywhere.
      </Text>
      {permanentlyDenied ? (
        <TouchableOpacity style={styles.button} onPress={() => Linking.openSettings()}>
          <Text style={styles.buttonText}>Open app settings</Text>
        </TouchableOpacity>
      ) : (
        <TouchableOpacity style={styles.button} onPress={() => void request()}>
          <Text style={styles.buttonText}>Grant permissions</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, padding: 24, alignItems: 'center', justifyContent: 'center'},
  title: {fontSize: 22, fontWeight: '600', textAlign: 'center'},
  body: {textAlign: 'center', marginVertical: 16, color: Colors.onSurface},
  button: {backgroundColor: Colors.primary, paddingHorizontal: 20, paddingVertical: 12, borderRadius: 24},
  buttonText: {color: Colors.white, fontWeight: '600'},
});
