import { useCallback } from 'react';

import { fetchAuctionList } from '../api/auction';

import { useFetch } from './useFetch';

export function useContinentAuctions(continentId: number) {
  const fetcher = useCallback(() => fetchAuctionList(continentId), [continentId]);
  return useFetch(fetcher, '경매 목록을 불러올 수 없습니다.');
}
