import { useState } from 'react';

import { useTerritoryHoldRanking, useAuctionSpendRanking, useTrophyRanking } from '../hooks/useRanking';
import { GNB } from '../components/GNB';
import type { TerritoryHoldRankEntry, AuctionSpendRankEntry, TrophyRankEntry } from '../types/ranking';

type Category = 'territory' | 'assets' | 'trophy';

const categoryLabel: Record<Category, { label: string; icon: string }> = {
  territory: { label: '영토 왕', icon: '🏰' },
  assets: { label: '경매 지출왕', icon: '💸' },
  trophy: { label: '트로피 랭킹', icon: '🏆' },
};

const RANK_COLORS = ['#ffd700', '#8892b0', '#ff8c00'];

interface NormalizedEntry {
  rank: number;
  nickname: string;
  valueLabel: string;
}

function normalizeTerritoryHold(entries: TerritoryHoldRankEntry[]): NormalizedEntry[] {
  return entries.map(e => ({
    rank: e.rank,
    nickname: e.nickname,
    valueLabel: `${e.score.toLocaleString()} 점`,
  }));
}

function normalizeAuctionSpend(entries: AuctionSpendRankEntry[]): NormalizedEntry[] {
  return entries.map(e => ({
    rank: e.rank,
    nickname: e.nickname,
    valueLabel: `${e.totalSpentAP.toLocaleString()} AP`,
  }));
}

function normalizeTrophy(entries: TrophyRankEntry[]): NormalizedEntry[] {
  return entries.map(e => ({
    rank: e.rank,
    nickname: e.nickname,
    valueLabel: `${e.score.toLocaleString()} 점`,
  }));
}

function initial(nickname: string) {
  return nickname.charAt(0) || '?';
}

function rankColor(rank: number): string {
  return RANK_COLORS[rank - 1] ?? '#7788a5';
}

function PodiumCard({ entry, height, medal }: { entry: NormalizedEntry; height: number; medal: string }) {
  const color = rankColor(entry.rank);
  return (
    <div className="flex flex-col items-center">
      <div className="w-14 h-14 rounded-full flex items-center justify-center font-bold text-2xl mb-2"
        style={{ background: color + '4d', border: `2px solid ${color}`, color }}>
        {initial(entry.nickname)}
      </div>
      <span className="text-[30px]">{medal}</span>
      <div className="w-40 rounded-xl flex flex-col items-center py-4 mb-2"
        style={{ height, background: color + '18', border: `${entry.rank === 1 ? 2 : 1}px solid ${color}` }}>
        <p className="font-bold text-sm" style={{ color }}>{entry.rank}위</p>
      </div>
      <p className="font-bold text-[13px]" style={{ color }}>{entry.nickname}</p>
      <p className="text-foreground font-semibold text-lg">{entry.valueLabel}</p>
    </div>
  );
}

function LoadingRows() {
  return (
    <>
      {Array.from({ length: 6 }, (_, i) => (
        <div key={i} className="grid px-4 py-3 border-b border-outline items-center animate-pulse"
          style={{ gridTemplateColumns: '80px 1fr 1fr 1fr 1fr' }}>
          <div className="h-4 bg-elevated rounded w-12" />
          <div className="h-4 bg-elevated rounded w-24" />
          <div className="h-4 bg-elevated rounded w-16" />
          <div className="h-4 bg-elevated rounded w-20" />
          <div className="h-4 bg-elevated rounded w-16" />
        </div>
      ))}
    </>
  );
}

