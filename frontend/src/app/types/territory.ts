export interface TerritoryBuilding {
  buildingId: number;
  type: string;
  level: number;
  hp: number;
  maxHp: number;
}

// GET /map/territories/{id}/buildings — 그리드 렌더용 상세 정보
export interface TerritoryGridBuilding {
  buildingId: number;
  type: string;
  name: string;
  posX: number;
  posY: number;
  width: number;
  height: number;
  hp: number;
  maxHp: number;
  level: number;
  zone: 1 | 2 | 3;
  isDestroyed: boolean;
  buildCompleteAt: string | null;
}

export interface TerritoryOwner {
  userId: number;
  nickname: string;
  currentColor: string;
}

export interface TerritoryAuction {
  auctionId: number;
  currentPrice: number;
  endAt: string;
}

export interface TerritoryDetailResponse {
  territoryId: number;
  coordX: number;
  coordY: number;
  continentName: string;
  grade: string;
  gradeMultiplier: number;
  gridSize: number;
  zone1Radius: number;
  zone2Radius: number;
  status: string;
  owner: TerritoryOwner | null;
  baseProductionRate: number;
  isInvincible: boolean;
  buildings: TerritoryBuilding[];
  auction: TerritoryAuction | null;
  productionRatePerMin: number | null;
  lastProducedAt: string | null;
  storedGp: number | null;
  storageCapacity: number | null;
}
