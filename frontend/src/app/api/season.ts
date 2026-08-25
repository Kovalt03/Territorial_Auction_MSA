import { apiClient } from './client';
import type {
  ClaimMissionResponse,
  ClaimRewardResponse,
  MissionListResponse,
  MySeasonPassResponse,
  PurchaseLevelResponse,
  PurchaseSeasonPassResponse,
  SeasonProgress,
} from '../types/season';

export function fetchMySeasonPass() {
  return apiClient.get<MySeasonPassResponse>('/season-pass/me');
}

export function purchaseSeasonPass() {
  return apiClient.post<PurchaseSeasonPassResponse>('/season-pass/purchase', {});
}

export function purchaseSeasonLevel() {
  return apiClient.post<PurchaseLevelResponse>('/season-pass/level-up', {});
}

export function fetchSeasonProgress() {
  return apiClient.get<SeasonProgress>('/season-pass');
}

export function fetchSeasonMissions() {
  return apiClient.get<MissionListResponse>('/season-pass/missions');
}

export function claimMissionApi(missionId: number) {
  return apiClient.post<ClaimMissionResponse>(`/season-pass/missions/${missionId}/claim`, {});
}

export function claimRewardApi(rewardId: number) {
  return apiClient.post<ClaimRewardResponse>(`/season-pass/rewards/${rewardId}/claim`, {});
}
