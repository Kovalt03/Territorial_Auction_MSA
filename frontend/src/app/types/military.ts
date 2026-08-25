export interface AttackTokens {
  normalCount: number;
  precisionCount: number;
}

export interface UnitInfo {
  unitTypeId: number;
  name: string;
  displayName: string | null;
  icon: string | null;
  colorHex: string | null;
  quantity: number;
  deployedCount: number;
  idleCount: number;
  inTransitCount: number;
  attackPower: number;
  defensePower: number;
  costGp: number;
  foodCost: number;
  buildingDamage: number;
  requiredBarracksLevel: number;
}

// 훈련 가능한 유닛 종류 카탈로그 — 보유 여부와 무관한 전체 목록(생산 UI 소스).
export interface UnitTypeCatalog {
  unitTypeId: number;
  name: string;
  displayName: string | null;
  icon: string | null;
  colorHex: string | null;
  attackPower: number;
  defensePower: number;
  costGp: number;
  foodCost: number;
  buildingDamage: number;
  requiredBarracksLevel: number;
}

// 자원 스코프 개편 후 유닛·식량은 위치(영토/섬)별로 그룹핑돼 내려온다.
export interface LocationUnits {
  locationType: 'TERRITORY' | 'ISLAND';
  locationId: number;
  coordX: number | null;
  coordY: number | null;
  unitCapacity: number;
  storedFood: number;
  units: UnitInfo[];
}

export interface UnitsResponse {
  locations: LocationUnits[];
}

export interface ProduceUnitResponse {
  unitTypeId: number;
  unitName: string;
  quantity: number;
  gpRemaining: number;
}

export interface DeployUnitResponse {
  deployedCount: number;
  territoryId: number;
}

export interface RecallUnitResponse {
  recalledCount: number;
  remainingDeployed: number;
}

// 특정 영토에 배치된 유닛(타입별 합계) — 회수 목록용
export interface GarrisonUnit {
  unitTypeId: number;
  name: string;
  displayName: string | null;
  icon: string | null;
  colorHex: string | null;
  deployedCount: number;
}

// 계정 연구 현황 — 연구소 레벨이 연구 가능 상한(목표 = 연구소 레벨 + 1)을 정한다.
export interface UnitResearchInfo {
  unitTypeId: number;
  name: string;
  displayName: string | null;
  icon: string | null;
  colorHex: string | null;
  researchedLevel: number;
  maxLevel: number;
  pendingLevel: number | null;
  researchCompleteAt: string | null;
  nextCostGp: number | null;
}

export interface ResearchStatus {
  researchLabLevel: number;
  units: UnitResearchInfo[];
}

export interface StartResearchResponse {
  unitTypeId: number;
  pendingLevel: number;
  researchCompleteAt: string;
  vaultGpRemaining: number;
}
