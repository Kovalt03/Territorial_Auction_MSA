import { apiClient } from './client';
import type { BuildingTypeInfo } from '../types/island';
import type {
  AdminContinentCompositionResponse,
  AdminContinentComposition,
  AdminTerritoryListResponse,
  AdminTerritory,
  AdminAuctionSetting,
  AdminUserListResponse,
  AdminUserDetail,
  AdminBulkResult,
  AdminUserBidListResponse,
  AdminUserActiveBid,
  AdminUserTerritoryListResponse,
  UserStatus,
  AdminAuditLogListResponse,
  AdminChatRoom,
  AdminChatMessageListResponse,
  AdminDashboard,
  AdminSeason,
  AdminItem,
  AdminAuctionListResponse,
  Announcement,
  AdminLoginResponse,
  TotpSetupResponse,
} from '../types/admin';

export function adminLogin(email: string, password: string, totpCode?: string) {
  return apiClient.post<AdminLoginResponse>('/admin/auth/login', {
    email,
    password,
    totpCode: totpCode || null,
  });
}

export function setupAdminTotp() {
  return apiClient.post<TotpSetupResponse>('/admin/auth/totp/setup', {});
}

export function fetchAdminContinents() {
  return apiClient.get<AdminContinentCompositionResponse>('/admin/continents');
}

export function fetchAdminTerritories(continentId: number) {
  return apiClient.get<AdminTerritoryListResponse>(`/admin/continents/${continentId}/territories`);
}

export function changeTerritoryGrade(territoryId: number, grade: string, reason: string) {
  return apiClient.patch<AdminTerritory>(`/admin/territories/${territoryId}/grade`, {
    grade,
    reason,
  });
}

export function applyGradeDistribution(
  continentId: number,
  distribution: Record<string, number>,
  reason: string,
) {
  return apiClient.patch<AdminContinentComposition>(
    `/admin/continents/${continentId}/grade-distribution`,
    { distribution, reason },
  );
}

export function changeContinentAuction(continentId: number, enabled: boolean, reason: string) {
  return apiClient.patch<AdminBulkResult>(`/admin/continents/${continentId}/auction`, {
    enabled,
    reason,
  });
}

export function bulkChangeTerritoryGrade(territoryIds: number[], grade: string, reason: string) {
  return apiClient.patch<AdminBulkResult>('/admin/territories/bulk/grade', {
    territoryIds,
    grade,
    reason,
  });
}

export function bulkChangeTerritoryAuction(
  territoryIds: number[],
  enabled: boolean,
  reason: string,
) {
  return apiClient.patch<AdminBulkResult>('/admin/territories/bulk/auction', {
    territoryIds,
    enabled,
    reason,
  });
}

export function bulkForceStartTerritories(territoryIds: number[], reason: string) {
  return apiClient.post<AdminBulkResult>('/admin/territories/bulk/force-start', {
    territoryIds,
    reason,
  });
}

export function changeTerritoryAuction(territoryId: number, enabled: boolean, reason: string) {
  return apiClient.patch<AdminTerritory>(`/admin/territories/${territoryId}/auction`, {
    enabled,
    reason,
  });
}

export function forceStartAuction(territoryId: number) {
  return apiClient.post<AdminTerritory>(
    `/admin/territories/${territoryId}/auction/force-start`,
    {},
  );
}

export function fetchAuctionSetting() {
  return apiClient.get<AdminAuctionSetting>('/admin/settings/auction');
}

export function setAuctionSetting(enabled: boolean) {
  return apiClient.patch<AdminAuctionSetting>('/admin/settings/auction', { enabled });
}

export function fetchAdminUsers(params: {
  keyword?: string;
  status?: UserStatus;
  page: number;
  size: number;
}) {
  const qs = new URLSearchParams();
  if (params.keyword) qs.set('keyword', params.keyword);
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(params.page));
  qs.set('size', String(params.size));
  return apiClient.get<AdminUserListResponse>(`/admin/users?${qs.toString()}`);
}

export function fetchAdminUser(userId: number) {
  return apiClient.get<AdminUserDetail>(`/admin/users/${userId}`);
}

export function changeUserStatus(userId: number, status: UserStatus, reason: string) {
  return apiClient.patch<AdminUserDetail>(`/admin/users/${userId}/status`, { status, reason });
}

