export interface MySeasonPassResponse {
  hasSeasonPass: boolean;
  seasonPass: {
    seasonPassId: number;
    name: string;
    startedAt: string;
    expiresAt: string;
    daysRemaining: number;
    benefits: {
      islandBonusPct: number;
      extraBuilders: number;
      taxExemptBonus: number;
    };
  } | null;
}

export interface PurchaseSeasonPassResponse {
  seasonPassId: number;
  name: string;
  startedAt: string;
  expiresAt: string;
  costAP: number;
  remainingAP: number;
  benefits: {
    islandBonusPct: number;
    extraBuilders: number;
    taxExemptBonus: number;
  };
}

export type RewardTrack = 'FREE' | 'PREMIUM';

export interface SeasonRewardItem {
  rewardId: number;
  level: number;
  track: RewardTrack;
  rewardName: string;
  isClaimed: boolean;
  canClaim: boolean;
}

export interface SeasonProgress {
  seasonId: number;
  seasonName: string;
  passType: 'FREE' | 'PREMIUM';
  currentLevel: number;
  currentXp: number;
  nextLevelXp: number;
  passCostAp: number;
  levelUpCostAp: number;
  seasonEndsAt: string;
  rewards: SeasonRewardItem[];
}

export interface PurchaseLevelResponse {
  currentLevel: number;
  currentXp: number;
  costAP: number;
  remainingAP: number;
}

export type MissionPeriod = 'DAILY' | 'WEEKLY' | 'SEASON';

export interface SeasonMission {
  missionId: number;
  code: string;
  title: string;
  description: string;
  missionType: MissionPeriod;
  goalCount: number;
  completedCount: number;
  xpReward: number;
  isClaimed: boolean;
  canClaim: boolean;
}

export interface MissionListResponse {
  missions: SeasonMission[];
}

export interface ClaimMissionResponse {
  missionId: number;
  xpGranted: number;
  newLevel: number;
  newXp: number;
}

export interface ClaimRewardResponse {
  rewardId: number;
  rewardName: string;
  track: RewardTrack;
  claimedAt: string;
}
