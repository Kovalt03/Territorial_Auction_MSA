import { useState, useEffect } from 'react';

import { fetchTerritoryAuctionHistory } from '../api/auction';
import type { TerritoryAuctionHistoryEntry } from '../types/auction';

export function useTerritoryAuctionHistory(territoryId: number | null) {
  const [history, setHistory] = useState<TerritoryAuctionHistoryEntry[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!territoryId) {
      setHistory([]);
      return;
    }
    setIsLoading(true);
    fetchTerritoryAuctionHistory(territoryId)
      .then(res => setHistory(res.histories))
      .catch(() => setHistory([]))
      .finally(() => setIsLoading(false));
  }, [territoryId]);

  return { history, isLoading };
}
