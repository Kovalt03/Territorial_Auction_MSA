export interface ContinentInfo {
  continentId: number;
  continentName: string;
  themeColor: string | null;
  grade: string | null;
  minTrophyRequired: number | null;
  description: string | null;
  totalTerritories: number;
  occupiedTerritories: number;
  dominantGuildName: string | null;
  avgTerritorytGrade: string | null;
  bonusDescription: string | null;
}

export interface ContinentListResponse {
  totalContinents: number;
  continent: ContinentInfo[];
}

export interface GridTerritoryDto {
  territoryId: number;
  coordX: number;
  coordY: number;
  ownerId: number | null;
  ownerNickname: string | null;
  currentColor: string | null;
  grade: string;
  status: string;
  hasActiveAuction: boolean;
  continentId: number;
  gridSize: number;
}

export interface GridMapResponse {
  mapSize: number;
  territories: GridTerritoryDto[];
}

export interface MapUpdateBroadcast {
  territoryId: number;
  coordX: number;
  coordY: number;
  ownerId: number | null;
  ownerNickname: string | null;
  status: 'OCCUPIED' | 'IDLE';
}
