import { useState } from 'react';

import { useApp } from '../context/AppContext';
import { useItems } from '../hooks/useItems';
import { useInventory } from '../hooks/useInventory';
import { purchaseItem } from '../api/item';
import { fetchMyWallet } from '../api/user';

import { GNB } from '../components/GNB';

import type { ItemInfo } from '../types/item';

const ITEM_COLOR: Record<string, string> = {
  INVINCIBILITY:    '#00f5ff',
  ATTACK_NORMAL:    '#ff8c00',
  ATTACK_PRECISION: '#ff3333',
  GP_PURCHASE:      '#00ff88',
};

function itemColor(itemType: string): string {
  return ITEM_COLOR[itemType] ?? '#8892b0';
}

function itemIconUrl(itemType: string): string {
  return `/images/items/${itemType.toLowerCase()}.svg`;
}

function InventoryTab() {
  const { inventory, isLoading } = useInventory();

  if (isLoading) {
    return (
      <div className="space-y-3">
        {[1, 2, 3].map(i => (
          <div key={i} className="bg-panel border border-outline rounded-2xl h-20 animate-pulse" />
        ))}
      </div>
    );
  }

  if (inventory.length === 0) {
    return (
      <div className="text-center py-16 text-muted">
        <p className="text-4xl mb-3">📦</p>
        <p className="text-sm">보유 중인 아이템이 없습니다.</p>
        <p className="text-xs mt-1">아이템 샵에서 구매해보세요.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {inventory.map(item => {
        const color = itemColor(item.itemType);
        return (
          <div key={item.userItemId} className="bg-panel border rounded-2xl p-4 flex items-center gap-4"
            style={{ borderColor: color + '33' }}>
            <div className="w-12 h-12 flex-shrink-0">
              <img src={itemIconUrl(item.itemType)} alt={item.itemName} className="w-full h-full object-contain" />
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-foreground font-bold text-sm">{item.itemName}</p>
              <p className="text-muted text-xs truncate">{item.description}</p>
            </div>
            <div className="text-right flex-shrink-0">
              <p className="font-bold text-lg" style={{ color }}>{item.quantity}개</p>
              <p className="text-muted text-[10px]">보유 수량</p>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function ItemShopPage() {
  const { ap, gp, syncAP, syncGP } = useApp();
  const { items, isLoading, error, updateInventory } = useItems();
  const [tab, setTab] = useState<'shop' | 'inventory'>('shop');
  const [confirmItem, setConfirmItem] = useState<ItemInfo | null>(null);
  const [qty, setQty] = useState(1);
  const [isPurchasing, setIsPurchasing] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [purchaseError, setPurchaseError] = useState<string | null>(null);

  const openConfirm = (item: ItemInfo) => {
    setConfirmItem(item);
    setQty(1);
    setPurchaseError(null);
  };

  const maxQty = (item: ItemInfo): number => {
    const byBalance = item.costAP != null
      ? Math.floor(ap / item.costAP)
      : item.costGP != null ? Math.floor(gp / item.costGP) : 99;
    const byLimit = item.dailyLimit != null
      ? item.dailyLimit - item.myInventory
      : 99;
    return Math.max(1, Math.min(byBalance, byLimit));
  };

  const handlePurchase = async (item: ItemInfo, quantity: number) => {
    setConfirmItem(null);
    setIsPurchasing(true);
    setPurchaseError(null);
    try {
      const result = await purchaseItem(item.itemId, quantity);
      syncAP(result.remainingAP);
      updateInventory(item.itemId, result.totalOwned);
      if (item.itemType === 'GP_PURCHASE') {
        const wallet = await fetchMyWallet();
        syncGP(wallet.availableGP);
      }
      setSuccessMsg(`${item.name} ${quantity}개 구매 완료!`);
      setTimeout(() => setSuccessMsg(''), 3000);
    } catch {
      setPurchaseError('구매에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setIsPurchasing(false);
    }
  };

  return (
    <div className="page-root">
      <GNB />

      <div className="page-body">
        <div className="flex items-center justify-between mb-5">
          <h1 className="text-foreground font-bold text-[26px]">🛍  아이템 샵</h1>
          <div className="flex gap-3">
            <div className="bg-elevated border border-ap rounded-lg px-4 py-2 flex items-center gap-2">
              <span className="text-ap font-semibold text-sm">⚡ {ap.toLocaleString()} AP</span>
            </div>
            <div className="bg-elevated border border-gp rounded-lg px-4 py-2 flex items-center gap-2">
              <span className="text-gp font-semibold text-sm">💎 {gp.toLocaleString()} GP</span>
            </div>
          </div>
        </div>

        {/* 탭 */}
        <div className="flex gap-1 bg-panel-deep border border-outline-soft rounded-xl p-1 mb-5">
          {(['shop', 'inventory'] as const).map(t => (
            <button key={t} onClick={() => setTab(t)}
              className={`flex-1 py-2 rounded-lg text-sm font-semibold transition-all ${tab === t ? 'bg-elevated text-foreground shadow-sm' : 'text-muted hover:text-foreground-soft'}`}>
              {t === 'shop' ? '🛒 샵' : '📦 보유 아이템'}
            </button>
          ))}
        </div>

        {(error || purchaseError) && (
          <div className="bg-gold/10 border border-gold/25 rounded-xl px-4 py-2.5 mb-4">
            <span className="text-gold text-xs">⚠ {purchaseError ?? error}</span>
          </div>
        )}

        {successMsg && (
          <div className="bg-gp/10 border border-gp rounded-xl px-4 py-3 mb-4">
            <span className="text-gp text-[13px]">✓ {successMsg}</span>
          </div>
        )}

        {tab === 'inventory' ? (
          <InventoryTab />
        ) : isLoading ? (
          <div className="grid grid-cols-2 gap-4">
            {[1, 2, 3, 4].map(i => (
              <div key={i} className="bg-panel border border-outline rounded-2xl p-5 h-36 animate-pulse">
                <div className="flex gap-4">
                  <div className="w-16 h-16 rounded-2xl bg-elevated" />
                  <div className="flex-1 space-y-2">
                    <div className="h-4 bg-elevated rounded w-32" />
                    <div className="h-3 bg-elevated rounded w-24" />
                    <div className="h-6 bg-elevated rounded w-20" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4">
            {items.map(item => {
              const color = itemColor(item.itemType);
              const isExhausted = item.dailyLimit != null && item.myInventory >= item.dailyLimit;
              return (
                <div key={item.itemId}
                  className="bg-panel border rounded-2xl p-4 flex flex-col transition-all hover:brightness-[1.05]"
                  style={{ borderColor: color + '33' }}>
                  <div className="flex items-start gap-3 mb-3">
                    <div className="relative w-14 h-14 flex-shrink-0">
                      <img src={item.iconUrl} alt={item.name} className="w-full h-full object-contain" />
                      {item.myInventory > 0 && (
                        <span className="absolute -top-1.5 -right-1.5 min-w-[18px] h-[18px] px-1 rounded-full text-[10px] font-bold flex items-center justify-center"
                          style={{ background: color, color: '#0a0e1a' }}>
                          {item.myInventory}
                        </span>
                      )}
                    </div>
                    <div className="flex-1 min-w-0 pt-0.5">
                      <h3 className="text-foreground font-bold text-[15px] leading-tight">{item.name}</h3>
                      <p className="text-muted text-xs mt-1 leading-snug">{item.description}</p>
                    </div>
                    {item.dailyLimit != null && (
                      <span className="flex-shrink-0 bg-elevated rounded-md px-2 py-1 text-[10px] font-medium"
                        style={{ color: isExhausted ? '#7788a5' : color }}>
                        일 {item.myInventory}/{item.dailyLimit}
                      </span>
                    )}
                  </div>

                  <div className="mt-auto flex items-center justify-between gap-2 pt-3 border-t border-outline-soft">
                    <div className="flex items-baseline gap-1.5 flex-wrap">
                      {item.costAP != null && (
                        <span className="font-extrabold text-lg leading-none" style={{ color }}>
                          {item.costAP.toLocaleString()}<span className="text-[11px] font-bold ml-0.5">AP</span>
                        </span>
                      )}
                      {item.costGP != null && (
                        <>
                          {item.costAP != null && <span className="text-muted text-[11px]">또는</span>}
                          <span className="font-extrabold text-lg leading-none text-gp">
                            {item.costGP.toLocaleString()}<span className="text-[11px] font-bold ml-0.5">GP</span>
                          </span>
                        </>
                      )}
                    </div>
                    <button
                      onClick={() => openConfirm(item)}
                      disabled={isExhausted || isPurchasing}
                      className="flex-shrink-0 h-9 px-5 rounded-lg font-bold text-[13px] transition-all hover:brightness-110 disabled:opacity-40 disabled:cursor-not-allowed"
                      style={{ background: color, color: '#0a0e1a' }}>
                      {isExhausted ? '완료' : '구매'}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {confirmItem && (() => {
        const color = itemColor(confirmItem.itemType);
        const unitCost = confirmItem.costAP ?? confirmItem.costGP ?? 0;
        const totalCost = unitCost * qty;
        const useAP = confirmItem.costAP != null;
        const remaining = useAP ? ap - totalCost : gp - totalCost;
        const max = maxQty(confirmItem);
        return (
          <div className="modal-overlay">
            <div className="bg-panel rounded-2xl p-8 max-w-sm mx-4 text-center"
              style={{ border: `2px solid ${color}` }}>
              <img src={confirmItem.iconUrl} alt={confirmItem.name} className="w-14 h-14 object-contain mx-auto" />
              <h3 className="text-foreground font-bold text-xl mt-3 mb-1">{confirmItem.name}</h3>
              <p className="text-muted mb-5 text-[13px]">{confirmItem.description}</p>

              {/* 수량 선택 */}
              <div className="flex items-center justify-center gap-4 mb-5">
                <button
                  onClick={() => setQty(q => Math.max(1, q - 1))}
                  disabled={qty <= 1}
                  className="w-10 h-10 rounded-xl border border-outline text-foreground text-xl font-bold disabled:opacity-30 hover:border-primary transition-colors">
                  −
                </button>
                <span className="text-foreground font-bold text-3xl w-12 text-center">{qty}</span>
                <button
                  onClick={() => setQty(q => Math.min(max, q + 1))}
                  disabled={qty >= max}
                  className="w-10 h-10 rounded-xl border border-outline text-foreground text-xl font-bold disabled:opacity-30 hover:border-primary transition-colors">
                  +
                </button>
              </div>

              {/* 비용 요약 */}
              <div className="bg-elevated rounded-xl py-4 mb-6">
                <p className="text-muted text-xs mb-1">총 차감 금액</p>
                <p className="font-bold text-[24px]" style={{ color }}>
                  {totalCost.toLocaleString()} {useAP ? 'AP' : 'GP'}
                </p>
                {qty > 1 && (
                  <p className="text-muted text-[11px]">
                    {unitCost.toLocaleString()} × {qty}개
                  </p>
                )}
                <p className="text-muted text-[11px] mt-1">
                  잔여: {(useAP ? ap : gp).toLocaleString()} → {remaining.toLocaleString()} {useAP ? 'AP' : 'GP'}
                </p>
              </div>

              <div className="flex gap-3">
                <button onClick={() => setConfirmItem(null)} className="btn-cancel">취소</button>
                <button onClick={() => void handlePurchase(confirmItem, qty)}
                  disabled={isPurchasing || remaining < 0}
                  className="flex-1 h-11 rounded-xl text-surface font-bold text-sm disabled:opacity-50"
                  style={{ background: color }}>
                  {isPurchasing ? '처리중...' : `${qty}개 구매`}
                </button>
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );
}
