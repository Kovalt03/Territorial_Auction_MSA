import { useState, useEffect } from 'react';

import { fetchWishlist, addToWishlist, removeFromWishlist } from '../api/user';

export function useWishlist() {
  const [wishlistIds, setWishlistIds] = useState<Set<number>>(new Set());
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchWishlist()
      .then(res => setWishlistIds(new Set(res.territoryIds)))
      .catch(() => setWishlistIds(new Set()))
      .finally(() => setIsLoading(false));
  }, []);

  const toggle = async (territoryId: number) => {
    const isIn = wishlistIds.has(territoryId);
    // 낙관적 업데이트
    setWishlistIds(prev => {
      const next = new Set(prev);
      if (isIn) next.delete(territoryId); else next.add(territoryId);
      return next;
    });
    try {
      if (isIn) {
        await removeFromWishlist(territoryId);
      } else {
        await addToWishlist(territoryId);
      }
    } catch {
      // 실패 시 롤백
      setWishlistIds(prev => {
        const next = new Set(prev);
        if (isIn) next.add(territoryId); else next.delete(territoryId);
        return next;
      });
    }
  };

  return { wishlistIds, toggle, isLoading };
}
