import {useEffect, useState} from 'react';
import {Store} from '../../core/engine/store';

/** Subscribe a component to a [Store] (the RN analogue of collecting a StateFlow). */
export function useStore<T>(store: Store<T>): T {
  const [value, setValue] = useState<T>(() => store.get());
  useEffect(() => store.subscribe(setValue), [store]);
  return value;
}
