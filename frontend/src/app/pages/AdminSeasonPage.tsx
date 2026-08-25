import { useEffect, useState } from 'react';

import { fetchSeasons, createSeason, endSeason, fetchSeasonPasses, updateSeasonPass, type AdminSeasonPass } from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminSeason } from '../types/admin';

const STATUS_STYLE: Record<AdminSeason['status'], string> = {
  SCHEDULED: 'text-primary',
  ACTIVE: 'text-gp',
  ENDED: 'text-flare',
  PROCESSED: 'text-muted',
};
const STATUS_LABEL: Record<AdminSeason['status'], string> = {
  SCHEDULED: '예정', ACTIVE: '진행중', ENDED: '종료(정산대기)', PROCESSED: '정산완료',
};

export function AdminSeasonPage() {
  const [seasons, setSeasons] = useState<AdminSeason[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => {
    fetchSeasons()
      .then(r => { setSeasons(r.seasons); setError(null); })
      .catch(e => { setError(e instanceof ApiError ? e.message : '시즌을 불러올 수 없습니다.'); console.warn('[AdminSeason] fetch', e); });
  };
  useEffect(load, []);

  const hasActive = seasons.some(s => s.status === 'ACTIVE' || s.status === 'SCHEDULED');

  const handleCreate = async () => {
    if (busy) return;
    if (!window.confirm('지금 새 시즌을 시작할까요?')) return;
    setBusy(true); setError(null); setMessage(null);
    try {
      const s = await createSeason();
      setMessage(`시즌 S${s.seasonNumber} 시작됨`);
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '시즌 시작에 실패했습니다.');
    } finally { setBusy(false); }
  };

  const handleEnd = async (s: AdminSeason) => {
    if (busy) return;
    if (!window.confirm(`시즌 S${s.seasonNumber}을(를) 종료할까요? 스케줄러가 정산합니다.`)) return;
    setBusy(true); setError(null); setMessage(null);
    try {
      await endSeason(s.seasonId);
      setMessage(`시즌 S${s.seasonNumber} 종료 처리됨`);
      load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '시즌 종료에 실패했습니다.');
    } finally { setBusy(false); }
  };

  return (
    <div className="h-full overflow-auto p-6">
      <div className="flex items-center gap-3 mb-4">
        <h2 className="font-bold text-base">시즌 운영</h2>
        <button onClick={() => void handleCreate()} disabled={busy || hasActive}
          title={hasActive ? '진행 중/예정 시즌이 있어 새로 시작할 수 없습니다.' : ''}
          className="ml-auto h-9 px-4 rounded-md bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">
          + 새 시즌 시작
        </button>
      </div>

      {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}
      {message && <p className="text-gp text-xs mb-3">✓ {message}</p>}

      <table className="w-full text-xs">
        <thead className="text-dim text-[11px] border-b border-outline">
          <tr>
            <th className="text-left font-medium py-2 px-2">시즌</th>
            <th className="text-left font-medium py-2 px-2">상태</th>
            <th className="text-left font-medium py-2 px-2">시작</th>
            <th className="text-left font-medium py-2 px-2">종료</th>
            <th className="text-right font-medium py-2 px-2"></th>
          </tr>
        </thead>
        <tbody>
          {seasons.map(s => (
            <tr key={s.seasonId} className="border-b border-outline-soft hover:bg-panel">
              <td className="py-2 px-2 font-bold">S{s.seasonNumber}</td>
              <td className={`py-2 px-2 font-bold ${STATUS_STYLE[s.status]}`}>{STATUS_LABEL[s.status]}</td>
              <td className="py-2 px-2 text-muted">{s.startedAt.slice(0, 16).replace('T', ' ')}</td>
              <td className="py-2 px-2 text-muted">{s.endedAt ? s.endedAt.slice(0, 16).replace('T', ' ') : '무기한'}</td>
              <td className="py-2 px-2 text-right">
                {(s.status === 'ACTIVE' || s.status === 'SCHEDULED') && (
                  <button onClick={() => void handleEnd(s)} disabled={busy}
                    className="text-danger font-bold hover:brightness-125 disabled:opacity-40">종료</button>
                )}
              </td>
            </tr>
          ))}
          {seasons.length === 0 && (
            <tr><td colSpan={5} className="py-8 text-center text-muted">시즌이 없습니다.</td></tr>
          )}
        </tbody>
      </table>

      <SeasonPassSettings onError={setError} onMessage={setMessage} />
    </div>
  );
}

