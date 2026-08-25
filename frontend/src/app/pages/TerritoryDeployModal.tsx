import { useMemo, useState } from 'react';

import { UNIT_LABELS } from './islandGrid';

import type { GarrisonUnit, LocationUnits } from '../types/military';

interface DeployParams {
  buildingId: number;
  unitTypeId: number;
  quantity: number;
  sourceLocationId: number;
  sourceLocationType: 'ISLAND' | 'TERRITORY';
}

interface Props {
  building: { buildingId: number; name: string; capacityPerLevel: number };
  locations: LocationUnits[];
  garrison: GarrisonUnit[];
  isBusy: boolean;
  onDeploy: (p: DeployParams) => void;
  onRecall: (unitTypeId: number, quantity: number) => void;
  onClose: () => void;
}

function locationLabel(loc: LocationUnits): string {
  return loc.locationType === 'ISLAND'
    ? '🏝 내 섬'
    : `🗺 영토 (${loc.coordX}, ${loc.coordY})`;
}

export function TerritoryDeployModal({
  building, locations, garrison, isBusy, onDeploy, onRecall, onClose,
}: Props) {
  // 출발 후보: 대기 유닛(idleCount>0)이 있는 위치
  const sources = useMemo(
    () => locations.filter(l => l.units.some(u => u.idleCount > 0)),
    [locations],
  );
  const [sourceIdx, setSourceIdx] = useState(0);
  const source = sources[sourceIdx];
  const idleUnits = source?.units.filter(u => u.idleCount > 0) ?? [];
  const [unitTypeId, setUnitTypeId] = useState<number | null>(null);
  const [quantity, setQuantity] = useState(1);

  const selected = idleUnits.find(u => u.unitTypeId === unitTypeId) ?? idleUnits[0];
  const maxQty = selected?.idleCount ?? 0;

  // 회수 후보: 이 영토에 배치된 유닛(백엔드 garrison 조회 — 배치 유닛은 귀속지에 잡혀 위치 응답으론 안 보임)
  const deployed = garrison;

  const meta = (name: string, u: { displayName: string | null; icon: string | null; colorHex: string | null }) => {
    const fb = UNIT_LABELS[name] ?? { label: name, icon: '⚔', color: '#e0e8ff' };
    return { label: u.displayName ?? fb.label, icon: u.icon ?? fb.icon, color: u.colorHex ?? fb.color };
  };

  const handleDeploy = () => {
    if (!source || !selected) return;
    onDeploy({
      buildingId: building.buildingId,
      unitTypeId: selected.unitTypeId,
      quantity: Math.min(quantity, maxQty),
      sourceLocationId: source.locationId,
      sourceLocationType: source.locationType,
    });
  };

  return (
    <div className="modal-center-overlay">
      <div className="modal-backdrop" onClick={onClose} />
      <div className="relative rounded-2xl overflow-hidden flex flex-col w-[420px] bg-panel border-[1.5px] border-danger">
        <div className="modal-header-secondary bg-[#2a0a0a]">
          <div>
            <h3 className="text-danger font-bold text-xl">🛡 유닛 주둔 — {building.name}</h3>
            <p className="text-muted text-xs">레벨당 수용 {building.capacityPerLevel}기 · 이 Zone 방어에 참여</p>
          </div>
          <button onClick={onClose} className="btn-close">✕</button>
        </div>

        <div className="p-4 space-y-4 max-h-[60vh] overflow-y-auto">
          {sources.length === 0 && (
            <p className="text-muted text-xs text-center py-3">주둔시킬 대기 유닛이 없습니다. 병영에서 먼저 훈련하세요.</p>
          )}

          {sources.length > 0 && (
            <>
              <div>
                <p className="text-muted text-xs mb-1">출발 위치</p>
                <select
                  value={sourceIdx}
                  onChange={e => { setSourceIdx(Number(e.target.value)); setUnitTypeId(null); setQuantity(1); }}
                  className="w-full h-9 rounded-xl px-3 text-sm bg-elevated border border-outline text-foreground"
                >
                  {sources.map((l, i) => <option key={`${l.locationType}-${l.locationId}`} value={i}>{locationLabel(l)}</option>)}
                </select>
              </div>

              <div>
                <p className="text-muted text-xs mb-2">유닛 선택 (대기 수량)</p>
                <div className="grid grid-cols-3 gap-2">
                  {idleUnits.map(u => {
                    const m = meta(u.name, u);
                    const on = (selected?.unitTypeId ?? -1) === u.unitTypeId;
                    return (
                      <button
                        key={u.unitTypeId}
                        onClick={() => { setUnitTypeId(u.unitTypeId); setQuantity(1); }}
                        className="rounded-xl p-2 flex flex-col items-center gap-1"
                        style={{ background: on ? m.color + '20' : 'var(--color-panel-deep)', border: `1.5px solid ${on ? m.color : '#354064'}`, color: m.color }}
                      >
                        <span className="text-lg">{m.icon}</span>
                        <span className="text-[11px] font-semibold">{m.label}</span>
                        <span className="text-[10px] text-muted">대기 {u.idleCount}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              <div>
                <p className="text-muted text-xs mb-1">수량 (최대 {maxQty})</p>
                <input
                  type="number" min={1} max={maxQty} value={quantity}
                  onChange={e => setQuantity(Math.max(1, Math.min(maxQty, parseInt(e.target.value) || 1)))}
                  className="w-full h-9 rounded-xl px-3 text-sm bg-elevated border border-outline text-foreground"
                />
              </div>

              <button
                onClick={handleDeploy}
                disabled={isBusy || !selected || maxQty < 1}
                className="w-full h-10 rounded-xl font-semibold text-sm bg-danger/20 text-danger border-[1.5px] border-danger disabled:opacity-50"
              >
                {isBusy ? '처리 중...' : '주둔시키기'}
              </button>
            </>
          )}

          {deployed.length > 0 && (
            <div className="border-t border-outline pt-3">
              <p className="text-muted text-xs mb-2">이 영토 배치 유닛 — 회수</p>
              <div className="space-y-2">
                {deployed.map(u => {
                  const m = meta(u.name, u);
                  return (
                    <div key={u.unitTypeId} className="flex items-center justify-between bg-panel-deep rounded-lg px-3 py-2">
                      <span className="text-xs" style={{ color: m.color }}>{m.icon} {m.label} · 배치 {u.deployedCount}</span>
                      <button
                        onClick={() => onRecall(u.unitTypeId, u.deployedCount)}
                        disabled={isBusy}
                        className="text-[11px] text-gold border border-gold/40 rounded-lg px-2 py-1 hover:bg-gold/10 disabled:opacity-50"
                      >
                        회수
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
