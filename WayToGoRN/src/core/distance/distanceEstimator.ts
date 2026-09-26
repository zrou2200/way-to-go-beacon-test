/**
 * Log-distance path-loss model (Section 5.2):
 *   distance = 10 ^ ((rssi1m - filteredRssi) / (10 * n))
 * Result is clamped to [minDistanceM, maxDistanceM].
 */
export const DistanceEstimator = {
  estimate(
    filteredRssi: number,
    rssi1m: number,
    pathLossExponent: number,
    minDistanceM: number,
    maxDistanceM: number,
  ): number {
    const exponent = (rssi1m - filteredRssi) / (10.0 * pathLossExponent);
    const raw = Math.pow(10.0, exponent);
    return Math.min(Math.max(raw, minDistanceM), maxDistanceM);
  },
};