const PASS_FIELDS: { key: keyof Omit<AdminSeasonPass, 'seasonPassId' | 'name'>; label: string; unit?: string }[] = [
  { key: 'costAp', label: '가격', unit: 'AP' },
  { key: 'durationDays', label: '기간', unit: '일' },
  { key: 'islandBonusPct', label: '섬 GP 보너스', unit: '%' },
  { key: 'extraBuilders', label: '건축 장인 추가', unit: '명' },
  { key: 'taxExemptBonus', label: '토지세 면제 추가', unit: '개' },
  { key: 'buildTimeReductionPct', label: '건설 시간 감소', unit: '%' },
];

const input = 'w-full bg-elevated border border-outline rounded px-2 h-8 text-foreground text-xs outline-none focus:border-primary';

function SeasonPassSettings({ onError, onMessage }: { onError: (m: string) => void; onMessage: (m: string) => void }) {
  const [passes, setPasses] = useState<AdminSeasonPass[]>([]);

  useEffect(() => {
    fetchSeasonPasses()
      .then(setPasses)
      .catch(e => { onError(e instanceof ApiError ? e.message : '시즌 패스를 불러올 수 없습니다.'); console.warn('[AdminSeason] passes', e); });
  }, [onError]);

  return (
    <div className="mt-8">
      <h3 className="font-bold text-sm mb-1">시즌 패스 설정</h3>
      <p className="text-muted text-[11px] mb-3">패스 이름은 식별자라 변경할 수 없습니다. 건설 시간 감소는 15레벨 보상과 합산되며 50%가 상한입니다.</p>
      {passes.map(p => (
        <SeasonPassRow key={p.seasonPassId} pass={p} onError={onError} onMessage={onMessage} />
      ))}
      {passes.length === 0 && <p className="text-muted text-xs">시즌 패스가 없습니다.</p>}
    </div>
  );
}

function SeasonPassRow({ pass, onError, onMessage }: { pass: AdminSeasonPass; onError: (m: string) => void; onMessage: (m: string) => void }) {
  const [form, setForm] = useState(pass);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const saved = await updateSeasonPass(pass.seasonPassId, {
        costAp: form.costAp, durationDays: form.durationDays, islandBonusPct: form.islandBonusPct,
        extraBuilders: form.extraBuilders, taxExemptBonus: form.taxExemptBonus,
        buildTimeReductionPct: form.buildTimeReductionPct,
      });
      setForm(saved);
      onMessage(`${pass.name} 저장됨`);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : '저장에 실패했습니다.');
    } finally { setBusy(false); }
  };

  return (
    <div className="card p-4 mb-3">
      <div className="flex items-center justify-between mb-3">
        <span className="text-gold font-bold text-[13px]">{pass.name}</span>
        <button onClick={() => void save()} disabled={busy}
          className="text-primary font-bold text-xs hover:brightness-125 disabled:opacity-30">저장</button>
      </div>
      <div className="flex flex-wrap gap-3">
        {PASS_FIELDS.map(f => (
          <label key={f.key} className="text-[11px] text-dim">
            {f.label}{f.unit ? ` (${f.unit})` : ''}
            <input type="number" min={0} value={form[f.key]}
              onChange={e => setForm(v => ({ ...v, [f.key]: Number(e.target.value) }))}
              className={`${input} w-[92px] mt-0.5`} />
          </label>
        ))}
      </div>
    </div>
  );
}
