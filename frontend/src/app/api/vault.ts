import { apiClient } from './client';
import type { GlobalVaultResponse, VaultTransferResponse, MyTerritory } from '../types/vault';

export function fetchGlobalVault() {
  return apiClient.get<GlobalVaultResponse>('/global-vault');
}

export function transferGP(direction: 'TO_VAULT' | 'FROM_VAULT', sourceTerritoryId: number, amount: number) {
  return apiClient.post<VaultTransferResponse>('/global-vault/transfer', {
    direction,
    sourceTerritoryId,
    amount,
  });
}

export function fetchMyTerritories() {
  return apiClient.get<{ totalCount: number; territories: MyTerritory[] }>('/users/me/territories');
}