export function adjustUserWallet(
  userId: number,
  apDelta: number,
  gpDelta: number,
  reason: string,
) {
  return apiClient.post<AdminUserDetail>(`/admin/users/${userId}/wallet/adjust`, {
    apDelta,
    gpDelta,
    reason,
  });
}

export function bulkAdjustWallet(
  userIds: number[],
  apDelta: number,
  gpDelta: number,
  reason: string,
) {
  return apiClient.post<AdminBulkResult>('/admin/users/bulk/wallet-adjust', {
    userIds,
    apDelta,
    gpDelta,
    reason,
  });
}

export function bulkChangeUserStatus(userIds: number[], status: UserStatus, reason: string) {
  return apiClient.post<AdminBulkResult>('/admin/users/bulk/status', { userIds, status, reason });
}

export function bulkSendNotification(userIds: number[], message: string) {
  return apiClient.post<AdminBulkResult>('/admin/users/bulk/notifications', { userIds, message });
}

export function fetchUserBids(userId: number, page: number, size = 20) {
  return apiClient.get<AdminUserBidListResponse>(
    `/admin/users/${userId}/bids?page=${page}&size=${size}`,
  );
}

export function fetchUserActiveBids(userId: number) {
  return apiClient.get<{ activeBids: AdminUserActiveBid[] }>(
    `/admin/users/${userId}/active-bids`,
  );
}

export function fetchUserTerritories(userId: number, page: number, size = 20) {
  return apiClient.get<AdminUserTerritoryListResponse>(
    `/admin/users/${userId}/territories?page=${page}&size=${size}`,
  );
}

export function sendUserNotification(userId: number, message: string) {
  return apiClient.post<null>(`/admin/users/${userId}/notifications`, { message });
}

export function fetchAuditLogs(params: {
  action?: string;
  targetType?: string;
  page: number;
  size?: number;
}) {
  const qs = new URLSearchParams();
  if (params.action) qs.set('action', params.action);
  if (params.targetType) qs.set('targetType', params.targetType);
  qs.set('page', String(params.page));
  qs.set('size', String(params.size ?? 30));
  return apiClient.get<AdminAuditLogListResponse>(`/admin/audit-logs?${qs.toString()}`);
}

export function fetchChatRooms() {
  return apiClient.get<AdminChatRoom[]>('/admin/chat/rooms');
}

export function fetchChatMessages(params: {
  roomId?: number;
  keyword?: string;
  page: number;
  size?: number;
}) {
  const qs = new URLSearchParams();
  if (params.roomId != null) qs.set('roomId', String(params.roomId));
  if (params.keyword) qs.set('keyword', params.keyword);
  qs.set('page', String(params.page));
  qs.set('size', String(params.size ?? 30));
  return apiClient.get<AdminChatMessageListResponse>(`/admin/chat/messages?${qs.toString()}`);
}

export function deleteChatMessage(messageId: number) {
  return apiClient.delete<null>(`/admin/chat/messages/${messageId}`);
}

export function fetchDashboard() {
  return apiClient.get<AdminDashboard>('/admin/dashboard');
}

export function fetchSeasons() {
  return apiClient.get<{ seasons: AdminSeason[] }>('/admin/seasons');
}

export function createSeason(startedAt?: string, endedAt?: string) {
  return apiClient.post<AdminSeason>('/admin/seasons', {
    startedAt: startedAt ?? null,
    endedAt: endedAt ?? null,
  });
}

export interface AdminUnitType {
  unitTypeId: number;
  name: string;
  displayName: string | null;
  icon: string | null;
  colorHex: string | null;
  attackPower: number;
  defensePower: number;
  costGp: number;
  foodCost: number;
  level: number;
}

export interface UnitLevelValues {
  attackPower: number | null;
  defensePower: number | null;
  trainCostFood: number | null;
  requiredBarracksLevel: number | null;
}

export function fetchAdminUnitTypes() {
  return apiClient.get<AdminUnitType[]>('/admin/unit-types');
}

export function updateUnitType(unitTypeId: number, form: Omit<AdminUnitType, 'unitTypeId' | 'name'>) {
  return apiClient.patch<AdminUnitType>(`/admin/unit-types/${unitTypeId}`, form);
}

export function fetchUnitLevelSpecs(unitTypeId: number) {
  return apiClient.get<Record<string, UnitLevelValues>>(`/admin/unit-types/${unitTypeId}/level-specs`);
}

