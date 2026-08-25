import { apiClient } from './client';
import type { AttackTokens, DeployUnitResponse, GarrisonUnit, ProduceUnitResponse, RecallUnitResponse, ResearchStatus, StartResearchResponse, UnitsResponse, UnitTypeCatalog } from '../types/military';

export function fetchAttackTokens(): Promise<AttackTokens> {
  return apiClient.get<AttackTokens>('/military/attack-tokens');
}

export function fetchUnits(): Promise<UnitsResponse> {
  return apiClient.get<UnitsResponse>('/military/units');
}

// 훈련 가능한 유닛 종류 전체(보유 무관) — 훈련 모달 선택 목록의 소스.
export function fetchUnitTypes(): Promise<UnitTypeCatalog[]> {
  return apiClient.get<UnitTypeCatalog[]>('/military/unit-types');
}

export function produceUnit(
  unitTypeId: number,
  quantity: number,
  locationId: number,
  locationType: 'ISLAND' | 'TERRITORY',
  level = 1,
): Promise<ProduceUnitResponse> {
  return apiClient.post<ProduceUnitResponse>('/military/units', {
    unitTypeId,
    quantity,
    level,
    locationId,
    locationType,
  });
}

// 대기 유닛을 영토의 방어 건물에 주둔시킨다(출발 위치의 대기 스택에서 차감).
export function deployUnit(params: {
  territoryId: number;
  buildingId: number;
  unitTypeId: number;
  quantity: number;
  sourceLocationId: number;
  sourceLocationType: 'ISLAND' | 'TERRITORY';
}): Promise<DeployUnitResponse> {
  return apiClient.post<DeployUnitResponse>('/military/units/deploy', params);
}

// 특정 영토에 배치(주둔)된 내 유닛을 타입별로 조회한다(회수 목록용).
export function fetchTerritoryGarrison(territoryId: number): Promise<GarrisonUnit[]> {
  return apiClient.get<GarrisonUnit[]>(`/military/territory/${territoryId}/garrison`);
}

// 영토에 배치된 유닛을 귀속지 대기 스택으로 회수한다.
export function recallUnit(
  territoryId: number,
  unitTypeId: number,
  quantity: number,
): Promise<RecallUnitResponse> {
  return apiClient.post<RecallUnitResponse>('/military/units/recall', {
    territoryId,
    unitTypeId,
    quantity,
  });
}

// 계정 연구 현황(연구소 레벨 + 유닛별 해금 레벨)
export function fetchResearch(): Promise<ResearchStatus> {
  return apiClient.get<ResearchStatus>('/military/research');
}

// 다음 레벨 연구 시작 — 금고 GP 차감 + 시간 소요
export function startResearch(unitTypeId: number): Promise<StartResearchResponse> {
  return apiClient.post<StartResearchResponse>(`/military/research/${unitTypeId}`, {});
}
