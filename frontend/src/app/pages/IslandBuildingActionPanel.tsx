import { buildingColors, buildingNames, type Cell } from './islandGrid';

import type { BuildingTypeInfo } from '../types/island';

interface Props {
  selectedCell: { x: number; y: number };
  cellData: Cell;
  info?: BuildingTypeInfo;
  onStartMove: () => void;
  onStoreBuilding: () => void;
  onUpgrade: () => void;
  onTrain: () => void;
  onHarvest: () => void;
  onRush: () => void;
  onClose: () => void;
}

const MAX_BUILDING_LEVEL = 3;
// 백엔드 BuildingPolicy.RUSH_AP_PER_MINUTE 와 일치.
const RUSH_AP_PER_MINUTE = 10;

function rushApCost(buildCompleteAt: string): number {
  const remainingSec = Math.max(0, (new Date(buildCompleteAt).getTime() - Date.now()) / 1000);
  return Math.ceil(remainingSec / 60) * RUSH_AP_PER_MINUTE;
}

function statLine(b: BuildingTypeInfo, level: number): string {
  const parts: string[] = [];
  if (b.gpProductionRate) parts.push(`GP +${b.gpProductionRate * level}/시간`);
  if (b.foodProductionRate) parts.push(`식량 +${b.foodProductionRate * level}/시간`);
  if (b.defensePower) parts.push(`방어력 +${b.defensePower}`);
  if (b.unitCapacityPerLevel) parts.push(`유닛 수용 +${b.unitCapacityPerLevel * level}`);
  return parts.join(' · ');
}

export function IslandBuildingActionPanel({
  selectedCell, cellData, info, onStartMove, onStoreBuilding, onUpgrade, onTrain, onHarvest, onRush, onClose,
}: Props) {
  const color = buildingColors[cellData.type];
  const isBuilding = !!cellData.buildCompleteAt && new Date(cellData.buildCompleteAt).getTime() > Date.now();
  const curLevel = cellData.level ?? 1;
  const isMaxLevel = curLevel >= MAX_BUILDING_LEVEL;
  const isCastle = cellData.type === 'castle';
  const isBarracks = cellData.type === 'barracks';
  // 수확은 생산 건물(생산소)의 기능 — 저장소는 저장만 담당한다.
  const isWorkshop = cellData.type === 'workshop';
  const stats = info ? statLine(info, curLevel) : '';

  return (
    <div className="modal-sheet-overlay">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="modal-sheet-panel">
        <div
          className="modal-header"
          style={{ background: color + '20', borderBottom: `2px solid ${color}` }}
        >
          <div>
            <h3 className="font-bold text-lg" style={{ color }}>
              {info?.displayName ?? buildingNames[cellData.type]}
            </h3>
            <p className="text-muted text-xs">
              위치: ({selectedCell.x}, {selectedCell.y}) · Zone {cellData.zone} · Lv.{cellData.level}
            </p>
          </div>
          <button onClick={onClose} className="btn-close">✕</button>
        </div>

        <div className="px-5 py-3 border-b border-outline">
          <div className="flex justify-between mb-1">
            <span className="text-muted text-[11px]">HP</span>
            <span className="text-[11px]" style={{ color }}>{cellData.hp} / {cellData.maxHp}</span>
          </div>
          <div className="h-2 bg-surface rounded-full overflow-hidden">
            <div
              className="h-full rounded-full"
              style={{
                width: `${cellData.maxHp ? Math.round((cellData.hp! / cellData.maxHp!) * 100) : 0}%`,
                background: color,
              }}
            />
          </div>
          {stats && <p className="text-muted text-[11px] mt-2">⚙ {stats}</p>}
        </div>

        {isBuilding && (
          <div className="px-4 pt-4">
            <button
              onClick={onRush}
              className="w-full h-12 rounded-xl font-bold border-[1.5px] border-primary text-primary hover:bg-primary/10 transition-all text-[13px]"
            >
              ⚡ AP로 즉시 완료 ({rushApCost(cellData.buildCompleteAt!).toLocaleString()} AP)
            </button>
          </div>
        )}

        {(isBarracks || isWorkshop) && (
          <div className="px-4 pt-4">
            {isBarracks && (
              <button
                onClick={onTrain}
                className="w-full h-12 rounded-xl font-bold border-[1.5px] border-secondary text-secondary hover:bg-secondary/10 transition-all text-[13px]"
              >
                ⚔ 유닛 훈련
              </button>
            )}
            {isWorkshop && (
              <button
                onClick={onHarvest}
                className="w-full h-12 rounded-xl font-bold border-[1.5px] border-gold text-gold hover:bg-gold/10 transition-all text-[13px]"
              >
                🌾 GP 수확
              </button>
            )}
          </div>
        )}

        <div className="p-4 flex gap-3">
          <button
            onClick={onStartMove}
            className="flex-1 h-12 rounded-xl font-semibold border border-gold/40 text-gold hover:bg-gold/10 transition-all text-[13px]"
          >
            🔄 이동하기
          </button>
          <button
            onClick={onStoreBuilding}
            disabled={isCastle}
            className={`flex-1 h-12 rounded-xl font-semibold border transition-all text-[13px] ${isCastle ? 'text-outline border-outline cursor-not-allowed' : 'text-secondary border-secondary/40 cursor-pointer'}`}
            title={isCastle ? '성은 보관함에 담을 수 없습니다' : ''}
          >
            📦 보관함에 담기
          </button>
          <button
            onClick={onClose}
            className="flex-1 h-12 bg-elevated border border-outline rounded-xl text-muted text-[13px]"
          >
            닫기
          </button>
        </div>
        <div className="px-4 pb-4">
          <button
            onClick={onUpgrade}
            disabled={isMaxLevel}
            className={`w-full h-10 rounded-xl font-semibold border transition-all text-[13px] ${isMaxLevel ? 'text-outline border-outline cursor-not-allowed' : 'text-primary border-primary/40 cursor-pointer'}`}
          >
            {isMaxLevel ? '⬆ 업그레이드 (최대 레벨)' : `⬆ 업그레이드 Lv.${curLevel} → Lv.${curLevel + 1}`}
          </button>
        </div>
        {isCastle && (
          <p className="text-center text-muted pb-3 text-[11px]">성(Castle)은 핵심 건물로 보관함에 담을 수 없습니다</p>
        )}
      </div>
    </div>
  );
}
