import { useNavigate } from 'react-router';

import { EmptyState } from '../components/EmptyState';
import { LoadingState } from '../components/LoadingState';

import { GRADE_COLOR } from '../types/grade';
import type { MyBidEntry } from '../types/auction';

interface Props {
  bids: MyBidEntry[];
  isLoading: boolean;
}

export function MyTradeHistoryList({ bids, isLoading }: Props) {
  const navigate = useNavigate();

  const sorted = [...bids].sort((a, b) => new Date(b.endAt).getTime() - new Date(a.endAt).getTime());

  if (isLoading) return <LoadingState />;
  if (bids.length === 0) return <EmptyState message="거래 내역이 없습니다" />;

  return (
    <div className="space-y-2">
      {sorted.map(b => {
        const color = GRADE_COLOR[b.grade as keyof typeof GRADE_COLOR] ?? '#8892b0';
        const won = b.isHighestBidder;
        return (
          <button
            key={b.auctionId}
            onClick={() => navigate(`/app/territory/${b.territoryId}`)}
            className="w-full flex items-center gap-3 p-3 rounded-xl border border-outline hover:bg-panel-deep transition-colors text-left"
          >
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center font-bold text-[11px] flex-shrink-0 border ${won ? 'bg-gold/20 border-gold/60 text-gold' : 'bg-outline-soft border-outline text-muted'}`}>
              {won ? '낙찰' : '패찰'}
            </div>
            <div className="flex-1">
              <div className="flex items-center gap-1.5 mb-0.5">
                <p className="text-foreground font-semibold text-[13px]">({b.coordX}, {b.coordY})</p>
                <span className="px-1.5 py-0.5 rounded font-bold text-[9px]" style={{ color, background: color + '20' }}>{b.grade}급</span>
              </div>
              <p className="text-muted text-[11px]">{b.continentName} · {new Date(b.endAt).toLocaleDateString('ko-KR')} 종료</p>
              <p className="text-muted text-[10px]">내 입찰 {b.myBidAmount.toLocaleString()} AP</p>
            </div>
            <div className="text-right">
              <p className={`font-bold text-[13px] ${won ? 'text-gold' : 'text-muted'}`}>{b.currentPrice.toLocaleString()} AP</p>
              <p className="text-muted text-[10px]">최종가</p>
            </div>
          </button>
        );
      })}
    </div>
  );
}
