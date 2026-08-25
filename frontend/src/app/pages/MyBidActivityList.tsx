import { useState } from 'react';
import { useNavigate } from 'react-router';

import { EmptyState } from '../components/EmptyState';
import { LoadingState } from '../components/LoadingState';

import { GRADE_COLOR } from '../types/grade';
import type { MyBidEntry } from '../types/auction';

type BidSort = 'time' | 'ap' | 'outbid';

const URGENT_MS = 5 * 60 * 1000;

const SORT_OPTIONS: { val: BidSort; label: string }[] = [
  { val: 'time', label: '⏱ 시간순' },
  { val: 'ap', label: '💰 AP순' },
  { val: 'outbid', label: '🔺 상회입찰' },
];

function fmtTimeLeft(endAt: string, now: number): string {
  const diff = new Date(endAt).getTime() - now;
  if (diff <= 0) return '종료';
  const h = Math.floor(diff / 3600000);
  const m = Math.floor((diff % 3600000) / 60000);
  const s = Math.floor((diff % 60000) / 1000);
  if (h > 0) return `${h}시간 ${String(m).padStart(2, '0')}분`;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

interface Props {
  bids: MyBidEntry[];
  isLoading: boolean;
  now: number;
}

export function MyBidActivityList({ bids, isLoading, now }: Props) {
  const navigate = useNavigate();
  const [bidSort, setBidSort] = useState<BidSort>('time');

  const sorted = [...bids].sort((a, b) => {
    if (bidSort === 'ap') return b.currentPrice - a.currentPrice;
    if (bidSort === 'outbid') {
      if (a.isHighestBidder !== b.isHighestBidder) return a.isHighestBidder ? 1 : -1;
      return 0;
    }
    return new Date(a.endAt).getTime() - new Date(b.endAt).getTime();
  });

  if (isLoading) return <LoadingState />;
  if (bids.length === 0) return <EmptyState message="데이터가 없습니다" />;

  return (
    <>
      <div className="flex items-center gap-1.5 mb-3">
        <span className="text-muted text-[11px] mr-1">정렬</span>
        {SORT_OPTIONS.map(s => (
          <button
            key={s.val}
            onClick={() => setBidSort(s.val)}
            className={`px-2.5 h-7 rounded-lg text-[11px] font-semibold transition-colors border ${bidSort === s.val ? 'bg-primary text-surface border-primary' : 'bg-outline-soft text-muted border-outline'}`}
          >
            {s.label}
          </button>
        ))}
      </div>
      <div className="space-y-2">
        {sorted.map(b => {
          const color = GRADE_COLOR[b.grade as keyof typeof GRADE_COLOR] ?? '#8892b0';
          return (
            <button
              key={b.auctionId}
              onClick={() => navigate(`/app/territory/${b.territoryId}`)}
              className="w-full flex items-center gap-3 p-3 rounded-xl border border-outline hover:bg-panel-deep transition-colors text-left"
            >
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold text-[11px] flex-shrink-0 border ${b.isHighestBidder ? 'bg-gp/20 border-gp/60 text-gp' : 'bg-danger/20 border-danger/60 text-danger'}`}>
                {b.isHighestBidder ? '↑' : '↓'}
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-1.5 mb-0.5">
                  <p className="text-foreground font-semibold text-[13px]">({b.coordX}, {b.coordY})</p>
                  <span className="px-1.5 py-0.5 rounded font-bold text-[9px]" style={{ color, background: color + '20' }}>
                    {b.grade}급
                  </span>
                </div>
                <p className="text-muted text-[11px]">{b.continentName} · {b.isHighestBidder ? '최고가 유지' : '상회 입찰됨'}</p>
                <p className="text-muted text-[10px]">내 입찰 {b.myBidAmount.toLocaleString()} AP</p>
              </div>
              <div className="text-right">
                <p className="text-gold font-bold text-[13px]">{b.currentPrice.toLocaleString()} AP</p>
                <p className="text-muted text-[10px]">현재가</p>
                {b.status === 'BIDDING' && (
                  <p className={`font-semibold text-[10px] mt-0.5 tabular-nums ${new Date(b.endAt).getTime() - now < URGENT_MS ? 'text-flare' : 'text-dim'}`}>
                    {fmtTimeLeft(b.endAt, now)}
                  </p>
                )}
              </div>
            </button>
          );
        })}
      </div>
    </>
  );
}
