import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { useApp } from '../context/AppContext';
import { fetchSiegeEvents } from '../api/siege';
import { EmptyState } from '../components/EmptyState';
import { LoadingState } from '../components/LoadingState';

import { GRADE_COLOR } from '../types/grade';
import type { MyTerritory } from '../types/vault';

interface Props {
  territories: MyTerritory[];
  isLoading: boolean;
}

const HOUR_MS = 3_600_000;
const SOON_THRESHOLD_MS = 24 * HOUR_MS;

function formatDuration(ms: number): string {
  if (ms <= 0) return '만료됨';
  const totalHours = Math.floor(ms / HOUR_MS);
  const days = Math.floor(totalHours / 24);
  const hours = totalHours % 24;
  if (days > 0) return `${days}일 ${hours}시간`;
  if (hours > 0) return `${hours}시간`;
  return `${Math.floor(ms / 60_000)}분`;
}

export function MyTerritoryList({ territories, isLoading }: Props) {
  const navigate = useNavigate();
  const { userId } = useApp();

  // 진행 중 공성(내가 방어자) 영토 id — '공성 중' 배지 표시용.
  const [siegeIds, setSiegeIds] = useState<Set<number>>(new Set());
  useEffect(() => {
    fetchSiegeEvents('PENDING')
      .then(r => setSiegeIds(new Set(r.sieges.filter(s => s.defender.userId === userId).map(s => s.targetTerritory.id))))
      .catch(e => console.warn('[MyTerritoryList] siege lookup failed', e));
  }, [userId]);

  if (isLoading) return <LoadingState />;
  if (territories.length === 0) return <EmptyState message="보유한 영토가 없습니다" />;

  const now = Date.now();

  return (
    <div className="space-y-2">
      {territories.map(t => {
        const color = GRADE_COLOR[t.grade as keyof typeof GRADE_COLOR] ?? '#8892b0';
        const heldMs = t.occupiedAt ? now - new Date(t.occupiedAt).getTime() : null;
        const remainMs = t.occupiedUntil ? new Date(t.occupiedUntil).getTime() - now : null;
        const isExpiringSoon = remainMs !== null && remainMs > 0 && remainMs < SOON_THRESHOLD_MS;
        const isUnderSiege = siegeIds.has(t.territoryId);
        return (
          <button
            key={t.territoryId}
            onClick={() => navigate(`/app/territory/${t.territoryId}`)}
            className={`w-full flex items-center gap-3 p-3 rounded-xl border transition-colors text-left ${isUnderSiege ? 'border-danger/60 bg-danger/5 hover:bg-danger/10' : 'border-outline hover:bg-panel-deep'}`}
          >
            <div
              className="w-10 h-10 rounded-xl flex items-center justify-center font-bold text-sm flex-shrink-0"
              style={{ background: color + '30', border: `1px solid ${color}60`, color }}
            >
              {t.grade}
            </div>
            <div className="flex-1">
              <p className="text-foreground font-semibold text-[13px] flex items-center gap-1.5">
                영토 #{t.territoryId}
                {isUnderSiege && (
                  <span className="px-1.5 py-0.5 rounded text-[9px] font-bold animate-pulse" style={{ color: '#ff3333', background: '#ff222215', border: '1px solid #ff444440' }}>
                    🔴 공성 중
                  </span>
                )}
              </p>
              <p className="text-muted text-[11px]">({t.position.x}, {t.position.y}) · {t.continentName}</p>
              {(heldMs !== null || remainMs !== null) && (
                <p className="text-[11px] mt-0.5">
                  {heldMs !== null && (
                    <span className="text-foreground/80">보유 {formatDuration(heldMs)}</span>
                  )}
                  {heldMs !== null && remainMs !== null && <span className="text-muted"> · </span>}
                  {remainMs !== null && (
                    <span style={{ color: isExpiringSoon ? '#ff8c00' : '#7788a5' }}>
                      만료까지 {formatDuration(remainMs)}
                    </span>
                  )}
                </p>
              )}
            </div>
            <span className="text-muted text-[11px]">→</span>
          </button>
        );
      })}
    </div>
  );
}
