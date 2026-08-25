import { useEffect, useState } from 'react';

import { applyGradeDistribution } from '../api/admin';
import { ApiError } from '../api/client';

const GRADES = ['S', 'A', 'B', 'C', 'D'];

interface Props {
  continentId: number;
  total: number;
  initial: Record<string, number>;
  onApplied: () => void;
}

export function GradeDistributionEditor({ continentId, total, initial, onApplied }: Props) {
  const [dist, setDist] = useState<Record<string, number>>({});
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    const base: Record<string, number> = {};
    GRADES.forEach(g => { base[g] = initial[g] ?? 0; });
    setDist(base);
    setReason('');
    setError(null);
  }, [continentId]); // eslint-disable-line react-hooks/exhaustive-deps

  const sum = GRADES.reduce((acc, g) => acc + (dist[g] ?? 0), 0);

  const handleApply = async () => {
    if (isSaving || sum !== total) return;
    setIsSaving(true); setError(null);
    try {
      await applyGradeDistribution(continentId, dist, reason);
      onApplied();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '일괄 조정에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="mb-4 pb-4 border-b border-outline">
      <p className="font-bold text-[13px] mb-2">등급 분포 일괄 조정</p>
      {GRADES.map(g => (
        <div key={g} className="flex items-center gap-2 mb-1.5">
          <span className="w-4 text-xs font-bold">{g}</span>
          <input type="number" min={0} value={dist[g] ?? 0}
            onChange={e => setDist(d => ({ ...d, [g]: Math.max(0, Number(e.target.value) || 0) }))}
            className="flex-1 bg-elevated border border-outline rounded px-2 h-8 text-foreground text-xs outline-none focus:border-primary" />
        </div>
      ))}
      <p className={`text-[11px] mb-2 ${sum === total ? 'text-muted' : 'text-danger'}`}>합계 {sum} / {total}</p>
      <input value={reason} onChange={e => setReason(e.target.value)} placeholder="사유"
        className="w-full bg-elevated border border-outline rounded px-2 h-8 text-foreground text-xs outline-none focus:border-primary mb-2" />
      {error && <p className="text-danger text-[11px] mb-2">{error}</p>}
      <button onClick={() => void handleApply()} disabled={isSaving || sum !== total}
        className="w-full h-9 rounded-lg bg-primary text-surface font-bold text-xs hover:brightness-110 disabled:opacity-40">
        {isSaving ? '적용 중...' : '분포 적용'}
      </button>
    </div>
  );
}
