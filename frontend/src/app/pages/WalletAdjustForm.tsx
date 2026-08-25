import { useState } from 'react';

import { adjustUserWallet } from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminUserDetail } from '../types/admin';

interface Props {
  userId: number;
  currentAp: number;
  currentGp: number;
  onAdjusted: (detail: AdminUserDetail) => void;
}

export function WalletAdjustForm({ userId, currentAp, currentGp, onAdjusted }: Props) {
  const [ap, setAp] = useState('');
  const [gp, setGp] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const apDelta = Number(ap) || 0;
  const gpDelta = Number(gp) || 0;
  const newAp = currentAp + apDelta;
  const newGp = currentGp + gpDelta;
  const wouldGoNegative = newAp < 0 || newGp < 0;
  const disabled =
    isSaving || (apDelta === 0 && gpDelta === 0) || !reason.trim() || wouldGoNegative;

  const handleSubmit = async () => {
    if (disabled) return;
    setIsSaving(true); setError(null);
    try {
      const detail = await adjustUserWallet(userId, apDelta, gpDelta, reason.trim());
      onAdjusted(detail);
      setAp(''); setGp(''); setReason('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '재화 조정에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div>
      <p className="text-muted text-[10px] mb-2 leading-relaxed">
        증가는 양수, 차감은 음수로 입력. 차감 시 잔액보다 크면 거부됩니다.
      </p>
      <div className="flex gap-2 mb-2">
        <label className="flex-1">
          <span className="block text-dim text-[11px] mb-1">AP 변화량</span>
          <input type="number" value={ap} onChange={e => setAp(e.target.value)} placeholder="0"
            className="w-full bg-elevated border border-outline rounded-md px-2 h-9 text-foreground text-xs outline-none focus:border-primary" />
        </label>
        <label className="flex-1">
          <span className="block text-dim text-[11px] mb-1">GP 변화량</span>
          <input type="number" value={gp} onChange={e => setGp(e.target.value)} placeholder="0"
            className="w-full bg-elevated border border-outline rounded-md px-2 h-9 text-foreground text-xs outline-none focus:border-primary" />
        </label>
      </div>
      {(apDelta !== 0 || gpDelta !== 0) && (
        <div className="bg-panel-deep rounded-md px-2 py-1.5 mb-2 text-[11px] space-y-0.5">
          {apDelta !== 0 && <DeltaRow label="AP" current={currentAp} next={newAp} delta={apDelta} />}
          {gpDelta !== 0 && <DeltaRow label="GP" current={currentGp} next={newGp} delta={gpDelta} />}
          {wouldGoNegative && <p className="text-danger">잔액이 음수가 되어 적용할 수 없습니다.</p>}
        </div>
      )}

      <input value={reason} onChange={e => setReason(e.target.value)} placeholder="사유 (필수)"
        className="w-full bg-elevated border border-outline rounded-md px-2 h-9 text-foreground text-xs outline-none focus:border-primary mb-2" />
      {error && <p className="text-danger text-[11px] mb-2">{error}</p>}
      <button onClick={() => void handleSubmit()} disabled={disabled}
        className="w-full h-9 rounded-lg bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">
        {isSaving ? '적용 중...' : '재화 조정 적용'}
      </button>
    </div>
  );
}

function DeltaRow({ label, current, next, delta }: { label: string; current: number; next: number; delta: number }) {
  const up = delta > 0;
  return (
    <p className="flex items-center gap-1.5">
      <span className="text-dim w-6">{label}</span>
      <span className="text-muted">{current.toLocaleString()}</span>
      <span className="text-dim">→</span>
      <span className="font-bold" style={{ color: next < 0 ? 'var(--color-danger)' : 'var(--color-foreground)' }}>
        {next.toLocaleString()}
      </span>
      <span className="font-semibold" style={{ color: up ? 'var(--color-gp)' : 'var(--color-flare)' }}>
        ({up ? '+' : ''}{delta.toLocaleString()})
      </span>
    </p>
  );
}
