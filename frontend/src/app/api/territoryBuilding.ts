import { apiClient } from './client';

import type { TerritoryGridBuilding } from '../types/territory';
import type { PlaceIslandBuildingResponse, UpgradeBuildingResponse } from '../types/island';

export function fetchTerritoryBuildings(territoryId: number): Promise<TerritoryGridBuilding[]> {
  return apiClient
    .get<{ buildings: TerritoryGridBuilding[] }>(`/map/territories/${territoryId}/buildings`)
    .then(r => r.buildings);
}

export function placeTerritoryBuilding(territoryId: number, buildingTypeId: number, posX: number, posY: number) {
  return apiClient.post<PlaceIslandBuildingResponse>(`/map/territories/${territoryId}/buildings`, {
    buildingTypeId,
    posX,
    posY,
  });
}

export function placeFromInventoryOnTerritory(inventoryId: number, territoryId: number, posX: number, posY: number) {
  return apiClient.post<unknown>(`/inventory/${inventoryId}/place`, { territoryId, posX, posY });
}

export function upgradeTerritoryBuilding(buildingId: number): Promise<UpgradeBuildingResponse> {
  return apiClient.post<UpgradeBuildingResponse>(`/buildings/${buildingId}/upgrade`, {});
}

// 수리는 시간이 걸린다(즉시 완료 없음). 수리 중에는 건물이 비활성, 완료 시 HP 풀피.
export interface RepairBuildingResponse {
  buildingId: number;
  hp: number;
  buildCompleteAt: string;
  gpRemaining: number;
}
export function repairBuilding(buildingId: number): Promise<RepairBuildingResponse> {
  return apiClient.post<RepairBuildingResponse>(`/buildings/${buildingId}/repair`, {});
}

export interface RepairAllResponse {
  repairedCount: number;
  totalCost: number;
  gpRemaining: number;
}
export function repairAllBuildings(locationType: 'TERRITORY' | 'ISLAND', locationId: number): Promise<RepairAllResponse> {
  return apiClient.post<RepairAllResponse>('/buildings/repair-all', { locationType, locationId });
}
