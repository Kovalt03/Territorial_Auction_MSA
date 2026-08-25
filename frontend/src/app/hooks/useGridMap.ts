import { useState, useEffect, useRef } from 'react';

import { useStompSubscribe } from './useStompClient';
import { fetchGridMap } from '../api/map';
import type { GridTerritoryDto, MapUpdateBroadcast } from '../types/map';

export interface GridMapResult {
  territories: GridTerritoryDto[];
  cols: number;
  rows: number;
  minX: number;
  minY: number;
  isLoading: boolean;
  error: string | null;
}

export function useGridMap(continentId?: number): GridMapResult {
  const [territories, setTerritories] = useState<GridTerritoryDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const latestUpdatesRef = useRef(new Map<number, MapUpdateBroadcast>());

  useStompSubscribe<MapUpdateBroadcast>('/sub/map/update', (update) => {
    latestUpdatesRef.current.set(update.territoryId, update);
    setTerritories(current => current.map(territory => {
      if (territory.territoryId !== update.territoryId) return territory;
      return {
        ...territory,
        ownerId: update.ownerId,
        ownerNickname: update.ownerNickname,
        status: update.status,
        hasActiveAuction: false,
      };
    }));
  });

  useEffect(() => {
    setIsLoading(true);
    setError(null);
    setTerritories([]);
    fetchGridMap(continentId)
      .then(res => setTerritories(res.territories.map(territory => {
        const update = latestUpdatesRef.current.get(territory.territoryId);
        if (!update) return territory;
        return {
          ...territory,
          ownerId: update.ownerId,
          ownerNickname: update.ownerNickname,
          status: update.status,
          hasActiveAuction: false,
        };
      })))
      .catch(() => setError('지도 데이터를 불러올 수 없습니다.'))
      .finally(() => setIsLoading(false));
  }, [continentId]);

  if (territories.length === 0) {
    return { territories: [], cols: 0, rows: 0, minX: 0, minY: 0, isLoading, error };
  }

  let minX = territories[0].coordX;
  let maxX = territories[0].coordX;
  let minY = territories[0].coordY;
  let maxY = territories[0].coordY;

  for (const t of territories) {
    if (t.coordX < minX) minX = t.coordX;
    if (t.coordX > maxX) maxX = t.coordX;
    if (t.coordY < minY) minY = t.coordY;
    if (t.coordY > maxY) maxY = t.coordY;
  }

  return {
    territories,
    cols: maxX - minX + 1,
    rows: maxY - minY + 1,
    minX,
    minY,
    isLoading,
    error,
  };
}
