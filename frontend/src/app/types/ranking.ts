export interface TerritoryHoldRankEntry {
  rank: number;
  userId: number;
  nickname: string;
  score: number;
  gradeBreakdown: Record<string, number>;
}

export interface TerritoryHoldRankingResponse {
  seasonId: number;
  seasonNumber: number;
  type: string;
  rankings: TerritoryHoldRankEntry[];
  myRank: number | null;
  myScore: number | null;
  updatedAt: string;
}

export interface AuctionSpendRankEntry {
  rank: number;
  userId: number;
  nickname: string;
  totalSpentAP: number;
}

export interface AuctionSpendRankingResponse {
  seasonId: number;
  seasonNumber: number;
  type: string;
  rankings: AuctionSpendRankEntry[];
  myRank: number | null;
  myScore: number | null;
  updatedAt: string;
}

export interface TrophyRankEntry {
  rank: number;
  userId: number;
  nickname: string;
  score: number;
  league: string;
}

export interface TrophyRankingResponse {
  seasonId: number | null;
  seasonNumber: number | null;
  type: string;
  rankings: TrophyRankEntry[];
  myRank: number | null;
  myScore: number | null;
  myLeague: string | null;
  updatedAt: string;
}

export interface ContinentRankEntry {
  rank: number;
  userId: number;
  nickname: string;
  score: number;
}

export interface ContinentRankingResponse {
  continentId: number;
  seasonId: number | null;
  seasonNumber: number | null;
  type: string;
  rankings: ContinentRankEntry[];
  myRank: number | null;
  myScore: number | null;
  updatedAt: string;
}
