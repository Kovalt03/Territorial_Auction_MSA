import { useState } from 'react';

import { EmptyState } from '../components/EmptyState';

import type { SeasonMission, MissionPeriod } from '../types/season';

const TABS: { id: MissionPeriod; label: string }[] = [
  { id: 'DAILY', label: '일일' },
  { id: 'WEEKLY', label: '주간' },
  { id: 'SEASON', label: '시즌' },
];

interface Props {
  missions: SeasonMission[];
  claimingId: number | null;
  onClaim: (missionId: number) => void;
}

export function SeasonMissionPanel({ missions, claimingId, onClaim }: Props) {
  const [tab, setTab] = useState<MissionPeriod>('DAILY');
  const filtered = missions.filter(m => m.missionType === tab);

  return (
    <div className="card overflow-hidden mb-5">
      <div className="bg-elevated px-4 py-2.5 border-b border-outline">
        <span className="text-foreground font-semibold text-[13px]">미션</span>
      </div>
      <div className="flex border-b border-outline">
        {TABS.map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`flex-1 py-2.5 text-[13px] font-semibold relative ${tab === t.id ? 'text-primary' : 'text-muted'}`}
          >
            {t.label}
            {tab === t.id && <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />}
          </button>
        ))}
      </div>
      <div className="p-4 space-y-2">
        {filtered.length === 0 ? (
          <EmptyState message="해당 미션이 없습니다" />
        ) : (
          filtered.map(m => {
            const pct = Math.min(100, Math.round((m.completedCount / m.goalCount) * 100));
            return (
              <div key={m.missionId} className="bg-panel-deep border border-outline rounded-xl p-3">
                <div className="flex items-center justify-between mb-1">
                  <p className="text-foreground font-semibold text-[13px]">{m.title}</p>
                  <span className="text-gold text-[11px] font-bold">+{m.xpReward} XP</span>
                </div>
                <p className="text-muted text-[11px] mb-2">{m.description}</p>
                <div className="flex items-center gap-2">
                  <div className="flex-1 h-1.5 bg-elevated rounded-full overflow-hidden">
                    <div className="h-full bg-primary rounded-full" style={{ width: `${pct}%` }} />
                  </div>
                  <span className="text-muted text-[10px] tabular-nums">
                    {m.completedCount}/{m.goalCount}
                  </span>
                  <button
                    disabled={!m.canClaim || claimingId === m.missionId}
                    onClick={() => onClaim(m.missionId)}
                    className={`h-7 px-3 rounded-lg text-[11px] font-semibold transition-colors flex-shrink-0 disabled:cursor-not-allowed ${m.isClaimed ? 'bg-elevated text-muted' : m.canClaim ? 'bg-primary text-surface' : 'bg-elevated text-outline'}`}
                  >
                    {m.isClaimed ? '완료' : claimingId === m.missionId ? '...' : '수령'}
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
