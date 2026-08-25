export interface BidEntry {
  price: number;
  bidAt: string;
  bidderNickname: string | null;
}

export interface AuctionBidsResponse {
  auctionId: number;
  bids: BidEntry[];
}

export interface PlaceBidResponse {
  auctionId: number;
  newPrice: number;
  endAt: string;
}

export interface MyBidEntry {
  auctionId: number;
  territoryId: number;
  coordX: number;
  coordY: number;
  myBidAmount: number;
  currentPrice: number;
  isHighestBidder: boolean;
  endAt: string;
  status: string;
  grade: string;
  continentName: string;
}

export interface MyBidsResponse {
  totalCount: number;
  page: number;
  size: number;
  bids: MyBidEntry[];
}

export interface TerritoryAuctionHistoryEntry {
  auctionId: number;
  winnerNickname: string;
  finalPrice: number;
  wonAt: string;
}

export interface TerritoryAuctionHistoryResponse {
  territoryId: number;
  histories: TerritoryAuctionHistoryEntry[];
}

export interface AuctionBidBroadcast {
  auctionId: number;
  currentPrice: number;
  bidderId: number;
  bidderNickname: string;
  bidAt: string;
  endAt: string;
}

export interface AuctionItem {
  auctionId: number;
  territoryId: number;
  coordX: number;
  coordY: number;
  continentName: string;
  grade: string;
  currentPrice: number;
  currentBidderNickname: string | null;
  endAt: string;
  status: string;
}

export interface AuctionListResponse {
  totalCount: number;
  page: number;
  size: number;
  auctions: AuctionItem[];
}
