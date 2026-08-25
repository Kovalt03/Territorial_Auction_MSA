import { useState, useEffect, useCallback } from 'react';

import { fetchMyBids } from '../api/auction';
import type { MyBidEntry } from '../types/auction';

export function useMyBids() {
  const [bids, setBids] = useState<MyBidEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const refresh = useCallback(() => {
    fetchMyBids()
      .then(data => setBids(data.bids))
      .catch(() => setBids([]))
      .finally(() => setIsLoading(false));
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { bids, isLoading, refresh };
}
