import {RawReading} from '../../core/model/models';
import {ScanCsv} from './scanCsv';

describe('ScanCsv', () => {
  test('format then parse round-trips', () => {
    const r: RawReading = {
      key: {uuid: 'E2C56DB5DFFB48D2B060D0F5A71096E0', major: 1, minor: 2},
      rssi: -70,
      advertisedTxPower: -59,
      timestampMs: 12345,
    };
    const parsed = ScanCsv.parse(ScanCsv.format(r))!;
    expect(parsed).toEqual(r);
  });

  test('parses missing tx power as null', () => {
    const parsed = ScanCsv.parse('100,U,1,2,-70,')!;
    expect(parsed.advertisedTxPower).toBeNull();
    expect(parsed.rssi).toBe(-70);
  });

  test('ignores header and malformed', () => {
    expect(ScanCsv.parse(ScanCsv.HEADER)).toBeNull();
    expect(ScanCsv.parse('garbage,row')).toBeNull();
    expect(ScanCsv.parse('')).toBeNull();
  });
});