export function RankingPage() {
  const [category, setCategory] = useState<Category>('territory');

  const { data: holdData, isLoading: holdLoading } = useTerritoryHoldRanking();
  const { data: spendData, isLoading: spendLoading } = useAuctionSpendRanking();
  const { data: trophyData, isLoading: trophyLoading } = useTrophyRanking();

  const isLoading =
    category === 'territory' ? holdLoading
    : category === 'assets' ? spendLoading
    : trophyLoading;

  const entries: NormalizedEntry[] = (() => {
    if (category === 'territory' && holdData) return normalizeTerritoryHold(holdData.rankings);
    if (category === 'assets' && spendData) return normalizeAuctionSpend(spendData.rankings);
    if (category === 'trophy' && trophyData) return normalizeTrophy(trophyData.rankings);
    return [];
  })();

  const top3 = entries.slice(0, 3);
  const rest = entries.slice(3);

  const podiumOrder = top3.length >= 2
    ? [top3[1], top3[0], top3[2]].filter(Boolean)
    : top3;
  const podiumHeights = [130, 160, 110];
  const podiumMedals = ['🥈', '🥇', '🥉'];

  return (
    <div className="page-root">
      <GNB />

      <div className="page-body">
        <h1 className="text-foreground font-bold mb-4 text-[26px]">🏆  랭킹 리더보드</h1>

        <div className="bg-panel border border-outline flex mb-5">
          {(Object.keys(categoryLabel) as Category[]).map(c => (
            <button key={c} onClick={() => setCategory(c)}
              className={`flex-1 py-3 font-semibold transition-colors relative text-[13px] ${category === c ? 'text-primary bg-elevated' : 'text-muted hover:text-foreground'}`}>
              {categoryLabel[c].icon} {categoryLabel[c].label}
              {category === c && <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />}
            </button>
          ))}
        </div>

        <>
            {isLoading ? (
              <div className="flex items-end justify-center gap-4 mb-6">
                {[130, 160, 110].map((h, i) => (
                  <div key={i} className="flex flex-col items-center gap-2 animate-pulse">
                    <div className="w-14 h-14 rounded-full bg-elevated" />
                    <div className="w-40 rounded-xl bg-elevated" style={{ height: h }} />
                  </div>
                ))}
              </div>
            ) : top3.length === 0 ? (
              <div className="flex items-center justify-center h-48 card mb-6">
                <p className="text-muted text-sm">랭킹 데이터가 없습니다</p>
              </div>
            ) : (
              <div className="flex items-end justify-center gap-4 mb-6 relative">
                {podiumOrder.map((entry, i) => (
                  <PodiumCard
                    key={entry.rank}
                    entry={entry}
                    height={podiumHeights[i]}
                    medal={podiumMedals[i]}
                  />
                ))}
              </div>
            )}

            <div className="card overflow-hidden">
              <div className="bg-elevated px-4 py-2.5 border-b-2 border-primary flex items-center justify-between">
                <span className="text-foreground font-semibold text-[13px]">4위 이하 순위</span>
              </div>
              <div className="grid text-muted px-4 py-2.5 border-b border-outline text-[11px]"
                style={{ gridTemplateColumns: '80px 1fr 1fr' }}>
                <span>순위</span>
                <span>플레이어</span>
                <span>{category === 'territory' ? '영토 점수' : category === 'trophy' ? '트로피 점수' : '총 지출'}</span>
              </div>
              {isLoading ? (
                <LoadingRows />
              ) : rest.length === 0 ? (
                <div className="px-4 py-6 text-center text-muted text-xs">데이터가 없습니다</div>
              ) : (
                rest.map((r, i) => (
                  <div key={r.rank}
                    className={`grid px-4 py-3 border-b border-outline items-center hover:bg-panel-deep transition-colors ${i % 2 === 0 ? 'bg-panel-deep bg-opacity-30' : ''}`}
                    style={{ gridTemplateColumns: '80px 1fr 1fr' }}>
                    <span className="text-foreground font-bold text-sm">{r.rank}위</span>
                    <div className="flex items-center gap-2">
                      <div className="w-8 h-8 rounded-full flex items-center justify-center font-bold text-white text-sm bg-[#8892b04d]">
                        {initial(r.nickname)}
                      </div>
                      <span className="text-foreground font-semibold text-[13px]">{r.nickname}</span>
                    </div>
                    <span className="text-gold font-medium text-[13px]">{r.valueLabel}</span>
                  </div>
                ))
              )}
            </div>

            {(() => {
              const myData = category === 'territory' ? holdData : category === 'trophy' ? trophyData : spendData;
              const myRank = myData?.myRank;
              const myScore = myData?.myScore;
              if (!myRank) return null;
              const myValueLabel = category === 'territory'
                ? `${(myScore ?? 0).toLocaleString()} 점`
                : category === 'trophy'
                ? `${(myScore ?? 0).toLocaleString()} 점`
                : `${(myScore ?? 0).toLocaleString()} AP`;
              return (
                <div className="mt-3 card px-4 py-3 flex items-center justify-between border-primary">
                  <div className="flex items-center gap-3">
                    <span className="text-primary font-bold text-sm">내 순위</span>
                    <span className="text-foreground font-bold text-lg">{myRank}위</span>
                  </div>
                  <span className="text-gold font-semibold text-sm">{myValueLabel}</span>
                </div>
              );
            })()}
        </>
      </div>
    </div>
  );
}
