import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
  AppState,
  Modal,
  ScrollView,
  Share,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {ErrorKind, PositionState} from '../../core/model/models';
import {DebugSnapshot, EMPTY_DEBUG_SNAPSHOT} from '../../core/engine/debugSnapshot';
import {Store} from '../../core/engine/store';
import {beaconKeyToString} from '../../core/util/beaconKey';
import {AppContainer, ScannerMode} from '../../di/appContainer';
import {useStore} from '../hooks/useStore';
import {Colors} from '../theme/theme';
import {DebugOverlay} from '../debug/DebugOverlay';
import {FloorPlanCanvas} from './FloorPlanCanvas';
import {loadFloorImage} from './useFloorImages';

interface UiState {
  displayedFloorOverride: number | null;
  followMode: boolean;
  debugVisible: boolean;
  loggingEnabled: boolean;
  scannerMode: ScannerMode;
}

function errorText(kind: ErrorKind): string {
  switch (kind) {
    case 'PERMISSION_DENIED':
      return 'Permission needed to scan for beacons';
    case 'BLUETOOTH_OFF':
      return 'Bluetooth is off';
    case 'LOCATION_SERVICES_OFF':
      return 'Location services are off';
    case 'SCAN_FAILED':
      return 'Bluetooth scan failed';
    case 'REGISTRY_INVALID':
      return 'Beacon registry could not be loaded';
  }
}

function bannerText(state: PositionState): string | null {
  switch (state.kind) {
    case 'Searching':
      return 'Looking for beacons\u2026';
    case 'Degraded':
      return 'Signal weak \u2014 showing last known position';
    case 'Idle':
      return 'Idle';
    case 'Error':
      return errorText(state.error);
    case 'Located':
      return null;
  }
}

