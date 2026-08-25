import { useEffect, useState } from 'react';

import { fetchItems, updateItem, grantItem } from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminItem } from '../types/admin';

export function AdminItemPage() {
  const [items, setItems] = useState<AdminItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchItems()
      .then(r => { setItems(r.items); setError(null); })
      .catch(e => { setError(e instanceof ApiError ? e.message : '아이템을 불러올 수 없습니다.'); console.warn('[AdminItem] fetch', e); });
  };
  useEffect(load, []);

  return (
    <div className="h-full overflow-auto p-6">
      <h2 className="font-bold text-base mb-4">아이템 관리</h2>
      {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}
      {message && <p className="text-gp text-xs mb-3">✓ {message}</p>}

      <section className="mb-6">
        <h3 className="font-bold text-sm mb-2">가격 · 한도</h3>
        <table className="w-full text-xs">
          <thead className="text-dim text-[11px] border-b border-outline">
            <tr>
              <th className="text-left font-medium py-2 px-2">아이템</th>
              <th className="text-left font-medium py-2 px-2 w-24">타입</th>
              <th className="text-left font-medium py-2 px-2 w-28">AP 가격</th>
              <th className="text-left font-medium py-2 px-2 w-28">GP 가격</th>
              <th className="text-left font-medium py-2 px-2 w-28">일일 한도</th>
              <th className="text-right font-medium py-2 px-2 w-16"></th>
            </tr>
          </thead>
          <tbody>
            {items.map(item => (
              <ItemRow key={item.itemId} item={item} onSaved={(msg) => { setMessage(msg); load(); }} onError={setError} />
            ))}
            {items.length === 0 && <tr><td colSpan={6} className="py-8 text-center text-muted">아이템이 없습니다.</td></tr>}
          </tbody>
        </table>
      </section>

      <section className="bg-panel border border-outline rounded-xl p-4 max-w-lg">
        <h3 className="font-bold text-sm mb-3">아이템 지급 (CS 보상)</h3>
        <GrantForm items={items} onGranted={(msg) => setMessage(msg)} onError={setError} />
      </section>
    </div>
  );
}

interface ItemRowProps {
  item: AdminItem;
  onSaved: (msg: string) => void;
  onError: (msg: string) => void;
}

function ItemRow({ item, onSaved, onError }: ItemRowProps) {
  const [ap, setAp] = useState(item.costAp?.toString() ?? '');
  const [gp, setGp] = useState(item.costGp?.toString() ?? '');
  const [limit, setLimit] = useState(item.dailyLimit?.toString() ?? '');
  const [busy, setBusy] = useState(false);

  const toNum = (s: string) => (s.trim() === '' ? null : Number(s));
  const dirty =
    toNum(ap) !== (item.costAp ?? null) ||
    toNum(gp) !== (item.costGp ?? null) ||
    toNum(limit) !== (item.dailyLimit ?? null);

  const handleSave = async () => {
    if (busy || !dirty) return;
    setBusy(true);
    try {
      await updateItem(item.itemId, toNum(ap), toNum(gp), toNum(limit));
      onSaved(`${item.name} 정책 저장됨`);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : '저장에 실패했습니다.');
    } finally { setBusy(false); }
  };

  const input = 'w-20 bg-elevated border border-outline rounded px-2 h-7 text-foreground text-[11px] outline-none focus:border-primary';

  return (
    <tr className="border-b border-outline-soft">
      <td className="py-2 px-2 font-semibold">{item.name}</td>
      <td className="py-2 px-2 text-muted">{item.itemType}</td>
      <td className="py-2 px-2"><input type="number" value={ap} onChange={e => setAp(e.target.value)} placeholder="없음" className={input} /></td>
      <td className="py-2 px-2"><input type="number" value={gp} onChange={e => setGp(e.target.value)} placeholder="없음" className={input} /></td>
      <td className="py-2 px-2"><input type="number" value={limit} onChange={e => setLimit(e.target.value)} placeholder="무제한" className={input} /></td>
      <td className="py-2 px-2 text-right">
        <button onClick={() => void handleSave()} disabled={busy || !dirty}
          className="text-primary font-bold hover:brightness-125 disabled:opacity-30">저장</button>
      </td>
    </tr>
  );
}

interface GrantProps {
  items: AdminItem[];
  onGranted: (msg: string) => void;
  onError: (msg: string) => void;
}

function GrantForm({ items, onGranted, onError }: GrantProps) {
  const [userId, setUserId] = useState('');
  const [itemId, setItemId] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const qty = Number(quantity) || 0;
  const disabled = busy || !userId || !itemId || qty < 1 || !reason.trim();

  const handleGrant = async () => {
    if (disabled) return;
    setBusy(true);
    try {
      await grantItem(Number(userId), Number(itemId), qty, reason.trim());
      onGranted(`유저 #${userId}에게 아이템 ${qty}개 지급됨`);
      setUserId(''); setQuantity('1'); setReason('');
    } catch (e) {
      onError(e instanceof ApiError ? e.message : '지급에 실패했습니다.');
    } finally { setBusy(false); }
  };

  const input = 'bg-elevated border border-outline rounded-md px-2 h-9 text-foreground text-xs outline-none focus:border-primary';

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input value={userId} onChange={e => setUserId(e.target.value)} placeholder="유저 ID" type="number" className={`${input} w-28`} />
        <select value={itemId} onChange={e => setItemId(e.target.value)} className={`${input} flex-1`}>
          <option value="">아이템 선택</option>
          {items.map(i => <option key={i.itemId} value={i.itemId}>{i.name}</option>)}
        </select>
        <input value={quantity} onChange={e => setQuantity(e.target.value)} placeholder="수량" type="number" className={`${input} w-20`} />
      </div>
      <input value={reason} onChange={e => setReason(e.target.value)} placeholder="사유 (필수)" className={`${input} w-full`} />
      <button onClick={() => void handleGrant()} disabled={disabled}
        className="w-full h-9 rounded-lg bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">
        {busy ? '지급 중...' : '아이템 지급'}
      </button>
    </div>
  );
}
