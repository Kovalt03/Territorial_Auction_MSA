import { apiClient } from './client';
import type { ItemInventoryResponse, ItemListResponse, PurchaseItemResponse } from '../types/item';

export function fetchItemList() {
  return apiClient.get<ItemListResponse>('/items');
}

export function purchaseItem(itemId: number, quantity: number) {
  return apiClient.post<PurchaseItemResponse>('/items/purchase', { itemId, quantity });
}

export function fetchInventory() {
  return apiClient.get<ItemInventoryResponse>('/items/inventory');
}