export function MapScreen({container}: {container: AppContainer}): React.JSX.Element {
  const {engine, stateStore, debugStore} = useMemo(() => {
    const e = container.createEngine();
    if (e != null) return {engine: e, stateStore: e.state, debugStore: e.debug};
    return {
      engine: null,
      stateStore: new Store<PositionState>(PositionState.error('REGISTRY_INVALID')),
      debugStore: new Store<DebugSnapshot>(EMPTY_DEBUG_SNAPSHOT),
    };
  }, [container]);

  const state = useStore(stateStore);
  const debug = useStore(debugStore);

  const [ui, setUi] = useState<UiState>({
    displayedFloorOverride: null,
    followMode: true,
    debugVisible: false,
    loggingEnabled: false,
    scannerMode: ScannerMode.live,
  });
  const [menuOpen, setMenuOpen] = useState(false);
  const uiRef = useRef(ui);
  uiRef.current = ui;

  const stopRef = useRef<null | (() => void)>(null);
  const startScanner = useCallback(
    (mode: ScannerMode) => {
      if (engine == null) return;
      stopRef.current?.();
      stopRef.current = engine.start(container.createScanner(mode));
    },
    [engine, container],
  );

  useEffect(() => {
    startScanner(uiRef.current.scannerMode);
    const sub = AppState.addEventListener('change', (s: string) => {
      if (s === 'active') {
        startScanner(uiRef.current.scannerMode);
      } else {
        stopRef.current?.();
        stopRef.current = null;
      }
    });
    return () => {
      sub.remove();
      stopRef.current?.();
      stopRef.current = null;
    };
  }, [startScanner]);

  const floors = container.registryLoad.repository?.floors() ?? [];
  const registry = container.registryLoad.repository;

  const currentFix =
    state.kind === 'Located' ? state.fix : state.kind === 'Degraded' ? state.lastFix : null;
  const resolvedFloor = currentFix?.floorLevel ?? debug.resolvedFloor ?? null;
  const displayedLevel =
    ui.displayedFloorOverride ?? resolvedFloor ?? floors[0]?.level ?? null;
  const displayedFloor = floors.find(f => f.level === displayedLevel) ?? null;
  const image = displayedFloor != null ? loadFloorImage(displayedFloor) : null;
  const onDisplayedFloor =
    currentFix != null && displayedFloor != null && currentFix.floorLevel === displayedFloor.level;
  const dimmed = state.kind === 'Degraded';
  const heardKeys = useMemo(
    () => new Set(debug.heard.map(o => beaconKeyToString(o.beacon.key))),
    [debug.heard],
  );

  const setScannerMode = (mode: ScannerMode) => {
    setUi(u => ({...u, scannerMode: mode}));
    startScanner(mode);
  };
  const toggleDebug = () => setUi(u => ({...u, debugVisible: !u.debugVisible}));
  const toggleLogging = () => {
    const next = !ui.loggingEnabled;
    void container.logger.setEnabled(next);
    setUi(u => ({...u, loggingEnabled: next}));
  };
  const selectFloor = (level: number) =>
    setUi(u => ({...u, displayedFloorOverride: level, followMode: false}));
  const recenter = () => setUi(u => ({...u, displayedFloorOverride: null, followMode: true}));
  const onUserPanned = () => setUi(u => (u.followMode ? {...u, followMode: false} : u));

  const shareLog = async () => {
    const file = container.logger.currentFile();
    if (file == null) return;
    try {
      await Share.share({url: `file://${file}`, message: 'WayToGo scan log'});
    } catch {
      // user cancelled
    }
  };

  const banner = bannerText(state);
  const showFab = !ui.followMode || ui.displayedFloorOverride != null;

  return (
    <View style={styles.root}>
      <View style={styles.appBar}>
        <Text style={styles.appTitle}>WayToGo</Text>
        <TouchableOpacity onPress={() => setMenuOpen(true)} style={styles.menuButton}>
          <Text style={styles.menuDots}>{'\u22EE'}</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.body}>
        {displayedFloor != null && image != null ? (
          <FloorPlanCanvas
            floor={displayedFloor}
            image={image}
            fix={onDisplayedFloor ? currentFix : null}
            dimmed={dimmed}
            debugVisible={ui.debugVisible}
            beacons={registry?.beaconsOnFloor(displayedFloor.level) ?? []}
            heardKeys={heardKeys}
            usedKeys={debug.usedKeys}
            onUserPan={onUserPanned}
          />
        ) : (
          <View style={styles.centered}>
            <Text>No floor plan available</Text>
          </View>
        )}

        <View style={styles.topOverlay} pointerEvents="box-none">
          {banner != null && (
            <TouchableOpacity
              activeOpacity={1}
              onLongPress={toggleDebug}
              style={[styles.banner, state.kind === 'Error' && styles.bannerError]}>
              <Text style={styles.bannerText}>{banner}</Text>
            </TouchableOpacity>
          )}
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.floorRow}>
            {floors.map(f => {
              const selected = f.level === displayedLevel;
              return (
                <TouchableOpacity
                  key={f.level}
                  onPress={() => selectFloor(f.level)}
                  style={[styles.chip, selected && styles.chipSelected]}>
                  <Text style={[styles.chipText, selected && styles.chipTextSelected]}>
                    {f.level === resolvedFloor ? `${f.name} \u25CF` : f.name}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
          {resolvedFloor != null && displayedLevel !== resolvedFloor && (
            <TouchableOpacity style={styles.snapChip} onPress={recenter}>
              <Text>You are on floor {resolvedFloor}</Text>
            </TouchableOpacity>
          )}
        </View>

        {ui.debugVisible && (
          <View style={styles.bottomOverlay} pointerEvents="box-none">
            <DebugOverlay snapshot={debug} />
          </View>
        )}

        {showFab && (
          <TouchableOpacity style={styles.fab} onPress={recenter}>
            <Text style={styles.fabText}>{'\u25CE'}</Text>
          </TouchableOpacity>
        )}
      </View>

      <Modal visible={menuOpen} transparent animationType="fade" onRequestClose={() => setMenuOpen(false)}>
        <TouchableOpacity style={styles.menuBackdrop} activeOpacity={1} onPress={() => setMenuOpen(false)}>
          <View style={styles.menu}>
            <MenuItem
              label={ui.debugVisible ? 'Hide debug overlay' : 'Show debug overlay'}
              onPress={() => {
                toggleDebug();
                setMenuOpen(false);
              }}
            />
            <MenuItem
              label={ui.loggingEnabled ? 'Stop raw logging' : 'Start raw logging'}
              onPress={() => {
                toggleLogging();
                setMenuOpen(false);
              }}
            />
            <MenuItem
              label="Export scan log"
              onPress={() => {
                setMenuOpen(false);
                void shareLog();
              }}
            />
            <MenuItem
              label="Scanner: Live"
              onPress={() => {
                setScannerMode(ScannerMode.live);
                setMenuOpen(false);
              }}
            />
            {[1, 2, 4].map(speed => (
              <MenuItem
                key={speed}
                label={`Scanner: Replay ${speed}x`}
                onPress={() => {
                  setScannerMode(ScannerMode.replay(speed));
                  setMenuOpen(false);
                }}
              />
            ))}
          </View>
        </TouchableOpacity>
      </Modal>
    </View>
  );
}

function MenuItem({label, onPress}: {label: string; onPress: () => void}): React.JSX.Element {
  return (
    <TouchableOpacity style={styles.menuItem} onPress={onPress}>
      <Text style={styles.menuItemText}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: Colors.white},
  appBar: {
    height: 56,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    backgroundColor: Colors.primary,
  },
  appTitle: {color: Colors.white, fontSize: 20, fontWeight: '600'},
  menuButton: {padding: 8},
  menuDots: {color: Colors.white, fontSize: 22},
  body: {flex: 1},
  centered: {flex: 1, alignItems: 'center', justifyContent: 'center'},
  topOverlay: {position: 'absolute', top: 0, left: 0, right: 0},
  bottomOverlay: {position: 'absolute', bottom: 0, left: 0, right: 0},
  banner: {backgroundColor: Colors.banner, padding: 12},
  bannerError: {backgroundColor: Colors.bannerError},
  bannerText: {color: Colors.white},
  floorRow: {padding: 8, gap: 8},
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
    backgroundColor: '#ECEFF1',
    marginRight: 8,
  },
  chipSelected: {backgroundColor: Colors.primary},
  chipText: {color: Colors.onSurface},
  chipTextSelected: {color: Colors.white},
  snapChip: {
    alignSelf: 'center',
    margin: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 16,
    backgroundColor: '#FFF3E0',
  },
  fab: {
    position: 'absolute',
    right: 20,
    bottom: 24,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 4,
  },
  fabText: {color: Colors.white, fontSize: 24},
  menuBackdrop: {flex: 1, backgroundColor: 'rgba(0,0,0,0.15)'},
  menu: {
    position: 'absolute',
    top: 52,
    right: 8,
    backgroundColor: Colors.white,
    borderRadius: 8,
    paddingVertical: 4,
    minWidth: 200,
    elevation: 8,
  },
  menuItem: {paddingHorizontal: 16, paddingVertical: 12},
  menuItemText: {color: Colors.onSurface, fontSize: 15},
});
