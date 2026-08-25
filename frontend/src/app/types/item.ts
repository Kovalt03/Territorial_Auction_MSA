export interface ItemInfo {
  itemId: number;
  name: string;
  itemType: string;
  description: string;
  costAP: number | null;
  costGP: number | null;
  dailyLimit: number | null;
  myInventory: number;
  iconUrl: string;
}

export interface ItemListResponse {
  items: ItemInfo[];
}

export interface PurchaseItemResponse {
  itemId: number;
  itemType: string;
  purchased: number;
  totalOwned: number;
  costAP: number;
  remainingAP: number;
}

export interface UserItemInfo {
  userItemId: number;
  itemId: number;
  itemName: string;
  itemType: string;
  description: string;
  quantity: number;
  acquiredAt: string;
}

export interface ItemInventoryResponse {
  totalCount: number;
  items: UserItemInfo[];
}
