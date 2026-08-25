import { HealthBar } from '../components/HealthBar';

import type { Cell } from './islandGrid';

interface Props {
  selectedCell: { x: number; y: number };
  cellData: Cell;
  isUnderConstruction: boolean;
  color: string;
  name: string;
  busy: boolean;
  onStartMove: () => void;
  onStoreBuilding: () => void;
  onUpgrade: () => void;
  onVaultTransfer: () => void;
  onGarrison: () => void;
  onTrain: () => void;
  onRepair: () => void;
  onClose: () => void;
}

const GARRISONABLE = new Set(['castle', 'residence', 'tower', 'wall']);

export function TerritoryGridBuildingActionPanel({
  selectedCell, cellData, isUnderConstruction, color, name, busy,
  onStartMove, onStoreBuilding, onUpgrade, onVaultTransfer, onGarrison, onTrain, onRepair, onClose,
}: Props) {
  const isCastle = cellData.type === 'castle';
  const isStorageBuilding = isCastle || cellData.type === 'storage';
  const isGarrisonable = GARRISONABLE.has(cellData.type);
  const isBarracks = cellData.type === 'barracks';
  const canAct = !busy && !isUnderConstruction;

  return (
    <div className="modal-sheet-overlay">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="modal-sheet-panel">
        <div className="modal-header" style={{ background: color + '20', borderBottom: `2px solid ${color}` }}>
          <div>
            <h3 className="font-bold text-lg" style={{ color }}>{name}</h3>
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
          <HealthBar hp={cellData.hp ?? 0} maxHp={cellData.maxHp ?? 0} color={color} height="h-2" bg="bg-surface" />
        </div>

        {isUnderConstruction && (
          <p className="text-center text-gold pt-3 text-[11px]">🔨 건설·수리 중에는 다른 작업을 할 수 없습니다 (완료까지 비활성)</p>
        )}

        {(cellData.hp ?? 0) < (cellData.maxHp ?? 0) && (
          <div className="px-4 pt-4">
            <button
              onClick={onRepair}
              disabled={!canAct}
              className="w-full h-12 rounded-xl font-bold border-[1.5px] border-gp text-gp hover:bg-gp/10 transition-all text-[13px] disabled:opacity-40 disabled:cursor-not-allowed"
              title={!canAct ? '건설·수리 중에는 수리할 수 없습니다' : '시간제 수리 — 손상 HP당 GP 차감, 수리 중 건물 비활성, 완료 시 HP 풀피'}
            >
              🔧 수리 (시간 소요 · 수리 중 비활성)
            </button>
          </div>
        )}

        {(isStorageBuilding || isGarrisonable || isBarracks) && (
          <div className="px-4 pt-4 space-y-2">
            {isBarracks && (
              <button
                onClick={onTrain}
                className="w-full h-12 rounded-xl font-bold border-[1.5px] border-secondary text-secondary hover:bg-secondary/10 transition-all text-[13px]"
              >
                ⚔ 유닛 훈련
              </button>
            )}
            {isStorageBuilding && (
              <button
                onClick={onVaultTransfer}
                className="w-full h-12 rounded-xl font-bold border-[1.5px] border-gold text-gold hover:bg-gold/10 transition-all text-[13px]"
              >
                🏦 금고 이전
              </button>
            )}
            {isGarrisonable && (
              <button
                onClick={onGarrison}
                className="w-full h-12 rounded-xl font-bold border-[1.5px] border-danger text-danger hover:bg-danger/10 transition-all text-[13px]"
              >
                🛡 유닛 주둔 / 회수
              </button>
            )}
          </div>
        )}

        <div className="p-4 grid grid-cols-2 gap-3">
          <button
            onClick={onUpgrade}
            disabled={!canAct}
            className="h-12 rounded-xl font-semibold border border-primary/40 text-primary hover:bg-primary/10 transition-all text-[13px] disabled:opacity-40"
          >
            ⬆ 업그레이드
          </button>
          <button
            onClick={onStartMove}
            disabled={!canAct}
            className="h-12 rounded-xl font-semibold border border-gold/40 text-gold hover:bg-gold/10 transition-all text-[13px] disabled:opacity-40"
          >
            🔄 이동하기
          </button>
          <button
            onClick={onStoreBuilding}
            disabled={!canAct || isCastle}
            className="h-12 rounded-xl font-semibold border border-secondary/40 text-secondary transition-all text-[13px] disabled:opacity-40 disabled:cursor-not-allowed"
            title={isCastle ? '성은 보관함에 담을 수 없습니다' : ''}
          >
            📦 보관함에 담기
          </button>
          <button onClick={onClose} className="h-12 bg-elevated border border-outline rounded-xl text-muted text-[13px]">
            닫기
          </button>
        </div>
        {isCastle && (
          <p className="text-center text-muted pb-3 text-[11px]">성(Castle)은 영토의 핵심 건물로 보관함에 담을 수 없습니다</p>
        )}
      </div>
    </div>
  );
}
