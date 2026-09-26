import React, {useEffect, useMemo, useRef, useState} from 'react';
import {Animated, PanResponder, StyleSheet, View} from 'react-native';
import Svg, {Circle, G, Image as SvgImage, Text as SvgText} from 'react-native-svg';
import {FloorPlan} from '../../core/model/floorPlan';
import {PositionFix, RegisteredBeacon} from '../../core/model/models';
import {beaconKeyToString} from '../../core/util/beaconKey';
import {Colors} from '../theme/theme';
import {LoadedFloorImage} from './useFloorImages';

interface Props {
  floor: FloorPlan;
  image: LoadedFloorImage;
  fix: PositionFix | null;
  dimmed: boolean;
  debugVisible: boolean;
  beacons: RegisteredBeacon[];
  heardKeys: Set<string>;
  usedKeys: Set<string>;
  onUserPan: () => void;
}

function clamp(v: number, lo: number, hi: number): number {
  return Math.min(Math.max(v, lo), hi);
}

/**
 * Renders a floor plan with pinch-zoom / pan (Section 7.1) and overlays: the
 * user marker with accuracy circle, plus optional debug beacon markers. The
 * floor image and the SVG overlay share one transformed content layer so the
 * marker stays aligned with the map (meters -> pixels via `ppm`).
 */
export function FloorPlanCanvas(props: Props): React.JSX.Element {
  const {image, fix, dimmed, debugVisible, beacons, heardKeys, usedKeys, onUserPan} =
    props;
  const [layout, setLayout] = useState({width: 0, height: 0});

  const imgW = image.widthPx || 1;
  const imgH = image.heightPx || 1;
  const ppm = image.ppm || 1;
  const baseScale =
    layout.width > 0 ? Math.min(layout.width / imgW, layout.height / imgH) : 1;

  const scale = useRef(new Animated.Value(1)).current;
  const translate = useRef(new Animated.ValueXY({x: 0, y: 0})).current;
  const panValue = useRef({x: 0, y: 0});
  const gesture = useRef({lastScale: 1, lastPan: {x: 0, y: 0}, startDist: 0, startScale: 1});

  useEffect(() => {
    const id = translate.addListener((v: {x: number; y: number}) => {
      panValue.current = v;
    });
    return () => translate.removeListener(id);
  }, [translate]);

  const panResponder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: () => true,
        onPanResponderGrant: () => onUserPan(),
        onPanResponderMove: (
          evt: {nativeEvent: {touches: Array<{pageX: number; pageY: number}>}},
          gs: {dx: number; dy: number},
        ) => {
          const touches = evt.nativeEvent.touches;
          const g = gesture.current;
          if (touches.length >= 2) {
            const dx = touches[0].pageX - touches[1].pageX;
            const dy = touches[0].pageY - touches[1].pageY;
            const dist = Math.hypot(dx, dy) || 1;
            if (g.startDist === 0) {
              g.startDist = dist;
              g.startScale = g.lastScale;
            }
            const next = clamp((g.startScale * dist) / g.startDist, 0.5, 5);
            scale.setValue(next);
            g.lastScale = next;
          } else {
            translate.setValue({x: g.lastPan.x + gs.dx, y: g.lastPan.y + gs.dy});
          }
        },
        onPanResponderRelease: () => {
          const g = gesture.current;
          g.lastPan = {...panValue.current};
          g.startDist = 0;
        },
        onPanResponderTerminate: () => {
          gesture.current.startDist = 0;
        },
      }),
    [onUserPan, scale, translate],
  );

  const onLayout = (e: {nativeEvent: {layout: {width: number; height: number}}}) => {
    const {width, height} = e.nativeEvent.layout;
    setLayout({width, height});
  };

  const contentScale = Animated.multiply(scale, baseScale);
  const markerColor = dimmed ? Colors.primaryDim : Colors.primary;
  const accuracyFill = dimmed ? Colors.accuracyFillDim : Colors.accuracyFill;

  return (
    <View style={styles.container} onLayout={onLayout} {...panResponder.panHandlers}>
      <Animated.View
        style={[
          styles.content,
          {
            width: imgW,
            height: imgH,
            left: (layout.width - imgW) / 2,
            top: (layout.height - imgH) / 2,
            transform: [
              {translateX: translate.x},
              {translateY: translate.y},
              {scale: contentScale},
            ],
          },
        ]}>
        <Svg width={imgW} height={imgH}>
          <SvgImage
            x={0}
            y={0}
            width={imgW}
            height={imgH}
            href={image.source}
            preserveAspectRatio="xMidYMid meet"
          />
          {debugVisible &&
            beacons.map(b => {
              const ks = beaconKeyToString(b.key);
              const color = usedKeys.has(ks)
                ? Colors.beaconUsed
                : heardKeys.has(ks)
                ? Colors.beaconHeard
                : Colors.beaconRegistered;
              return (
                <G key={ks}>
                  <Circle cx={b.x * ppm} cy={b.y * ppm} r={7} fill={color} />
                  <SvgText x={b.x * ppm + 10} y={b.y * ppm + 4} fontSize={12} fill="#37474F">
                    {b.label}
                  </SvgText>
                </G>
              );
            })}
          {fix != null && (
            <G>
              <Circle
                cx={fix.x * ppm}
                cy={fix.y * ppm}
                r={fix.accuracyM * ppm}
                fill={accuracyFill}
              />
              <Circle
                cx={fix.x * ppm}
                cy={fix.y * ppm}
                r={9}
                fill={markerColor}
                stroke={Colors.markerStroke}
                strokeWidth={3}
              />
            </G>
          )}
        </Svg>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, overflow: 'hidden', backgroundColor: Colors.background},
  content: {position: 'absolute'},
});
