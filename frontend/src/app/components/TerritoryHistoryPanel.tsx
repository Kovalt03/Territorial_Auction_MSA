import { useState, useMemo } from 'react';

import { LineChart } from './LineChart';
import type { TerritoryAuctionHistoryEntry } from '../types/auction';

export type ChartRange = '3일' | '7일' | '30일';

const RANGE_MS: Record<ChartRange, number> = {
  '3일': 3 * 86_400_000,
  '7일': 7 * 86_400_000,
  '30일': 30 * 86_400_000,
};

const HISTORY_MAX = 8;

interface Props {
  history: TerritoryAuctionHistoryEntry[];
  isLoading?: boolean;
  gradeColor: string;
  compact?: boolean;
}

function formatWonAt(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', { month: '2-digit', day: '2-digit' });
}

export function TerritoryHistoryPanel({ history, isLoading, gradeColor, compact }: Props) {
  const [range, setRange] = useState<ChartRange>('7일');

  const chartData = useMemo(() => {
    const cutoff = Date.now() - RANGE_MS[range];
    return history
      .filter(h => new Date(h.wonAt).getTime() >= cutoff)
      .map(h => h.finalPrice);
  }, [history, range]);

  const titleSize = compact ? 'text-[10px]' : 'text-xs';
  const cardPad = compact ? 'p-2.5' : 'p-4';

  return (
    <>
      <div className={`card ${cardPad}`}>
        <div className="flex items-center justify-between mb-2">
          <p className={`text-muted ${titleSize}`}>가격 추이</p>
          <div className="flex gap-1">
            {(['3일', '7일', '30일'] as ChartRange[]).map(r => (
              <button
                key={r}
                onClick={() => setRange(r)}
                className={`px-2 h-6 rounded-md text-[10px] font-semibold transition-colors border ${range === r ? 'text-surface' : 'text-muted border-outline bg-elevated'}`}
                style={range === r ? { background: gradeColor, borderColor: gradeColor } : undefined}
              >
                {r}
              </button>
            ))}
          </div>
        </div>

        {chartData.length > 0 ? (
          <>
            <div className="flex justify-between mb-1">
              <span className="text-muted text-[9px]">{Math.min(...chartData).toLocaleString()}</span>
              <span className="text-[9px]" style={{ color: gradeColor }}>{Math.max(...chartData).toLocaleString()} AP</span>
            </div>
            <LineChart data={chartData} color={gradeColor} />
          </>
        ) : (
          <p className="text-center text-muted text-[10px] py-3">
            {isLoading ? '불러오는 중...' : `${range} 거래 내역 없음`}
          </p>
        )}
      </div>

      <div>
        <p className={`text-muted ${titleSize} mb-1.5`}>거래 내역 ({history.length}건)</p>
        {isLoading ? (
          <p className="text-center text-muted text-[10px] py-2">불러오는 중...</p>
        ) : history.length === 0 ? (
          <p className="text-center text-muted text-[10px] py-2">거래 내역 없음</p>
        ) : (
          <div className="space-y-1">
            {history.slice(0, HISTORY_MAX).map(h => (
              <div
                key={h.auctionId}
                className="flex items-center justify-between rounded-lg px-2.5 py-1.5 bg-panel-deep border border-outline-soft"
              >
                <div>
                  <p className="text-foreground-soft font-semibold text-[10px]">
                    {h.winnerNickname}
                  </p>
                  <p className="text-muted text-[8px]">{formatWonAt(h.wonAt)} 낙찰</p>
                </div>
                <p className="font-bold text-[10px]" style={{ color: gradeColor }}>
                  {h.finalPrice.toLocaleString()} AP
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
