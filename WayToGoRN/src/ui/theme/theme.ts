/** Shared colors (mirrors the Compose theme in the original app). */
export const Colors = {
  primary: '#2196F3',
  primaryDim: 'rgba(33,150,243,0.66)',
  accuracyFill: 'rgba(33,150,243,0.20)',
  accuracyFillDim: 'rgba(33,150,243,0.10)',
  markerStroke: '#FFFFFF',
  beaconUsed: '#43A047', // used for positioning
  beaconHeard: '#FB8C00', // heard
  beaconRegistered: '#9E9E9E', // registered, not heard
  banner: 'rgba(51,51,51,0.8)',
  bannerError: '#B00020',
  debugBg: 'rgba(0,0,0,0.9)',
  debugText: '#FFFFFF',
  debugTopK: '#81C784',
  debugHeader: '#B0BEC5',
  white: '#FFFFFF',
  background: '#FAFAFA',
  onSurface: '#1A1A1A',
} as const;
