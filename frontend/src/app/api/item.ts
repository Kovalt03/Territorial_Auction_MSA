import { apiClient } from './client';
import type { ItemInventoryResponse, ItemListResponse, PurchaseItemResponse } from '../types/item';

export function fetchItemList() {
  return apiClient.get<ItemListResponse>('/items');
}

export function purchaseItem(itemId: number, quantity: number) {
  // 멱등 키를 호출당 1회 생성 — 인터셉터 401 재시도·네트워크 재전송에도 같은 키가 실려 AP 이중 차감을 막는다.
  const idempotencyKey = crypto.randomUUID();
  return apiClient.post<PurchaseItemResponse>('/items/purchase', { itemId, quantity, idempotencyKey });
}

export function fetchInventory() {
  return apiClient.get<ItemInventoryResponse>('/items/inventory');
}
