import { useState, useEffect } from 'react';

import { fetchTerritoryDetail } from '../api/map';
import { fetchAuctionBids } from '../api/auction';
import type { TerritoryDetailResponse } from '../types/territory';
import type { BidEntry } from '../types/auction';

export function useTerritoryDetail(territoryId: number) {
  const [territory, setTerritory] = useState<TerritoryDetailResponse | null>(null);
  const [bids, setBids] = useState<BidEntry[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!territoryId) return;
    setIsLoading(true);
    setError(null);
    fetchTerritoryDetail(territoryId)
      .then(async data => {
        setTerritory(data);
        if (data.auction) {
          try {
            const bidData = await fetchAuctionBids(data.auction.auctionId);
            setBids(bidData.bids);
          } catch {
            setBids([]);
          }
        } else {
          setBids([]);
        }
      })
      .catch(() => setError('영토 정보를 불러올 수 없습니다.'))
      .finally(() => setIsLoading(false));
  }, [territoryId]);

  const refreshBids = async (auctionId: number) => {
    try {
      const bidData = await fetchAuctionBids(auctionId);
      setBids(bidData.bids);
    } catch {
      // keep existing bids
    }
  };

  const updateCurrentPrice = (price: number) => {
    setTerritory(prev => {
      if (!prev?.auction) return prev;
      return { ...prev, auction: { ...prev.auction, currentPrice: price } };
    });
  };

  return { territory, bids, isLoading, error, refreshBids, updateCurrentPrice };
}
