import { apiClient } from './client';
import type { ContinentListResponse, GridMapResponse } from '../types/map';
import type { TerritoryDetailResponse } from '../types/territory';

export function fetchContinentList() {
  return apiClient.get<ContinentListResponse>('/continents');
}

export function fetchGridMap(continentId?: number) {
  const q = continentId != null ? `?continent=${continentId}` : '';
  return apiClient.get<GridMapResponse>(`/map/grid${q}`);
}

export function fetchTerritoryDetail(territoryId: number) {
  return apiClient.get<TerritoryDetailResponse>(`/map/territories/${territoryId}`);
}
