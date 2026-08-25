import { apiClient } from './client';

export interface GuildSummary {
  guildId: number;
  guildName: string;
  masterNickname: string;
  memberCount: number;
  maxMembers: number;
  totalTrophyPoints: number;
  totalTerritories: number;
  recruitingStatus: 'OPEN' | 'CLOSED';
}

export interface GuildMember {
  userId: number;
  nickname: string;
  role: 'MASTER' | 'MEMBER';
  territoryCount: number;
  joinedAt: string;
}

export interface GuildDetail {
  guildId: number;
  name: string;
  description: string | null;
  emblem: string | null;
  master: { userId: number; nickname: string };
  memberCount: number;
  totalTerritoryCount: number;
  members: GuildMember[];
  createdAt: string;
}

export interface MyGuild {
  guildId: number;
  guildName: string;
  description: string | null;
  masterNickname: string;
  memberCount: number;
  maxMembers: number;
  totalTerritories: number;
  totalTrophyPoints: number;
  myRole: 'MASTER' | 'MEMBER';
  joinedAt: string;
}

export interface GuildApplication {
  applicationId: number;
  userId: number;
  nickname: string;
  trophyPoints: number;
  appliedAt: string;
}

export interface GuildApplicationListResponse {
  guildId: number;
  applications: GuildApplication[];
}

export interface GuildListResponse {
  totalCount: number;
  page: number;
  size: number;
  guilds: GuildSummary[];
}

export function fetchGuildList(params?: { page?: number; size?: number; search?: string }) {
  const q = new URLSearchParams();
  if (params?.page != null) q.set('page', String(params.page));
  if (params?.size != null) q.set('size', String(params.size));
  if (params?.search) q.set('search', params.search);
  const qs = q.toString() ? `?${q}` : '';
  return apiClient.get<GuildListResponse>(`/guilds${qs}`);
}

export function fetchGuildDetail(guildId: number) {
  return apiClient.get<GuildDetail>(`/guilds/${guildId}`);
}

export function fetchMyGuild() {
  return apiClient.get<MyGuild>('/guilds/me');
}

export function createGuild(body: { name: string; description?: string; emblem?: string }) {
  return apiClient.post<{ guildId: number }>('/guilds', body);
}

export function updateGuild(guildId: number, body: { description?: string; emblem?: string; recruitingStatus?: 'OPEN' | 'CLOSED' }) {
  return apiClient.patch<null>(`/guilds/${guildId}`, body);
}

export function joinGuild(guildId: number, message?: string) {
  return apiClient.post<null>(`/guilds/${guildId}/join`, message ? { message } : {});
}

export function cancelJoinGuild(guildId: number) {
  return apiClient.delete<null>(`/guilds/${guildId}/join`);
}

export function leaveGuild(guildId: number) {
  return apiClient.delete<null>(`/guilds/${guildId}/members/me`);
}

export function fetchGuildApplications(guildId: number) {
  return apiClient.get<GuildApplicationListResponse>(`/guilds/${guildId}/applications`);
}

export function approveApplication(guildId: number, userId: number) {
  return apiClient.patch<null>(`/guilds/${guildId}/members/${userId}/approve`, {});
}

export function rejectApplication(guildId: number, userId: number) {
  return apiClient.patch<null>(`/guilds/${guildId}/members/${userId}/reject`, {});
}

export function kickMember(guildId: number, userId: number) {
  return apiClient.delete<null>(`/guilds/${guildId}/members/${userId}`);
}

export function transferMaster(guildId: number, newMasterId: number) {
  return apiClient.patch<null>(`/guilds/${guildId}/master`, { newMasterId });
}
