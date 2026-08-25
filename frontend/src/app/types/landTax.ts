export type TaxStatus = 'PAID' | 'FAILED' | 'EXEMPT' | 'EVICTED';

export interface TaxBreakdown {
  exemptCount: number;
  taxableCount: number;
  dailyGP: number;
}

export interface LandTaxStatus {
  territoryCount: number;
  taxBreakdown: TaxBreakdown;
  seasonPassExemptBonus: number;
  effectiveExemptCount: number;
  finalDailyGP: number;
  nextChargeAt: string;
}

export interface LandTaxLogItem {
  logId: number;
  chargedAt: string;
  territoryCount: number;
  gpCharged: number;
  status: TaxStatus;
}

export interface LandTaxLogResponse {
  totalCount: number;
  logs: LandTaxLogItem[];
}
