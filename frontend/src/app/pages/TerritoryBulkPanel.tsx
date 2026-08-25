import { useState } from 'react';

import {
  bulkChangeTerritoryGrade,
  bulkChangeTerritoryAuction,
  bulkForceStartTerritories,
} from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminBulkResult } from '../types/admin';

const GRADES = ['S', 'A', 'B', 'C', 'D'];

interface Props {
  territoryIds: number[];
  onDone: (message: string) => void;
  onClear: () => void;
}

export function TerritoryBulkPanel({ territoryIds, onDone, onClear }: Props) {
  const [grade, setGrade] = useState('S');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async (fn: () => Promise<AdminBulkResult>, label: string, needReason = true) => {
    if (busy) return;
    if (needReason && !reason.trim()) { setError('사유를 입력하세요.'); return; }
    setBusy(true); setError(null);
    try {
      const r = await fn();
      onDone(`${label} — ${r.affected}개`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '처리에 실패했습니다.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <p className="font-bold text-sm mb-1">선택 {territoryIds.length}개</p>
      <button onClick={onClear} className="text-muted text-[11px] underline hover:text-foreground-soft mb-3">선택 해제</button>

      <input value={reason} onChange={e => setReason(e.target.value)} placeholder="사유(필수)"
        className="w-full bg-elevated border border-outline rounded-md px-2 h-9 text-foreground text-xs outline-none focus:border-primary mb-3" />

      <label className="block text-dim mb-1.5 text-[11px] font-medium">등급 일괄 변경</label>
      <div className="flex gap-1 mb-2">
        {GRADES.map(g => (
          <button key={g} onClick={() => setGrade(g)}
            className={`flex-1 h-8 rounded-lg border text-xs font-bold ${grade === g ? 'border-primary text-primary' : 'border-outline text-muted'}`}>
            {g}
          </button>
        ))}
      </div>
      <button disabled={busy || !reason.trim()}
        onClick={() => void run(() => bulkChangeTerritoryGrade(territoryIds, grade, reason.trim()), `${grade}급 일괄 변경`)}
        className="w-full h-9 rounded-lg bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40 mb-4">
        등급 적용
      </button>

      <div className="border-t border-outline pt-3">
        <label className="block text-dim mb-1.5 text-[11px] font-medium">경매</label>
        <div className="flex gap-2 mb-2">
          <button disabled={busy || !reason.trim()}
            onClick={() => void run(() => bulkChangeTerritoryAuction(territoryIds, false, reason.trim()), '경매 일괄 중지')}
            className="flex-1 h-9 rounded-lg border border-danger text-danger text-xs font-bold hover:bg-elevated disabled:opacity-40">경매 중지</button>
          <button disabled={busy || !reason.trim()}
            onClick={() => void run(() => bulkChangeTerritoryAuction(territoryIds, true, reason.trim()), '경매 일괄 재개')}
            className="flex-1 h-9 rounded-lg border border-gp text-gp text-xs font-bold hover:bg-elevated disabled:opacity-40">경매 재개</button>
        </div>
        <button disabled={busy}
          onClick={() => void run(() => bulkForceStartTerritories(territoryIds, reason.trim()), '강제 시작', false)}
          className="w-full h-9 rounded-lg bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">
          경매 강제 시작 (IDLE만)
        </button>
      </div>

      {error && <p className="text-danger text-[11px] mt-3">{error}</p>}
    </div>
  );
}
