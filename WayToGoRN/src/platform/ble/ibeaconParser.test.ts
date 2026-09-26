import {IBeaconParser} from './ibeaconParser';

function bytes(hex: string): number[] {
  const clean = hex.replace(/ /g, '');
  const out: number[] = [];
  for (let i = 0; i < clean.length; i += 2) {
    out.push(parseInt(clean.substring(i, i + 2), 16));
  }
  return out;
}

describe('IBeaconParser', () => {
  test('parses test vector', () => {
    const data = bytes('02 15 E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0 00 02 00 11 C5');
    const frame = IBeaconParser.parse(data)!;
    expect(frame.uuid).toBe('E2C56DB5-DFFB-48D2-B060-D0F5A71096E0');
    expect(frame.major).toBe(2);
    expect(frame.minor).toBe(17);
    expect(frame.txPower).toBe(-59);
  });

  test('wrong type byte returns null', () => {
    const data = bytes('01 15 E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0 00 02 00 11 C5');
    expect(IBeaconParser.parse(data)).toBeNull();
  });

  test('wrong length byte returns null', () => {
    const data = bytes('02 14 E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0 00 02 00 11 C5');
    expect(IBeaconParser.parse(data)).toBeNull();
  });

  test('truncated payload returns null', () => {
    const data = bytes('02 15 E2 C5 6D B5 DF FB 48 D2 B0 60 D0 F5 A7 10 96 E0 00 02');
    expect(IBeaconParser.parse(data)).toBeNull();
  });

  test('null payload returns null', () => {
    expect(IBeaconParser.parse(null)).toBeNull();
  });
});
