import { UNIT_LABELS } from './islandGrid';

// 훈련 모달이 실제로 읽는 필드만 요구한다 — 카탈로그(UnitTypeCatalog)와 보유 유닛(UnitInfo) 양쪽이 만족.
interface TrainableUnit {
  unitTypeId: number;
  name: string;
  displayName: string | null;
  icon: string | null;
  colorHex: string | null;
  costGp: number;
  foodCost: number;
  /** 이 유닛(레벨 1)을 생산하는 데 필요한 병영 레벨. 보유 유닛(UnitInfo)에도 있음. */
  requiredBarracksLevel: number;
}

interface Props {
  units: TrainableUnit[];
  islandGp: number;
  storedFood: number;
  /** 이 위치의 병영 최고 레벨(0=병영 없음). 요구 레벨 초과 유닛은 잠긴다. */
  maxBarracksLevel: number;
  trainUnitTypeId: number | null;
  trainQuantity: number;
  trainLevel: number;
  /** unitTypeId → 연구로 해금된 최대 레벨 */
  researchedLevels: Record<number, number>;
  isTraining: boolean;
  onSelectUnit: (id: number) => void;
  onChangeQuantity: (q: number) => void;
  onChangeLevel: (lv: number) => void;
  onTrain: () => void;
  onClose: () => void;
}

export function IslandTrainUnitModal({
  units, islandGp, storedFood, maxBarracksLevel, trainUnitTypeId, trainQuantity, trainLevel, researchedLevels,
  isTraining, onSelectUnit, onChangeQuantity, onChangeLevel, onTrain, onClose,
}: Props) {
  const maxLevel = trainUnitTypeId ? (researchedLevels[trainUnitTypeId] ?? 1) : 1;
  const selectedLocked =
    trainUnitTypeId != null &&
    (units.find(u => u.unitTypeId === trainUnitTypeId)?.requiredBarracksLevel ?? 1) > maxBarracksLevel;
  return (
    <div className="modal-center-overlay">
      <div className="modal-backdrop" onClick={onClose} />
      <div className="relative rounded-2xl overflow-hidden flex flex-col w-[400px] bg-panel border-[1.5px] border-secondary">
        <div className="modal-header-secondary bg-[#1a0a35]">
          <div>
            <h3 className="text-secondary font-bold text-xl">⚔ 유닛 훈련</h3>
            <p className="text-muted text-xs">섬 GP: {islandGp.toLocaleString()} · 식량: {storedFood.toLocaleString()}</p>
          </div>
          <button onClick={onClose} className="btn-close">✕</button>
        </div>
        <div className="p-4 space-y-4">
          <div>
            <p className="text-muted text-xs mb-2">유닛 선택</p>
            <div className="grid grid-cols-3 gap-2">
              {units.map(u => {
                // 관리자 지정 값 우선, 없으면 기본 매핑
                const fallback = UNIT_LABELS[u.name] ?? { label: u.name, icon: '⚔', color: '#e0e8ff' };
                const meta = {
                  label: u.displayName ?? fallback.label,
                  icon: u.icon ?? fallback.icon,
                  color: u.colorHex ?? fallback.color,
                };
                const isSelected = trainUnitTypeId === u.unitTypeId;
                const isLocked = u.requiredBarracksLevel > maxBarracksLevel;
                return (
                  <button
                    key={u.unitTypeId}
                    onClick={() => onSelectUnit(u.unitTypeId)}
                    disabled={isLocked}
                    title={isLocked ? `병영 Lv.${u.requiredBarracksLevel} 필요` : ''}
                    className="rounded-xl p-2 flex flex-col items-center gap-1 transition-all disabled:cursor-not-allowed"
                    style={{
                      background: isSelected ? meta.color + '20' : 'var(--color-panel-deep)',
                      border: `1.5px solid ${isSelected ? meta.color : '#354064'}`,
                      color: meta.color,
                      opacity: isLocked ? 0.4 : 1,
                    }}
                  >
                    <span className="text-lg">{isLocked ? '🔒' : meta.icon}</span>
                    <span className="text-[11px] font-semibold">{meta.label}</span>
                    <span className="text-[10px] text-muted">
                      {isLocked ? `병영 Lv.${u.requiredBarracksLevel}` : `${u.costGp} GP · 식량 ${u.foodCost}`}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
          <div>
            <p className="text-muted text-xs mb-2">레벨 (연구 해금 Lv.{maxLevel}까지)</p>
            <div className="flex gap-2">
              {Array.from({ length: maxLevel }, (_, i) => i + 1).map(lv => (
                <button
                  key={lv}
                  onClick={() => onChangeLevel(lv)}
                  className="flex-1 rounded-xl py-2 text-[12px] font-bold transition-all"
                  style={{
                    background: trainLevel === lv ? '#ff44cc20' : 'var(--color-panel-deep)',
                    border: `1.5px solid ${trainLevel === lv ? '#ff44cc' : '#354064'}`,
                    color: trainLevel === lv ? '#ff44cc' : '#8892b0',
                  }}
                >
                  Lv.{lv}
                </button>
              ))}
            </div>
            {maxLevel === 1 && (
              <p className="text-muted text-[10px] mt-1">연구소에서 상위 레벨을 연구하면 선택할 수 있습니다.</p>
            )}
          </div>

          <div>
            <p className="text-muted text-xs mb-1">수량</p>
            <input
              type="number"
              min={1}
              value={trainQuantity}
              onChange={e => onChangeQuantity(Math.max(1, parseInt(e.target.value) || 1))}
              className="w-full h-9 rounded-xl px-3 text-sm bg-elevated border border-outline text-foreground"
            />
          </div>
          {selectedLocked && (
            <p className="text-danger text-[11px]">⚠ 병영 레벨이 부족해 이 유닛을 생산할 수 없습니다.</p>
          )}
          <button
            onClick={onTrain}
            disabled={isTraining || !trainUnitTypeId || selectedLocked}
            className="w-full h-10 rounded-xl font-semibold text-sm transition-all hover:brightness-110 disabled:opacity-50 bg-secondary/20 text-secondary border-[1.5px] border-secondary"
          >
            {isTraining ? '훈련 중...' : '훈련하기'}
          </button>
        </div>
      </div>
    </div>
  );
}
