export interface IslandBuilding {
  buildingId: number;
  type: string;
  posX: number;
  posY: number;
  hp: number;
  maxHp: number;
  level: number;
  width: number;
  height: number;
  isDestroyed: boolean;
  buildCompleteAt: string | null;
}

export interface IslandData {
  islandId: number;
  grade: string;
  gridSize: number;
  level: number;
  productionRatePerHour: number;
  lastHarvestAt: string;
  accumulatedGp: number;
  storedGp: number;
  storedFood: number;
  storageCapacity: number;
  zone1Radius: number;
  zone2Radius: number;
  builderCount: number;
  buildersInUse: number;
  productionBoostUntil: string | null;
  buildings: IslandBuilding[];
}

export interface ProductionBoostResponse {
  boostUntil: string;
  multiplier: number;
  apSpent: number;
  apRemaining: number;
}

export interface HarvestIslandGpResponse {
  harvestedGp: number;
  gpBalance: number;
  lastHarvestAt: string;
}

export interface PlaceIslandBuildingResponse {
  buildingId: number;
  type: string;
  posX: number;
  posY: number;
  gpRemaining: number;
}

export interface InventoryItem {
  inventoryId: number;
  buildingTypeId: number;
  buildingTypeName: string;
  buildingType: string;
  quantity: number;
  acquiredAt: string;
}

export interface UpgradeBuildingResponse {
  buildingId: number;
  newLevel: number;
  nextLevel: number | null;
  maxLevel: number;
  upgradeCost: number;
  gpRemaining: number;
  buildCompleteAt: string | null;
}

export interface RushConstructionResponse {
  buildingId: number;
  apSpent: number;
  apRemaining: number;
}

export interface PlaceFromInventoryResponse {
  buildingId: number;
  buildingType: string;
  posX: number;
  posY: number;
  territoryId: number | null;
}

export interface BuildingTypeInfo {
  buildingTypeId: number;
  name: string;
  displayName: string | null;
  category: 'FUNCTIONAL' | 'DECORATIVE' | null;
  width: number;
  height: number;
  maxHp: number;
  baseCostGp: number;
  upgradeCostGp: number | null;
  apCost: number | null;
  zoneRestriction: number | null;
  defensePower: number | null;
  foodProductionRate: number | null;
  unitCapacityPerLevel: number | null;
  gpProductionRate: number | null;
  buildTimeSeconds: number | null;
  upgradeTimeSeconds: number | null;
  icon: string | null;
  colorHex: string | null;
}
