import { apiClient } from './client';
import type { AuctionBidsResponse, PlaceBidResponse, MyBidsResponse, TerritoryAuctionHistoryResponse, AuctionListResponse } from '../types/auction';

export function fetchAuctionList(continentId: number) {
  return apiClient.get<AuctionListResponse>(
    `/auctions?continentId=${continentId}&status=BIDDING&size=100`,
  );
}

export function fetchAuctionBids(auctionId: number) {
  return apiClient.get<AuctionBidsResponse>(`/auctions/${auctionId}/bids`);
}

export function placeBidApi(auctionId: number, bidAmount: number) {
  return apiClient.post<PlaceBidResponse>(`/auctions/${auctionId}/bids`, { bidAmount });
}

export function fetchMyBids() {
  return apiClient.get<MyBidsResponse>('/auctions/my-bids');
}

export function fetchTerritoryAuctionHistory(territoryId: number) {
  return apiClient.get<TerritoryAuctionHistoryResponse>(`/auctions/territories/${territoryId}`);
}
