import { apiClient } from './client';
import type {
  TerritoryHoldRankingResponse,
  AuctionSpendRankingResponse,
  TrophyRankingResponse,
  ContinentRankingResponse,
} from '../types/ranking';

export function fetchTerritoryHoldRanking(page = 0, size = 50) {
  return apiClient.get<TerritoryHoldRankingResponse>(
    `/rankings/territory-hold?page=${page}&size=${size}`,
  );
}

export function fetchAuctionSpendRanking(page = 0, size = 50) {
  return apiClient.get<AuctionSpendRankingResponse>(
    `/rankings/auction-spend?page=${page}&size=${size}`,
  );
}

export function fetchTrophyRanking(page = 0, size = 50) {
  return apiClient.get<TrophyRankingResponse>(`/rankings/trophy?page=${page}&size=${size}`);
}

export function fetchContinentRanking(continentId: number, page = 0, size = 50) {
  return apiClient.get<ContinentRankingResponse>(
    `/rankings/continent/${continentId}?page=${page}&size=${size}`,
  );
}
