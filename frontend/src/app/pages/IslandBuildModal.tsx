import { buildingColors, buildingLabels, buildingNames, type BuildingType } from './islandGrid';

import type { BuildingTypeInfo } from '../types/island';

interface Props {
  selectedCell: { x: number; y: number } | null;
  selectedZone: 1 | 2 | 3 | 4 | undefined;
  gp: number;
  catalog: BuildingTypeInfo[];
  selectedBuilding: BuildingType | null;
  buildError: string;
  isBuilding: boolean;
  onSelectBuilding: (type: BuildingType) => void;
  onClose: () => void;
  onBuild: () => void;
}

function statLine(b: BuildingTypeInfo): string {
  const parts: string[] = [];
  if (b.gpProductionRate) parts.push(`GP +${b.gpProductionRate}/시간`);
  if (b.foodProductionRate) parts.push(`식량 +${b.foodProductionRate}/시간`);
  if (b.defensePower) parts.push(`방어력 +${b.defensePower}`);
  if (b.unitCapacityPerLevel) parts.push(`유닛 +${b.unitCapacityPerLevel}/레벨`);
  if (b.zoneRestriction && b.zoneRestriction > 0) parts.push(`Zone ${b.zoneRestriction} 전용`);
  return parts.join(' · ');
}

// 성은 섬에 하나뿐이고 시작 건물로 이미 배치되어 있다 — 건설 목록에서 제외.
function buildable(catalog: BuildingTypeInfo[]): BuildingTypeInfo[] {
  return catalog.filter(b => b.name !== 'CASTLE');
}

function buildTimeLabel(seconds: number | null): string {
  if (!seconds) return '즉시';
  if (seconds < 60) return `${seconds}초`;
  return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`.replace(' 0초', '');
}

export function IslandBuildModal({
  selectedCell, selectedZone, gp, catalog,
  selectedBuilding, buildError, isBuilding,
  onSelectBuilding, onClose, onBuild,
}: Props) {
  return (
    <div className="modal-side-overlay">
      <div className="modal-backdrop" onClick={onClose} />
      <div className="relative bg-panel border-l-2 border-gp w-[520px] flex flex-col overflow-hidden">
        <div className="bg-surface px-5 py-4 border-b-2 border-gp flex items-center justify-between">
          <div>
            <h3 className="text-gp font-bold text-lg">🏗 건물 건설</h3>
            {selectedCell
              ? <p className="text-muted text-xs">위치: ({selectedCell.x}, {selectedCell.y}) · Zone {selectedZone ?? 3} · 보유 GP: {gp.toLocaleString()}</p>
              : <p className="text-muted text-xs">빈 셀을 클릭하여 위치를 선택하세요 · 보유 GP: {gp.toLocaleString()}</p>
            }
          </div>
          <button onClick={onClose} className="btn-close">✕</button>
        </div>
        <div className="flex-1 overflow-y-auto p-4 space-y-2">
          {buildable(catalog).map(b => {
            const type = b.name.toLowerCase() as BuildingType;
            const color = b.colorHex ?? buildingColors[type] ?? '#8892b0';
            const icon = b.icon ?? buildingLabels[type] ?? '🏗';
            const isSelected = selectedBuilding === type;
            const affordable = gp >= b.baseCostGp;
            return (
              <div
                key={b.buildingTypeId}
                onClick={() => onSelectBuilding(type)}
                className="rounded-xl p-3 flex items-center gap-3 border transition-all cursor-pointer"
                style={{
                  background: isSelected ? color + '20' : '#2a3050',
                  borderColor: isSelected ? color : color + '60',
                  boxShadow: isSelected ? `0 0 8px ${color}40` : undefined,
                }}
              >
                <div className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: color + '25' }}>
                  <span className="text-[22px]">{icon}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-[13px] text-foreground">
                    {b.displayName ?? buildingNames[type] ?? b.name} <span className="text-muted font-normal text-[11px]">{b.width}×{b.height} · HP {b.maxHp} · 🔨 {buildTimeLabel(b.buildTimeSeconds)}</span>
                  </p>
                  <p className="text-muted text-[11px] truncate">{statLine(b) || '기능 없음'}</p>
                </div>
                <div className="border rounded px-2 py-1 flex-shrink-0" style={{ background: isSelected ? color + '30' : '#1a1f35', borderColor: affordable ? color : '#ff3333' }}>
                  <span className="text-xs" style={{ color: affordable ? color : '#ff3333' }}>{b.baseCostGp.toLocaleString()} GP</span>
                </div>
              </div>
            );
          })}
          {buildable(catalog).length === 0 && <p className="text-muted text-xs text-center py-8">건물 목록을 불러오는 중...</p>}
        </div>
        {buildError && (
          <div className="mx-4 mb-2 px-3 py-2 rounded-lg bg-danger/15 border border-danger">
            <span className="text-danger text-xs">⚠ {buildError}</span>
          </div>
        )}
        <div className="border-t border-outline p-4 flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 h-12 bg-elevated border border-outline rounded-xl text-muted text-sm"
          >
            취소
          </button>
          <button
            onClick={onBuild}
            disabled={!selectedBuilding || isBuilding}
            className={`flex-1 h-12 rounded-xl font-bold text-sm transition-all border ${selectedBuilding && !isBuilding ? 'bg-gp text-surface border-transparent cursor-pointer' : 'bg-elevated text-muted border-outline cursor-not-allowed'}`}
          >
            건설하기
          </button>
        </div>
      </div>
    </div>
  );
}
