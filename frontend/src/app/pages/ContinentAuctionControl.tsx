import { useState } from 'react';

import { changeContinentAuction } from '../api/admin';
import { ApiError } from '../api/client';

interface Props {
  continentId: number;
  onDone: (message: string) => void;
}

export function ContinentAuctionControl({ continentId, onDone }: Props) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async (enabled: boolean, label: string) => {
    if (busy) return;
    if (!reason.trim()) { setError('사유를 입력하세요.'); return; }
    setBusy(true); setError(null);
    try {
      const r = await changeContinentAuction(continentId, enabled, reason.trim());
      onDone(`행성 경매 ${label} — ${r.affected}개`);
      setReason('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '처리에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mb-4 pb-4 border-b border-outline">
      <p className="font-bold text-[13px] mb-2">행성 경매 전체</p>
      <input value={reason} onChange={e => setReason(e.target.value)} placeholder="사유(필수)"
        className="w-full bg-elevated border border-outline rounded px-2 h-8 text-foreground text-xs outline-none focus:border-primary mb-2" />
      <div className="flex gap-2">
        <button disabled={busy || !reason.trim()} onClick={() => void run(false, '중지')}
          className="flex-1 h-8 rounded-lg border border-danger text-danger text-[11px] font-bold hover:bg-elevated disabled:opacity-40">전체 중지</button>
        <button disabled={busy || !reason.trim()} onClick={() => void run(true, '재개')}
          className="flex-1 h-8 rounded-lg border border-gp text-gp text-[11px] font-bold hover:bg-elevated disabled:opacity-40">전체 재개</button>
      </div>
      {error && <p className="text-danger text-[11px] mt-2">{error}</p>}
    </div>
  );
}
