export interface GlobalVaultResponse {
  storedGP: number;
  capacity: number;
  lastTransferAt: string | null;
  nextTransferAvailableAt: string | null;
  isTransferAvailable: boolean;
}

export interface VaultTransferResponse {
  direction: string;
  transferredAmount: number;
  sourceTerritoryId: number;
  territoryStorageAfter: number;
  vaultStoredAfter: number;
  vaultCapacity: number;
  nextTransferAvailableAt: string | null;
}

export interface MyTerritory {
  territoryId: number;
  grade: string;
  position: { x: number; y: number };
  continentName: string;
  occupiedAt: string | null;
  occupiedUntil: string | null;
}
