import { useEffect, useState } from 'react';

import { fetchDecorationShop, purchaseDecoration } from '../api/island';
import { ApiError } from '../api/client';

import { buildingLabels, buildingColors, type BuildingType } from './islandGrid';

import type { BuildingTypeInfo } from '../types/island';

interface Props {
  ap: number;
  onPurchased: (apRemaining: number) => void;
  onClose: () => void;
}

export function IslandDecorationShopModal({ ap, onPurchased, onClose }: Props) {
  const [items, setItems] = useState<BuildingTypeInfo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => {
    fetchDecorationShop()
      .then(setItems)
      .catch(e => { setError(e instanceof ApiError ? e.message : '상점을 불러올 수 없습니다.'); console.warn('[DecorationShop] fetch', e); });
  }, []);

  const buy = async (b: BuildingTypeInfo) => {
    if (busyId != null) return;
    if (!window.confirm(`${b.displayName ?? b.name}을(를) ${b.apCost} AP에 구매할까요?`)) return;
    setBusyId(b.buildingTypeId); setError(null); setMessage(null);
    try {
      const r = await purchaseDecoration(b.buildingTypeId);
      onPurchased(r.apRemaining);
      setMessage(`${b.displayName ?? b.name} 구매 완료 — 보관함에 담겼습니다.`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '구매에 실패했습니다.');
    } finally { setBusyId(null); }
  };

  return (
    <div className="modal-side-overlay">
      <div className="modal-backdrop" onClick={onClose} />
      <div className="relative bg-panel border-l-2 border-gold w-[480px] flex flex-col overflow-hidden">
        <div className="bg-surface px-5 py-4 border-b-2 border-gold flex items-center justify-between">
          <div>
            <h3 className="text-gold font-bold text-lg">🛒 장식 상점</h3>
            <p className="text-muted text-xs">AP로 장식 블록을 구매합니다 · 보유 {ap.toLocaleString()} AP</p>
          </div>
          <button onClick={onClose} className="btn-close">✕</button>
        </div>

        {error && <p className="text-danger text-xs px-5 pt-3">⚠ {error}</p>}
        {message && <p className="text-gp text-xs px-5 pt-3">✓ {message}</p>}

        <div className="flex-1 overflow-y-auto p-4 space-y-2">
          {items.map(b => {
            const type = b.name.toLowerCase() as BuildingType;
            const color = b.colorHex ?? buildingColors[type] ?? '#8892b0';
            const icon = b.icon ?? buildingLabels[type] ?? '🏗';
            const affordable = ap >= (b.apCost ?? 0);
            return (
              <div key={b.buildingTypeId} className="rounded-xl p-3 flex items-center gap-3 border border-outline bg-elevated">
                <div className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: color + '25' }}>
                  <span className="text-[22px]">{icon}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-[13px] text-foreground">
                    {b.displayName ?? b.name} <span className="text-muted font-normal text-[11px]">{b.width}×{b.height} · HP {b.maxHp}{b.defensePower ? ` · 방어 ${b.defensePower}` : ''}</span>
                  </p>
                  <p className="text-gold text-[12px] font-bold">{b.apCost?.toLocaleString()} AP</p>
                </div>
                <button onClick={() => void buy(b)} disabled={busyId === b.buildingTypeId || !affordable}
                  className="h-8 px-4 rounded-lg bg-gold text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">
                  {busyId === b.buildingTypeId ? '구매 중' : affordable ? '구매' : 'AP 부족'}
                </button>
              </div>
            );
          })}
          {items.length === 0 && !error && <p className="text-muted text-xs text-center py-8">판매 중인 장식이 없습니다.</p>}
        </div>
        <div className="border-t border-outline p-3">
          <p className="text-muted text-[11px] text-center">구매한 장식은 <b>📦 보관함</b>에서 섬에 배치할 수 있습니다.</p>
        </div>
      </div>
    </div>
  );
}
