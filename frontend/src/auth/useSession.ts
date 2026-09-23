import { useEffect, useSyncExternalStore } from 'react';
import { api } from '../api/client';

export function useSession() {
  const session = useSyncExternalStore(api.subscribe, api.snapshot);
  useEffect(() => { void api.bootstrap(); }, []);
  return session;
}
