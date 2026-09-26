import React from 'react';
import {ScrollView, StyleSheet, Text, View} from 'react-native';
import {DebugSnapshot} from '../../core/engine/debugSnapshot';
import {beaconKeyToString} from '../../core/util/beaconKey';
import {Colors} from '../theme/theme';

function padEnd(s: string, n: number): string {
  return s.length >= n ? s.slice(0, n) : s + ' '.repeat(n - s.length);
}
function padStart(s: string, n: number): string {
  return s.length >= n ? s : ' '.repeat(n - s.length) + s;
}

/** Diagnostics panel (Section 7.2). */
export function DebugOverlay({snapshot}: {snapshot: DebugSnapshot}): React.JSX.Element {
  const acc = snapshot.accuracyM != null ? `${snapshot.accuracyM.toFixed(1)}m` : '-';
  const header =
    `method=${snapshot.method ?? '-'}  acc=${acc}  ` +
    `used=${snapshot.beaconsUsed}  floor=${snapshot.resolvedFloor ?? '-'}  ` +
    `rate=${snapshot.scanRateHz.toFixed(1)}/s`;

  return (
    <View style={styles.card}>
      <Text style={styles.headerLine}>{header}</Text>
      <View style={styles.divider} />
      <Text style={styles.colHeader}>label        rssi  n   dist  topK</Text>
      <ScrollView style={styles.list}>
        {snapshot.heard.map(obs => {
          const ks = beaconKeyToString(obs.beacon.key);
          const inTopK = snapshot.usedKeys.has(ks);
          const row =
            `${padEnd(obs.beacon.label, 12)} ` +
            `${padStart(obs.filteredRssi.toFixed(0), 5)} ` +
            `${padStart(String(obs.sampleCount), 2)} ` +
            `${padStart(obs.distanceM.toFixed(1), 5)}  ` +
            `${inTopK ? 'yes' : ''}`;
          return (
            <Text key={ks} style={[styles.row, inTopK && styles.topK]}>
              {row}
            </Text>
          );
        })}
      </ScrollView>
    </View>
  );
}

const mono = 'monospace';
const styles = StyleSheet.create({
  card: {
    alignSelf: 'stretch',
    margin: 8,
    padding: 12,
    borderRadius: 8,
    backgroundColor: Colors.debugBg,
  },
  headerLine: {color: Colors.debugText, fontFamily: mono, fontWeight: 'bold', fontSize: 12},
  divider: {height: 1, backgroundColor: 'rgba(255,255,255,0.2)', marginVertical: 6},
  colHeader: {color: Colors.debugHeader, fontFamily: mono, fontSize: 12},
  list: {maxHeight: 220},
  row: {color: Colors.debugText, fontFamily: mono, fontSize: 12},
  topK: {color: Colors.debugTopK},
});
