export type Listener<T> = (value: T) => void;

/**
 * Minimal observable value, the React Native stand-in for Kotlin's
 * `StateFlow<T>`: holds a current value, notifies listeners on change, and
 * replays the current value to new subscribers. Consumed by the `useStore` hook.
 */
export class Store<T> {
  private readonly listeners = new Set<Listener<T>>();

  constructor(private value: T) {}

  get(): T {
    return this.value;
  }

  set(value: T): void {
    this.value = value;
    for (const listener of this.listeners) listener(value);
  }

  /** Subscribe and immediately receive the current value. Returns an unsubscribe. */
  subscribe(listener: Listener<T>): () => void {
    this.listeners.add(listener);
    listener(this.value);
    return () => {
      this.listeners.delete(listener);
    };
  }
}
