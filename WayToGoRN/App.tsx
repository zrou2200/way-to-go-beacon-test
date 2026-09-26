import React, {useEffect, useMemo} from 'react';
import {SafeAreaView, StatusBar, StyleSheet} from 'react-native';
import {AppContainer} from './src/di/appContainer';
import {PermissionGate} from './src/ui/permissions/PermissionGate';
import {MapScreen} from './src/ui/map/MapScreen';
import {Colors} from './src/ui/theme/theme';

function App(): React.JSX.Element {
  const container = useMemo(() => new AppContainer(), []);

  useEffect(() => {
    // Surface non-fatal registry issues (skipped beacons/floors) like the original.
    for (const w of container.registryLoad.warnings) {
      console.warn(`[registry] ${w}`);
    }
    if (container.registryLoad.fatalError) {
      console.error(`[registry] ${container.registryLoad.fatalError}`);
    }
  }, [container]);

  return (
    <SafeAreaView style={styles.root}>
      <StatusBar barStyle="light-content" backgroundColor={Colors.primary} />
      <PermissionGate onGranted={() => {}} onDenied={() => {}}>
        <MapScreen container={container} />
      </PermissionGate>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.white},
});

export default App;
