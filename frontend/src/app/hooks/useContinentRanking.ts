import { useCallback } from 'react';

import { fetchContinentRanking } from '../api/ranking';

import { useFetch } from './useFetch';

const TOP_SIZE = 3;

export function useContinentRanking(continentId: number) {
  const fetcher = useCallback(
    () => fetchContinentRanking(continentId, 0, TOP_SIZE),
    [continentId],
  );
  return useFetch(fetcher, '대륙 랭킹을 불러올 수 없습니다.');
}
