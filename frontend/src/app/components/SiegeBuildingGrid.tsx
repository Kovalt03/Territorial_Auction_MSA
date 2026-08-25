import type { SiegeTargetBuilding } from '../api/siege';

const ZONE_COLOR: Record<number, string> = { 1: '#ff3333', 2: '#ffd700', 3: '#00f5ff' };

const BUILDING_ICON: Record<string, string> = {
  CASTLE: '🏯', STORAGE: '📦', WORKSHOP: '🏭', BARRACKS: '⚔️',
  RESIDENCE: '🏠', FARMLAND: '🌾', WALL: '🧱', TOWER: '🗼', RESEARCH_LAB: '🔬',
};

interface Props {
  buildings: SiegeTargetBuilding[];
  gridSize?: number;
  /** 공격받는 존 — 그 존 건물을 강조한다. null이면 강조 없음. */
  highlightZone?: number | null;
  /** 정밀 공격 대상 건물 — 빨간 펄스로 표시. */
  targetBuildingId?: number | null;
  /** 지정 시 건물이 클릭 가능해진다. */
  onPickBuilding?: (b: SiegeTargetBuilding) => void;
}

// 영토 격자에 실제 건물을 좌표대로 배치해 보여준다. 겹칠 때 각 건물의 좌상단(아이콘) 칸을
// 다른 건물 몸통보다 우선 소유하게 해 아이콘이 사라지지 않는다.
export function SiegeBuildingGrid({
  buildings,
  gridSize = 10,
  highlightZone = null,
  targetBuildingId = null,
  onPickBuilding,
}: Props) {
  const size = gridSize;
  const cellMap = new Map<string, SiegeTargetBuilding>();
  buildings.forEach(b => {
    for (let dx = 0; dx < b.width; dx++) {
      for (let dy = 0; dy < b.height; dy++) {
        const cx = b.posX + dx;
        const cy = b.posY + dy;
        if (cx < 0 || cx >= size || cy < 0 || cy >= size) continue;
        const key = `${cx},${cy}`;
        const existing = cellMap.get(key);
        const isThisTopLeft = cx === b.posX && cy === b.posY;
        const existingIsTopLeft = existing != null && cx === existing.posX && cy === existing.posY;
        if (existing == null || (isThisTopLeft && !existingIsTopLeft)) cellMap.set(key, b);
      }
    }
  });

  return (
    <div className="grid gap-0.5" style={{ gridTemplateColumns: `repeat(${size}, 1fr)` }}>
      {Array.from({ length: size * size }, (_, i) => {
        const x = i % size;
        const y = Math.floor(i / size);
        const b = cellMap.get(`${x},${y}`);
        if (!b) {
          return (
            <div
              key={i}
              className="aspect-square rounded-sm"
              style={{ background: '#0a0e1a', border: '1px solid #1a1f35' }}
            />
          );
        }
        const isTopLeft = x === b.posX && y === b.posY;
        const isTarget = targetBuildingId != null && targetBuildingId === b.buildingId;
        const isAttacked = highlightZone != null && b.zone === highlightZone;
        const col = ZONE_COLOR[b.zone] ?? '#8892b0';
        const bgAlpha = isTarget ? '66' : isAttacked ? '44' : '2a';
        const style = {
          background: col + bgAlpha,
          border: `1px solid ${isTarget ? '#ff3333' : col}`,
          boxShadow: isTarget ? '0 0 0 1px #ff3333 inset' : undefined,
          opacity: b.isUnderConstruction ? 0.5 : 1,
        };
        const icon = isTopLeft ? (BUILDING_ICON[b.name] ?? '▪') : '';
        const title = `${b.displayName ?? b.name} · Zone ${b.zone} · ${b.currentHp}/${b.maxHp} HP${b.isUnderConstruction ? ' · 건설 중' : ''}`;
        const cls = `aspect-square rounded-sm flex items-center justify-center ${isTarget ? 'animate-pulse' : ''}`;
        return onPickBuilding ? (
          <button key={i} onClick={() => onPickBuilding(b)} title={title} className={cls} style={style}>
            <span className="text-[10px] leading-none">{icon}</span>
          </button>
        ) : (
          <div key={i} title={title} className={cls} style={style}>
            <span className="text-[10px] leading-none">{icon}</span>
          </div>
        );
      })}
    </div>
  );
}
