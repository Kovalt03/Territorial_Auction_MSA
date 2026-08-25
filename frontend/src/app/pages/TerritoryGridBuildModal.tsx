import { buildingColors, buildingLabels, buildingNames, type BuildingType } from './islandGrid';

import type { BuildingTypeInfo } from '../types/island';

interface Props {
  selectedCell: { x: number; y: number } | null;
  selectedZone: 1 | 2 | 3 | undefined;
  gp: number;
  catalog: BuildingTypeInfo[];
  selectedBuilding: BuildingType | null;
  buildError: string;
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
  return parts.join(' · ');
}

function buildTimeLabel(seconds: number | null): string {
  if (!seconds) return '즉시';
  if (seconds < 60) return `${seconds}초`;
  return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`.replace(' 0초', '');
}

// 양수 zoneRestriction 은 해당 Zone 전용, 음수는 |값| 이상 Zone에만 배치 가능
function isZoneAllowed(b: BuildingTypeInfo, zone: 1 | 2 | 3 | undefined): boolean {
  if (b.zoneRestriction == null || zone == null) return true;
  return b.zoneRestriction > 0 ? zone === b.zoneRestriction : zone >= -b.zoneRestriction;
}

function zoneLabel(b: BuildingTypeInfo): string {
  if (b.zoneRestriction == null) return '어디든';
  return b.zoneRestriction > 0 ? `Zone ${b.zoneRestriction} 전용` : `Zone ${-b.zoneRestriction} 이상`;
}

export function TerritoryGridBuildModal({
  selectedCell, selectedZone, gp, catalog,
  selectedBuilding, buildError,
  onSelectBuilding, onClose, onBuild,
}: Props) {
  return (
    <div className="modal-side-overlay">
      <div className="modal-backdrop" onClick={onClose} />
      <div className="relative bg-panel border-[1.5px] border-primary w-[540px] flex flex-col overflow-hidden">
        <div className="bg-elevated px-5 py-4 border-b-2 border-primary flex items-center justify-between">
          <div>
            <h3 className="text-foreground font-bold text-xl">🏗  건물 건설</h3>
            <p className="text-muted text-xs">
              {selectedCell
                ? `위치: (${selectedCell.x}, ${selectedCell.y}) · Zone ${selectedZone ?? '-'} · `
                : '빈 셀을 클릭하여 위치를 선택하세요 · '}
              보유 GP: {gp.toLocaleString()}
            </p>
          </div>
          <button onClick={onClose} className="btn-close">✕</button>
        </div>

        <div className="flex-1 overflow-y-auto px-4 pb-4 mt-4 space-y-2">
          {catalog.map(b => {
            const type = b.name.toLowerCase() as BuildingType;
            const color = b.colorHex ?? buildingColors[type] ?? '#8892b0';
            const icon = b.icon ?? buildingLabels[type] ?? '🏗';
            const isSel = selectedBuilding === type;
            const zoneBlocked = !isZoneAllowed(b, selectedZone);
            const affordable = gp >= b.baseCostGp;
            return (
              <div
                key={b.buildingTypeId}
                onClick={() => !zoneBlocked && onSelectBuilding(type)}
                className={`rounded-xl p-4 flex items-center gap-3 border transition-all ${zoneBlocked ? 'opacity-40 cursor-not-allowed' : 'cursor-pointer'}`}
                style={{
                  background: isSel ? color + '20' : '#2a3050',
                  borderColor: isSel ? color : color + '60',
                  boxShadow: isSel ? `0 0 8px ${color}40` : undefined,
                }}
              >
                <div className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: color + '30' }}>
                  <span className="text-[22px]">{icon}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-foreground font-semibold text-sm">{b.displayName ?? buildingNames[type] ?? b.name}</p>
                  <p className="text-muted text-[11px] truncate">{statLine(b) || '기능 없음'}</p>
                  <p className="text-muted text-[10px]">
                    {b.width}×{b.height} · {zoneLabel(b)} · 🔨 {buildTimeLabel(b.buildTimeSeconds)}
                  </p>
                </div>
                {zoneBlocked ? (
                  <div className="bg-danger/10 border border-danger/25 rounded px-2 py-1 flex-shrink-0">
                    <span className="text-danger text-[10px]">Zone 제한</span>
                  </div>
                ) : (
                  <div className="border rounded px-2 py-1 flex-shrink-0" style={{ background: isSel ? color + '30' : 'var(--color-panel)', borderColor: affordable ? color : '#ff3333' }}>
                    <span className="text-xs" style={{ color: affordable ? color : '#ff3333' }}>{b.baseCostGp.toLocaleString()} GP</span>
                  </div>
                )}
              </div>
            );
          })}
          {catalog.length === 0 && <p className="text-muted text-xs text-center py-8">건물 목록을 불러오는 중...</p>}
        </div>

        {buildError && (
          <div className="mx-4 mb-2 px-3 py-2 rounded-lg bg-danger/15 border border-danger">
            <span className="text-danger text-xs">⚠ {buildError}</span>
          </div>
        )}

        <div className="border-t border-outline p-4 flex gap-3">
          <button onClick={onClose} className="flex-1 h-14 bg-elevated border border-outline rounded-xl text-muted text-sm">
            취소
          </button>
          <button
            onClick={onBuild}
            disabled={!selectedBuilding}
            className={`flex-1 h-14 rounded-xl font-bold transition-all text-sm border ${selectedBuilding ? 'bg-primary text-surface border-transparent cursor-pointer' : 'bg-elevated text-muted border-outline cursor-not-allowed'}`}
          >
            건설하기
          </button>
        </div>
      </div>
    </div>
  );
}
