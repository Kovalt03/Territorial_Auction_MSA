import { apiClient } from './client';

export type SiegeStructureType = 'STAGING' | 'TOWER' | 'SUPPLY';

export interface ForceEntry {
  unitTypeId: number;
  quantity: number;
}

export interface StructureEntry {
  type: SiegeStructureType;
  coordX: number;
  coordY: number;
}

// 백엔드 DeclareSiegeRequest 계약. forces=커밋 병력, structures=공성 건물(주둔지 최소 1개).
export interface DeclareSiegeRequest {
  targetTerritoryId: number;
  targetBuildingId?: number | null;
  attackZone: number;
  forces: ForceEntry[];
  structures: StructureEntry[];
}

export interface DeclareSiegeResponse {
  siegeId: number;
  resolveAt: string;
  attackTokenRemaining: number;
}

export function declareSiege(req: DeclareSiegeRequest): Promise<DeclareSiegeResponse> {
  return apiClient.post<DeclareSiegeResponse>('/military/siege', req);
}

// 공성 대상 정찰 — 존별 실제 HP + 정밀 공격 대상 건물. 방어 병력 구성은 미노출(정보 비대칭).
export interface SiegeTargetZone {
  zone: number;
  currentHp: number;
  maxHp: number;
  buildingCount: number;
}

export interface SiegeTargetBuilding {
  buildingId: number;
  name: string;
  displayName: string | null;
  zone: number;
  currentHp: number;
  maxHp: number;
  posX: number;
  posY: number;
  width: number;
  height: number;
  isUnderConstruction: boolean;
}

export interface SiegeTargetIntel {
  territoryId: number;
  coordX: number;
  coordY: number;
  zones: SiegeTargetZone[];
  buildings: SiegeTargetBuilding[];
}

export function fetchSiegeTarget(territoryId: number): Promise<SiegeTargetIntel> {
  return apiClient.get<SiegeTargetIntel>(`/military/siege/target/${territoryId}`);
}

export interface SiegeResult {
  siegeId: number;
  isAttackerWin: boolean;
  attackerUnitsLost: number;
  defenderUnitsLost: number;
  lootedGp: number;
  resultType: string | null;
  resolvedAt: string;
}

export function fetchSiegeResult(siegeId: number): Promise<SiegeResult> {
  return apiClient.get<SiegeResult>(`/military/siege/${siegeId}/result`);
}

// ── 공성 현황/이력/실시간 경보 ─────────────────────────────────────

export interface SiegeUser { userId: number; nickname: string; }
export interface SiegeTargetBuildingRef { buildingId: number; name: string; displayName: string | null; }
export interface SiegeEventItem {
  siegeId: number;
  status: 'PENDING' | 'RESOLVED';
  attacker: SiegeUser;
  defender: SiegeUser;
  targetTerritory: { id: number; coordX: number; coordY: number };
  attackZone: number;
  targetBuilding: SiegeTargetBuildingRef | null;
  siegeStartAt: string;
  resolveAt: string;
}
export interface SiegeEventList { totalCount: number; sieges: SiegeEventItem[]; }

// 진행 상태별 공성 목록(전체). 화면에서 내가 공격자/방어자인 것만 필터링해 쓴다.
export function fetchSiegeEvents(status: 'PENDING' | 'RESOLVED' = 'PENDING'): Promise<SiegeEventList> {
  return apiClient.get<SiegeEventList>(`/siege/events?status=${status}&size=100`);
}

export interface MySiegeHistoryItem {
  siegeId: number;
  territoryId: number;
  territoryGrade: string;
  role: 'ATTACKER' | 'DEFENDER';
  result: 'WIN' | 'LOSE';
  occurredAt: string;
}
export interface MySiegeHistory {
  totalCount: number;
  wins: number;
  losses: number;
  history: MySiegeHistoryItem[];
}
export function fetchMySiegeHistory(): Promise<MySiegeHistory> {
  return apiClient.get<MySiegeHistory>('/siege/my-history');
}

// STOMP /sub/user/{userId}/siege-alert 페이로드 (방어자 채널)
export interface SiegeAlert {
  siegeId: number;
  alertType: 'DECLARED' | 'RESOLVED';
  territoryId: number;
  coordX: number;
  coordY: number;
  attackZone: number;
  attackerId: number;
  attackerNickname: string;
  defenderId: number;
  defenderNickname: string;
  resolveAt: string;
  isAttackerWin: boolean | null;
  resultType: string | null;
}
