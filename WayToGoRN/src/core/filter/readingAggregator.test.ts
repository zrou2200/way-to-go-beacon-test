import {BeaconKey, RawReading, RegisteredBeacon} from '../model/models';
import {DEFAULT_POSITIONING_CONFIG} from '../model/positioningConfig';
import {beaconKeyEquals} from '../util/beaconKey';
import {ReadingAggregator} from './readingAggregator';

const key: BeaconKey = {uuid: 'U', major: 1, minor: 1};
const beacon: RegisteredBeacon = {
  key,
  label: 'B1',
  floorLevel: 1,
  x: 5.0,
  y: 5.0,
  rssi1m: -59,
};
const config = DEFAULT_POSITIONING_CONFIG;
const aggregator = () =>
  new ReadingAggregator(config, k => (beaconKeyEquals(k, key) ? beacon : null));

function reading(rssi: number, ts: number): RawReading {
  return {key, rssi, advertisedTxPower: null, timestampMs: ts};
}

function single<T>(arr: T[]): T {
  expect(arr).toHaveLength(1);
  return arr[0];
}

describe('ReadingAggregator', () => {
  test('median ignores outliers', () => {
    const agg = aggregator();
    agg.add(reading(-60, 1000));
    agg.add(reading(-61, 1100));
    agg.add(reading(-59, 1200));
    agg.add(reading(-100, 1300)); // outlier
    const obs = single(agg.observations(1400));
    // sorted: -100,-61,-60,-59 -> median of middle two = -60.5
    expect(obs.filteredRssi).toBeCloseTo(-60.5, 9);
    expect(obs.sampleCount).toBe(4);
  });

  test('expires stale samples from window', () => {
    const agg = aggregator();
    agg.add(reading(-60, 0)); // older than windowMs at now=4000
    agg.add(reading(-62, 3500));
    agg.add(reading(-64, 3600));
    const obs = single(agg.observations(4000));
    expect(obs.sampleCount).toBe(2); // the t=0 sample expired (window 3000)
    expect(obs.filteredRssi).toBeCloseTo(-63.0, 9);
  });

  test('excludes beacon with too few samples', () => {
    const agg = aggregator();
    agg.add(reading(-60, 1000)); // only one sample, min is 2
    expect(agg.observations(1100)).toHaveLength(0);
  });

  test('drops beacon unseen longer than stale', () => {
    const agg = aggregator();
    agg.add(reading(-60, 0));
    agg.add(reading(-61, 100));
    // now far beyond staleMs (5000)
    expect(agg.observations(10_000)).toHaveLength(0);
  });

  test('ignores invalid rssi', () => {
    const agg = aggregator();
    agg.add(reading(0, 1000)); // >= 0 invalid
    agg.add(reading(-120, 1050)); // < -110 invalid
    agg.add(reading(-60, 1100));
    agg.add(reading(-62, 1150));
    const obs = single(agg.observations(1200));
    expect(obs.sampleCount).toBe(2);
  });

  test('ignores unknown beacon', () => {
    const agg = aggregator();
    agg.add({key: {uuid: 'U', major: 9, minor: 9}, rssi: -60, advertisedTxPower: null, timestampMs: 1000});
    agg.add({key: {uuid: 'U', major: 9, minor: 9}, rssi: -61, advertisedTxPower: null, timestampMs: 1100});
    expect(agg.observations(1200)).toHaveLength(0);
  });
});
