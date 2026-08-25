import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router';

import { fetchLandTaxStatus, fetchLandTaxLogList } from '../api/landTax';

import type { LandTaxStatus, LandTaxLogItem, TaxStatus } from '../types/landTax';

export const LAND_TAX_PAGE_SIZE = 10;

const TAX_STATUSES: readonly TaxStatus[] = ['PAID', 'FAILED', 'EXEMPT', 'EVICTED'];

function parseStatus(raw: string | null): TaxStatus | null {
  return TAX_STATUSES.includes(raw as TaxStatus) ? (raw as TaxStatus) : null;
}

export function useLandTax() {
  const [status, setStatus] = useState<LandTaxStatus | null>(null);
  const [isStatusLoading, setIsStatusLoading] = useState(true);
  const [statusError, setStatusError] = useState<string | null>(null);

  // 유예/강제처분 경고 배너용 — 필터와 무관하게 가장 최근 1건을 별도 조회한다
  const [latestLog, setLatestLog] = useState<LandTaxLogItem | null>(null);

  const [logs, setLogs] = useState<LandTaxLogItem[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [isLogsLoading, setIsLogsLoading] = useState(true);
  const [logsError, setLogsError] = useState<string | null>(null);

  const [searchParams, setSearchParams] = useSearchParams();
  const page = Number(searchParams.get('page') ?? '0');
  const statusFilter = parseStatus(searchParams.get('status'));

  useEffect(() => {
    let cancelled = false;
    setIsStatusLoading(true);
    setStatusError(null);

    fetchLandTaxStatus()
      .then(data => { if (!cancelled) setStatus(data); })
      .catch(e => {
        if (cancelled) return;
        setStatusError('토지세 현황을 불러올 수 없습니다.');
        console.warn('[useLandTax] status fetch failed', e);
      })
      .finally(() => { if (!cancelled) setIsStatusLoading(false); });

    fetchLandTaxLogList({ page: 0, size: 1 })
      .then(data => { if (!cancelled) setLatestLog(data.logs[0] ?? null); })
      .catch(e => { console.warn('[useLandTax] latest log fetch failed', e); });

    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setIsLogsLoading(true);
    setLogsError(null);

    fetchLandTaxLogList({ page, size: LAND_TAX_PAGE_SIZE, status: statusFilter ?? undefined })
      .then(data => {
        if (cancelled) return;
        setLogs(data.logs);
        setTotalCount(data.totalCount);
      })
      .catch(e => {
        if (cancelled) return;
        setLogsError('납세 내역을 불러올 수 없습니다.');
        console.warn('[useLandTax] logs fetch failed', e);
      })
      .finally(() => { if (!cancelled) setIsLogsLoading(false); });

    return () => { cancelled = true; };
  }, [page, statusFilter]);

  const setPage = (next: number) => {
    setSearchParams(prev => {
      const params = new URLSearchParams(prev);
      params.set('page', String(next));
      return params;
    });
  };

  const setStatusFilter = (filter: TaxStatus | null) => {
    setSearchParams(prev => {
      const params = new URLSearchParams(prev);
      if (filter) params.set('status', filter);
      else params.delete('status');
      params.set('page', '0');
      return params;
    });
  };

  return {
    status, isStatusLoading, statusError,
    latestLog,
    logs, totalCount, isLogsLoading, logsError,
    page, statusFilter, setPage, setStatusFilter,
  };
}
