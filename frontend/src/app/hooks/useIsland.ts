import { useState, useEffect, useCallback, useRef } from 'react';

import { fetchIsland } from '../api/island';
import { ApiError } from '../api/client';
import type { IslandData } from '../types/island';

export function useIsland() {
  const [island, setIsland] = useState<IslandData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const loadedRef = useRef(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await fetchIsland();
      setIsland(data);
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 401)) setError('섬 데이터를 불러올 수 없습니다.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (loadedRef.current) return;
    loadedRef.current = true;
    void load();
  }, [load]);

  return { island, isLoading, error, reload: load };
}
