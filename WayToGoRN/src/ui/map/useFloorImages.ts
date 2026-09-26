import {Image} from 'react-native';
import {FloorPlan} from '../../core/model/floorPlan';

/**
 * Static require() map of bundled floor-plan images (Metro needs literal paths).
 * Add new floors here alongside their `floors.json` entry.
 */
const FLOOR_IMAGE_SOURCES: Record<string, number> = {
  'floorplans/floor1.png': require('../../assets/floorplans/floor1.png'),
  'floorplans/floor2.png': require('../../assets/floorplans/floor2.png'),
};

export interface LoadedFloorImage {
  source: number;
  widthPx: number;
  heightPx: number;
  /** Pixels per meter (x axis reference). */
  ppm: number;
}

const cache = new Map<string, LoadedFloorImage | null>();

/**
 * Resolves a floor image and validates pixels-per-meter axis equality (Section 2.3),
 * logging a warning on mismatch rather than crashing.
 */
export function loadFloorImage(floor: FloorPlan): LoadedFloorImage | null {
  if (cache.has(floor.image)) return cache.get(floor.image) ?? null;

  const source = FLOOR_IMAGE_SOURCES[floor.image];
  if (source == null) {
    console.warn(`FloorImages: no bundled image for ${floor.image}`);
    cache.set(floor.image, null);
    return null;
  }
  const resolved = Image.resolveAssetSource(source);
  const widthPx = resolved?.width ?? 0;
  const heightPx = resolved?.height ?? 0;
  if (widthPx > 0 && heightPx > 0) {
    const ppmX = widthPx / floor.widthM;
    const ppmY = heightPx / floor.heightM;
    if (Math.abs(ppmX - ppmY) / Math.max(ppmX, ppmY) > 0.02) {
      console.warn(
        `Floor ${floor.level}: pixels_per_meter differs by axis ` +
          `(x=${ppmX}, y=${ppmY}); map scaling may be distorted.`,
      );
    }
  }
  const loaded: LoadedFloorImage = {
    source,
    widthPx,
    heightPx,
    ppm: widthPx > 0 ? widthPx / floor.widthM : 0,
  };
  cache.set(floor.image, loaded);
  return loaded;
}
