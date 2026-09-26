/** Monotonic clock abstraction so the positioning core is testable. */
export interface Clock {
  nowMs(): number;
}

function monotonicNowMs(): number {
  const perf = (globalThis as any).performance;
  if (perf && typeof perf.now === 'function') {
    return perf.now();
  }
  return Date.now();
}

/** Default monotonic clock; React Native/Hermes expose `performance.now()`. */
export const SystemClock: Clock = {
  nowMs: () => Math.trunc(monotonicNowMs()),
};
