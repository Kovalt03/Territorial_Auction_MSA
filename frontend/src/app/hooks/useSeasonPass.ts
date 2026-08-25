import { useState, useEffect, useCallback } from 'react';

import { fetchSeasonProgress, fetchSeasonMissions } from '../api/season';
import { ApiError } from '../api/client';

import type { SeasonProgress, SeasonMission } from '../types/season';

export function useSeasonPass() {
  const [progress, setProgress] = useState<SeasonProgress | null>(null);
  const [missions, setMissions] = useState<SeasonMission[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [p, m] = await Promise.all([fetchSeasonProgress(), fetchSeasonMissions()]);
      setProgress(p);
      setMissions(m.missions);
      setError(null);
    } catch (e) {
      setError(
        e instanceof ApiError && e.status >= 400 && e.status < 500
          ? e.message
          : '시즌 패스 정보를 불러올 수 없습니다.',
      );
      console.warn('[useSeasonPass] load failed', e);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return { progress, missions, isLoading, error, reload: load };
}
