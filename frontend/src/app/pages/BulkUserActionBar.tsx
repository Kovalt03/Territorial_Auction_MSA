import { useState } from 'react';

import { bulkAdjustWallet, bulkChangeUserStatus, bulkSendNotification } from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminBulkResult } from '../types/admin';

interface Props {
  userIds: number[];
  onDone: (message: string) => void;
  onClear: () => void;
}

export function BulkUserActionBar({ userIds, onDone, onClear }: Props) {
  const [ap, setAp] = useState('');
  const [gp, setGp] = useState('');
  const [reason, setReason] = useState('');
  const [notice, setNotice] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const apDelta = Number(ap) || 0;
  const gpDelta = Number(gp) || 0;

  const run = async (fn: () => Promise<AdminBulkResult>, label: string) => {
    if (busy) return;
    if (!reason.trim()) { setError('사유를 입력하세요.'); return; }
    setBusy(true); setError(null);
    try {
      const r = await fn();
      onDone(`${label} 완료 — ${r.affected}명`);
      setAp(''); setGp(''); setReason('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '처리에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  };

  const sendNotice = async () => {
    if (busy) return;
    if (!notice.trim()) { setError('메시지를 입력하세요.'); return; }
    setBusy(true); setError(null);
    try {
      const r = await bulkSendNotification(userIds, notice.trim());
      onDone(`알림 발송 완료 — ${r.affected}명`);
      setNotice('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '처리에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  };

  const input = 'bg-elevated border border-outline rounded-md px-2 h-8 text-foreground text-[11px] outline-none focus:border-primary';

  return (
    <div className="sticky bottom-0 bg-panel border-t border-outline px-4 py-3 flex flex-wrap items-center gap-2 text-[11px]">
      <span className="font-bold text-primary">선택 {userIds.length}명</span>
      <button onClick={onClear} className="text-muted hover:text-foreground-soft underline">선택 해제</button>
      <span className="w-px h-5 bg-outline mx-1" />

      <input value={reason} onChange={e => setReason(e.target.value)} placeholder="사유(필수)" className={`${input} w-40`} />
      <input type="number" value={ap} onChange={e => setAp(e.target.value)} placeholder="AP" className={`${input} w-20`} />
      <input type="number" value={gp} onChange={e => setGp(e.target.value)} placeholder="GP" className={`${input} w-20`} />
      <button disabled={busy || (apDelta === 0 && gpDelta === 0) || !reason.trim()}
        onClick={() => void run(() => bulkAdjustWallet(userIds, apDelta, gpDelta, reason.trim()), '재화 조정')}
        className="h-8 px-3 rounded-md bg-primary text-surface font-bold hover:brightness-110 disabled:opacity-40">
        재화 적용
      </button>

      <span className="w-px h-5 bg-outline mx-1" />
      <button disabled={busy || !reason.trim()}
        onClick={() => void run(() => bulkChangeUserStatus(userIds, 'SUSPENDED', reason.trim()), '정지')}
        className="h-8 px-3 rounded-md border border-danger text-danger font-bold hover:bg-elevated disabled:opacity-40">
        정지
      </button>
      <button disabled={busy || !reason.trim()}
        onClick={() => void run(() => bulkChangeUserStatus(userIds, 'ACTIVE', reason.trim()), '활성화')}
        className="h-8 px-3 rounded-md border border-gp text-gp font-bold hover:bg-elevated disabled:opacity-40">
        활성화
      </button>

      <span className="w-px h-5 bg-outline mx-1" />
      <input value={notice} onChange={e => setNotice(e.target.value)} placeholder="알림 메시지" className={`${input} w-44`} />
      <button disabled={busy || !notice.trim()} onClick={() => void sendNotice()}
        className="h-8 px-3 rounded-md border border-primary text-primary font-bold hover:bg-elevated disabled:opacity-40">
        알림 발송
      </button>

      {error && <span className="text-danger">{error}</span>}
    </div>
  );
}
