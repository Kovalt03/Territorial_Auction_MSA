import { useState, useEffect } from 'react';

import { fetchInventory } from '../api/item';
import type { UserItemInfo } from '../types/item';

export function useInventory() {
  const [inventory, setInventory] = useState<UserItemInfo[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchInventory()
      .then(res => setInventory(res.items))
      .catch((e) => console.warn('[useInventory] fetch failed', e))
      .finally(() => setIsLoading(false));
  }, []);

  return { inventory, isLoading };
}
