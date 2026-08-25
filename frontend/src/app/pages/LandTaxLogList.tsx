import { EmptyState } from '../components/EmptyState';
import { LAND_TAX_PAGE_SIZE } from '../hooks/useLandTax';

import type { LandTaxLogItem, TaxStatus } from '../types/landTax';

const FILTERS: { label: string; value: TaxStatus | null }[] = [
  { label: '전체', value: null },
  { label: '납부', value: 'PAID' },
  { label: '미납', value: 'FAILED' },
  { label: '면제', value: 'EXEMPT' },
  { label: '강제처분', value: 'EVICTED' },
];

const STATUS_META: Record<TaxStatus, { label: string; className: string }> = {
  PAID: { label: '납부', className: 'text-primary' },
  FAILED: { label: '미납', className: 'text-danger' },
  EXEMPT: { label: '면제', className: 'text-muted' },
  EVICTED: { label: '강제처분', className: 'text-[#ff8c00]' },
};

interface Props {
  logs: LandTaxLogItem[];
  totalCount: number;
  page: number;
  statusFilter: TaxStatus | null;
  isLoading: boolean;
  error: string | null;
  onPageChange: (page: number) => void;
  onFilterChange: (filter: TaxStatus | null) => void;
}

export function LandTaxLogList({
  logs,
  totalCount,
  page,
  statusFilter,
  isLoading,
  error,
  onPageChange,
  onFilterChange,
}: Props) {
  const totalPages = Math.max(1, Math.ceil(totalCount / LAND_TAX_PAGE_SIZE));

  return (
    <div className="card overflow-hidden">
      <div className="bg-elevated px-4 py-2.5 border-b border-outline">
        <span className="text-foreground font-semibold text-[13px]">납세 내역</span>
      </div>

      <div className="flex flex-wrap gap-2 p-4 pb-0">
        {FILTERS.map(filter => {
          const isActive = statusFilter === filter.value;
          return (
            <button
              key={filter.label}
              onClick={() => onFilterChange(filter.value)}
              className={`h-8 px-3 rounded-lg text-xs transition-colors ${isActive ? 'bg-primary text-surface font-semibold' : 'bg-elevated text-muted hover:text-foreground'}`}
            >
              {filter.label}
            </button>
          );
        })}
      </div>

      <div className="p-4 space-y-2.5">
        {error ? (
          <p className="text-danger text-[11px] py-4 text-center">⚠ {error}</p>
        ) : isLoading ? (
          [1, 2, 3].map(i => (
            <div key={i} className="bg-panel-deep border border-outline rounded-xl h-16 animate-pulse" />
          ))
        ) : logs.length === 0 ? (
          <EmptyState message="납세 내역이 없습니다." />
        ) : (
          logs.map(log => {
            const meta = STATUS_META[log.status];
            return (
              <div
                key={log.logId}
                className="bg-panel-deep border border-outline rounded-xl p-3.5 flex items-center justify-between"
              >
                <div>
                  <p className="text-foreground text-sm">{new Date(log.chargedAt).toLocaleDateString('ko-KR')}</p>
                  <p className="text-muted text-[11px] mt-0.5">
                    영토 {log.territoryCount}개
                    {log.status === 'EXEMPT'
                      ? ' · 세금 없음 (면제 구간)'
                      : ` · ${log.gpCharged.toLocaleString()} GP 차감`}
                  </p>
                </div>
                <span className={`text-xs font-semibold ${meta.className}`}>{meta.label}</span>
              </div>
            );
          })
        )}
      </div>

      {!isLoading && !error && totalCount > LAND_TAX_PAGE_SIZE && (
        <div className="flex flex-wrap justify-center items-center gap-1.5 p-4 pt-0">
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i}
              onClick={() => onPageChange(i)}
              className={`w-8 h-8 rounded-lg text-xs transition-colors ${i === page ? 'bg-primary text-surface font-semibold' : 'bg-elevated text-muted hover:text-foreground'}`}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
