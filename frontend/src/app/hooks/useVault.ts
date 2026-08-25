import { useState, useEffect, useCallback } from 'react';

import { fetchGlobalVault, fetchMyTerritories } from '../api/vault';
import { ApiError } from '../api/client';
import type { GlobalVaultResponse, MyTerritory } from '../types/vault';

export function useVault() {
  const [vault, setVault] = useState<GlobalVaultResponse | null>(null);
  const [territories, setTerritories] = useState<MyTerritory[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    const [vaultResult, territoryResult] = await Promise.allSettled([
      fetchGlobalVault(),
      fetchMyTerritories(),
    ]);

    if (vaultResult.status === 'fulfilled') {
      setVault(vaultResult.value);
      setError(null);
    } else if (!(vaultResult.reason instanceof ApiError && vaultResult.reason.status === 401)) {
      setError('금고 데이터를 불러올 수 없습니다.');
      console.warn('[useVault] global vault fetch failed', vaultResult.reason);
    }

    if (territoryResult.status === 'fulfilled') {
      setTerritories(territoryResult.value.territories);
    } else if (
      !(territoryResult.reason instanceof ApiError && territoryResult.reason.status === 401)
    ) {
      console.warn('[useVault] my territories fetch failed', territoryResult.reason);
    }

    setIsLoading(false);
  }, []);

  useEffect(() => { load(); }, [load]);

  const updateVault = (storedGP: number, nextTransferAvailableAt: string | null) => {
    setVault(prev => prev ? { ...prev, storedGP, nextTransferAvailableAt, isTransferAvailable: false } : prev);
  };

  return { vault, territories, isLoading, error, reload: load, updateVault };
}
