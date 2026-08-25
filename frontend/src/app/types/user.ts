export interface MyWalletResponse {
  availableGP: number;
  availableAP: number;
  lockedAP: number;
}

export interface ChargeApResponse {
  availableAP: number;
  chargedAmount: number;
  chargedAt: string;
}

export interface NotificationSettings {
  isOutbidEnabled: boolean;
  isAuctionStartEnabled: boolean;
  isMarketingEnabled: boolean;
}

export interface MyProfileResponse {
  userId: number;
  nickname: string;
  wallet: {
    availableGP: number;
    availableAP: number;
    lockedAP: number;
  };
  island: {
    islandId: number;
    level: number;
    productionRate: number;
    builderCount: number;
  } | null;
  seasonPass: {
    isActive: boolean;
    expiresAt: string | null;
  };
  territoryCount: number;
}