export function updateUnitLevelSpecs(unitTypeId: number, specs: Record<number, UnitLevelValues>) {
  return apiClient.patch<Record<string, UnitLevelValues>>(`/admin/unit-types/${unitTypeId}/level-specs`, { specs });
}

export interface AdminSeasonPass {
  seasonPassId: number;
  name: string;
  costAp: number;
  durationDays: number;
  islandBonusPct: number;
  extraBuilders: number;
  taxExemptBonus: number;
  buildTimeReductionPct: number;
}

export function fetchSeasonPasses() {
  return apiClient.get<AdminSeasonPass[]>('/admin/season-passes');
}

export function updateSeasonPass(seasonPassId: number, form: Omit<AdminSeasonPass, 'seasonPassId' | 'name'>) {
  return apiClient.patch<AdminSeasonPass>(`/admin/season-passes/${seasonPassId}`, form);
}

export function endSeason(seasonId: number) {
  return apiClient.patch<AdminSeason>(`/admin/seasons/${seasonId}/end`, {});
}

export function fetchItems() {
  return apiClient.get<{ items: AdminItem[] }>('/admin/items');
}

export function updateItem(
  itemId: number,
  costAp: number | null,
  costGp: number | null,
  dailyLimit: number | null,
) {
  return apiClient.patch<AdminItem>(`/admin/items/${itemId}`, { costAp, costGp, dailyLimit });
}

export function grantItem(userId: number, itemId: number, quantity: number, reason: string) {
  return apiClient.post<null>('/admin/items/grant', { userId, itemId, quantity, reason });
}

export function fetchActiveAuctions(page: number, size = 20) {
  return apiClient.get<AdminAuctionListResponse>(`/admin/auctions?page=${page}&size=${size}`);
}

export function forceSettleAuction(auctionId: number) {
  return apiClient.post<null>(`/admin/auctions/${auctionId}/settle`, {});
}

export function forceCancelAuction(auctionId: number) {
  return apiClient.post<null>(`/admin/auctions/${auctionId}/cancel`, {});
}

export function fetchAnnouncement() {
  return apiClient.get<Announcement>('/announcement');
}

export function fetchAdminAnnouncement() {
  return apiClient.get<Announcement>('/admin/announcement');
}

export function updateAnnouncement(active: boolean, message: string) {
  return apiClient.patch<Announcement>('/admin/announcement', { active, message });
}

export interface BuildingTypeForm {
  name?: string;
  displayName: string | null;
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

export function fetchAdminBuildingTypes() {
  return apiClient.get<{ buildingTypes: BuildingTypeInfo[] }>('/admin/building-types').then(r => r.buildingTypes);
}

export function createBuildingType(form: BuildingTypeForm) {
  return apiClient.post<BuildingTypeInfo>('/admin/building-types', form);
}

export function updateBuildingType(id: number, form: BuildingTypeForm) {
  return apiClient.patch<BuildingTypeInfo>(`/admin/building-types/${id}`, form);
}

export function deleteBuildingType(id: number) {
  return apiClient.delete<null>(`/admin/building-types/${id}`);
}

export interface LevelSpecValues {
  upgradeCostGp: number | null;
  maxHp: number | null;
  defensePower: number | null;
  foodProductionRate: number | null;
  unitCapacityPerLevel: number | null;
  gpProductionRate: number | null;
  upgradeTimeSeconds: number | null;
}

// {성 레벨: 최대 개수}. 값이 없으면 그 성 레벨에서는 제한 없음.
export function fetchCastleLimits(id: number) {
  return apiClient.get<Record<string, number>>(`/admin/building-types/${id}/castle-limits`);
}

export function updateCastleLimits(id: number, limits: Record<number, number | null>) {
  return apiClient.patch<Record<string, number>>(`/admin/building-types/${id}/castle-limits`, { limits });
}

export function fetchLevelSpecs(id: number) {
  return apiClient.get<Record<string, LevelSpecValues>>(`/admin/building-types/${id}/level-specs`);
}

export function updateLevelSpecs(id: number, specs: Record<number, LevelSpecValues>) {
  return apiClient.patch<Record<string, LevelSpecValues>>(`/admin/building-types/${id}/level-specs`, { specs });
}
