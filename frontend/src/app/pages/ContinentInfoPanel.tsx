import type { ReactNode } from 'react';

import { useContinentRanking } from '../hooks/useContinentRanking';

import { ContinentAuctionList } from './ContinentAuctionList';

import type { Grade } from '../types/grade';
import { GRADE_COLOR } from '../types/grade';

const GRADE_EMOJI: Record<Grade, string> = { S: '👑', A: '💎', B: '🔷', C: '🔹' };

const MEDAL = ['🥇', '🥈', '🥉'];

interface PanelTerritory {
  coordX: number; coordY: number;
  status: 'mine' | 'occupied' | 'auction' | 'idle';
  grade: Grade; id: number;
}

interface Props {
  continentId: number;
  continentName: string;
  continentColor: string;
  continentTrophyReq: number | null;
  continentDesc: string;
  territories: PanelTerritory[];
  wishlistIds: Set<number>;
  onSelect: (id: number) => void;
}

function Section({ title, count, children }: { title: string; count?: number; children: ReactNode }) {
  return (
    <div className="px-4 py-3 border-b border-outline-soft">
      <p className="text-[11px] font-semibold text-foreground-soft mb-1.5">
        {title}
        {count != null && <span className="text-muted font-normal ml-1">{count}</span>}
      </p>
      {children}
    </div>
  );
}

function TerritoryRow({ t, label, labelColor, onSelect }: {
  t: PanelTerritory; label: string; labelColor: string; onSelect: (id: number) => void;
}) {
  return (
    <button
      onClick={() => onSelect(t.id)}
      className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-panel-deep transition-colors"
    >
      <span className="leading-none" style={{ fontSize: 11 }}>{GRADE_EMOJI[t.grade]}</span>
      <span className="text-[11px] font-semibold" style={{ color: GRADE_COLOR[t.grade] }}>{t.grade}</span>
      <span className="text-muted text-[10px]">({t.coordX}, {t.coordY})</span>
      <span className="ml-auto text-[10px]" style={{ color: labelColor }}>{label}</span>
    </button>
  );
}

export function ContinentInfoPanel({
  continentId, continentName, continentColor, continentTrophyReq, continentDesc,
  territories, wishlistIds, onSelect,
}: Props) {
  const wishlist = territories.filter(t => t.id !== 0 && wishlistIds.has(t.id));
  const { data: ranking, isLoading: isRankingLoading, error: rankingError } = useContinentRanking(continentId);

  return (
    <div className="flex-1 flex flex-col overflow-y-auto">
      <div className="px-4 py-3 border-b border-outline-soft">
        <p className="font-bold text-[13px]" style={{ color: continentColor }}>{continentName}</p>
        {continentDesc && <p className="text-muted text-[10px] mt-0.5">{continentDesc}</p>}
        <div className="flex items-center gap-1.5 mt-2">
          <span className="text-muted text-[10px]">입장 트로피</span>
          <span className="text-[10px] font-semibold" style={{ color: '#ffd700' }}>
            {continentTrophyReq == null ? '제한 없음' : `🏆 ${continentTrophyReq.toLocaleString()}`}
          </span>
        </div>
      </div>

      <ContinentAuctionList continentId={continentId} onSelect={onSelect} />

      <Section title="⭐ 내 관심 영토" count={wishlist.length}>
        {wishlist.length === 0 ? (
          <p className="text-muted text-[10px] px-2 py-1">관심 영토가 없습니다</p>
        ) : (
          wishlist.map(t => (
            <TerritoryRow
              key={t.id} t={t}
              label={t.status === 'auction' ? '경매중' : t.status === 'idle' ? '미점령' : '점령됨'}
              labelColor={t.status === 'auction' ? '#ffd700' : 'var(--color-muted)'}
              onSelect={onSelect}
            />
          ))
        )}
      </Section>

      <div className="px-4 py-3">
        <p className="text-[11px] font-semibold text-foreground-soft">🏆 대륙 트로피 랭킹</p>
        <p className="text-muted text-[9px] mb-1.5">이 대륙 입장 트로피 범위 내 순위</p>
        {rankingError ? (
          <p className="text-danger text-[10px] px-2 py-1">{rankingError}</p>
        ) : isRankingLoading ? (
          <p className="text-muted text-[10px] px-2 py-1">불러오는 중…</p>
        ) : !ranking || ranking.rankings.length === 0 ? (
          <p className="text-muted text-[10px] px-2 py-1">아직 점유 기록이 없습니다</p>
        ) : (
          ranking.rankings.map((r, i) => (
            <div key={r.userId} className="flex items-center gap-2 px-2 py-1.5">
              <span className="leading-none w-4 text-center" style={{ fontSize: 11 }}>{MEDAL[i] ?? r.rank}</span>
              <span className="text-[11px] text-foreground-soft truncate">{r.nickname}</span>
              <span className="ml-auto text-[10px] text-muted flex-shrink-0">🏆 {r.score.toLocaleString()}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
