import { apiClient } from './client';
import type { BuildingTypeInfo, HarvestIslandGpResponse, InventoryItem, IslandData, PlaceFromInventoryResponse, PlaceIslandBuildingResponse, ProductionBoostResponse, RushConstructionResponse, UpgradeBuildingResponse } from '../types/island';

export function fetchIsland() {
  return apiClient.get<IslandData>('/island');
}

export function fetchBuildingTypes(): Promise<BuildingTypeInfo[]> {
  return apiClient.get<{ buildingTypes: BuildingTypeInfo[] }>('/building-types').then(r => r.buildingTypes);
}

export function fetchDecorationShop(): Promise<BuildingTypeInfo[]> {
  return apiClient.get<{ buildingTypes: BuildingTypeInfo[] }>('/building-shop').then(r => r.buildingTypes);
}

export function purchaseDecoration(buildingTypeId: number) {
  return apiClient.post<{ inventoryId: number; buildingType: string; displayName: string | null; apRemaining: number }>(`/building-shop/${buildingTypeId}/purchase`, {});
}

export function placeIslandBuilding(buildingTypeId: number, posX: number, posY: number) {
  return apiClient.post<PlaceIslandBuildingResponse>('/island/buildings', { buildingTypeId, posX, posY });
}

export function storeBuilding(buildingId: number) {
  return apiClient.post<unknown>(`/buildings/${buildingId}/store`, {});
}

export function moveBuilding(buildingId: number, posX: number, posY: number) {
  return apiClient.patch<unknown>(`/buildings/${buildingId}/move`, { posX, posY });
}

export function fetchBuildingInventory(): Promise<InventoryItem[]> {
  return apiClient.get<{ items: InventoryItem[] }>('/inventory').then(r => r.items);
}

export function placeFromInventoryOnIsland(inventoryId: number, posX: number, posY: number): Promise<PlaceFromInventoryResponse> {
  return apiClient.post<PlaceFromInventoryResponse>(`/inventory/${inventoryId}/place-on-island`, { posX, posY });
}

export function harvestIslandGp(): Promise<HarvestIslandGpResponse> {
  return apiClient.post<HarvestIslandGpResponse>('/island/harvest', {});
}

export function upgradeBuilding(buildingId: number): Promise<UpgradeBuildingResponse> {
  return apiClient.post<UpgradeBuildingResponse>(`/buildings/${buildingId}/upgrade`, {});
}

// AP로 건설/업그레이드를 즉시 완료. 비용은 남은 시간 비례(서버 계산).
export function rushBuilding(buildingId: number): Promise<RushConstructionResponse> {
  return apiClient.post<RushConstructionResponse>(`/buildings/${buildingId}/rush`, {});
}

// AP로 섬 생산 부스터 발동 (일시 배율).
export function activateProductionBoost(): Promise<ProductionBoostResponse> {
  return apiClient.post<ProductionBoostResponse>('/island/production-boost', {});
}
