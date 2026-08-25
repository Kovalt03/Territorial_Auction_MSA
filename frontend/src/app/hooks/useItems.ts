import { useState, useEffect } from 'react';

import { fetchItemList } from '../api/item';
import type { ItemInfo } from '../types/item';

export function useItems() {
  const [items, setItems] = useState<ItemInfo[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchItemList()
      .then(res => setItems(res.items))
      .catch(() => setError('아이템 목록을 불러올 수 없습니다. 잠시 후 다시 시도해주세요.'))
      .finally(() => setIsLoading(false));
  }, []);

  const updateInventory = (itemId: number, newCount: number) => {
    setItems(prev => prev.map(item =>
      item.itemId === itemId ? { ...item, myInventory: newCount } : item
    ));
  };

  return { items, isLoading, error, updateInventory };
}
