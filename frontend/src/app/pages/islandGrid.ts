import type { IslandData } from '../types/island';

// 백엔드 building_types 시드와 1:1 대응 (name.toLowerCase()). empty는 빈 셀.
export type BuildingType = 'castle' | 'storage' | 'workshop' | 'barracks' | 'wall' | 'tower' | 'farmland' | 'residence' | 'research_lab' | 'empty';

export interface Cell {
  type: BuildingType;
  level?: number;
  hp?: number;
  maxHp?: number;
  zone?: 1 | 2 | 3;
  buildingId?: number;
  isBody?: boolean;
  width?: number;
  height?: number;
  buildCompleteAt?: string | null;
}

export const buildingColors: Record<BuildingType, string> = {
  castle: '#ffd700', storage: '#00f5ff', workshop: '#00ff88', barracks: '#8b50ff',
  wall: '#e0e8ff', tower: '#ff8c00', farmland: '#a3e635', residence: '#44aaff',
  research_lab: '#ff44cc', empty: '#1a1f35',
};

export const buildingLabels: Record<BuildingType, string> = {
  castle: '🏰', storage: '📦', workshop: '⚙', barracks: '⚔',
  wall: '🧱', tower: '🗼', farmland: '🌾', residence: '🏠',
  research_lab: '🔬', empty: '',
};

export const buildingNames: Record<BuildingType, string> = {
  castle: '성', storage: '저장소', workshop: '생산소', barracks: '병영',
  wall: '방벽', tower: '방어탑', farmland: '농지', residence: '주거지',
  research_lab: '연구소', empty: '빈 공간',
};

export const UNIT_LABELS: Record<string, { label: string; icon: string; color: string }> = {
  INFANTRY: { label: '보병', icon: '🗡', color: '#e0e8ff' },
  ARCHER: { label: '궁수', icon: '🏹', color: '#00ff88' },
  KNIGHT: { label: '기사', icon: '⚔', color: '#ffd700' },
};

// 백엔드 ZonePolicy.calculateZone 과 동일. 짝수 크기 격자의 중심은 반칸 위치라
// 좌표·반경을 2배로 환산해 정수 연산으로 좌우 대칭을 유지한다.
export function assignZone(x: number, y: number, size: number, zone1Radius: number, zone2Radius: number): 1 | 2 | 3 {
  const doubledCenter = size - 1;
  const dist = Math.max(Math.abs(2 * x - doubledCenter), Math.abs(2 * y - doubledCenter));
  if (dist <= 2 * zone1Radius) return 1;
  if (dist <= 2 * zone2Radius) return 2;
  return 3;
}

export function emptyGrid(size: number, zone1Radius: number, zone2Radius: number): Cell[][] {
  return Array.from({ length: size }, (_, y) =>
    Array.from({ length: size }, (_, x) => ({ type: 'empty' as BuildingType, zone: assignZone(x, y, size, zone1Radius, zone2Radius) }))
  );
}

// 그리드에 얹을 수 있는 최소 형태 — 섬·영토가 공유한다.
export interface GridBuilding {
  buildingId: number;
  type: string;
  posX: number;
  posY: number;
  width?: number;
  height?: number;
  hp: number;
  maxHp: number;
  level: number;
  isDestroyed: boolean;
  buildCompleteAt: string | null;
}

export function buildGrid(size: number, z1: number, z2: number, buildings: GridBuilding[]): Cell[][] {
  const grid = emptyGrid(size, z1, z2);
  for (const b of buildings) {
    if (b.isDestroyed || b.posY >= size || b.posX >= size) continue;
    const w = b.width ?? 1;
    const h = b.height ?? 1;
    const type = b.type.toLowerCase() as BuildingType;
    for (let dy = 0; dy < h; dy++) {
      for (let dx = 0; dx < w; dx++) {
        const gx = b.posX + dx;
        const gy = b.posY + dy;
        if (gx >= size || gy >= size) continue;
        grid[gy][gx] = {
          type,
          level: b.level,
          hp: b.hp,
          maxHp: b.maxHp,
          buildingId: b.buildingId,
          buildCompleteAt: b.buildCompleteAt,
          zone: assignZone(gx, gy, size, z1, z2),
          isBody: dx > 0 || dy > 0,
          width: w,
          height: h,
        };
      }
    }
  }
  return grid;
}

export function buildGridFromIsland(island: IslandData): Cell[][] {
  return buildGrid(island.gridSize, island.zone1Radius, island.zone2Radius, island.buildings);
}

export function isUnderConstruction(buildCompleteAt: string | null | undefined, now: number): boolean {
  return !!buildCompleteAt && new Date(buildCompleteAt).getTime() > now;
}

// 남은 시간은 초 단위까지 보여준다 — 1분 이상이면 m:ss, 미만이면 초.
export function remainingLabel(buildCompleteAt: string | null | undefined, now: number): string {
  if (!buildCompleteAt) return '';
  const seconds = Math.max(0, Math.ceil((new Date(buildCompleteAt).getTime() - now) / 1000));
  if (seconds < 60) return `${seconds}초`;
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

export function findOriginCell(grid: Cell[][], buildingId: number): { x: number; y: number } | null {
  for (let gy = 0; gy < grid.length; gy++) {
    for (let gx = 0; gx < grid[gy].length; gx++) {
      const c = grid[gy][gx];
      if (c.buildingId === buildingId && !c.isBody) return { x: gx, y: gy };
    }
  }
  return null;
}

export function clearBuildingCells(grid: Cell[][], buildingId: number): Cell[][] {
  return grid.map(row =>
    row.map(c => c.buildingId === buildingId ? { type: 'empty' as BuildingType, zone: c.zone } : { ...c })
  );
}
