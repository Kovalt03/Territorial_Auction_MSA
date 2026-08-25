import { useEffect, useState } from 'react';

import {
  fetchAuctionSetting, setAuctionSetting,
  fetchActiveAuctions, forceSettleAuction, forceCancelAuction,
} from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminAuction } from '../types/admin';

const PAGE_SIZE = 20;

export function AdminAuctionPage() {
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [auctions, setAuctions] = useState<AdminAuction[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [actingId, setActingId] = useState<number | null>(null);

  useEffect(() => {
    fetchAuctionSetting()
      .then(r => setEnabled(r.auctionEnabled))
      .catch(e => { setError(e instanceof ApiError ? e.message : '경매 설정을 불러올 수 없습니다.'); console.warn('[AdminAuction] setting', e); });
  }, []);

  const loadAuctions = () => {
    fetchActiveAuctions(page, PAGE_SIZE)
      .then(r => { setAuctions(r.auctions); setTotalCount(r.totalCount); })
      .catch(e => { setError(e instanceof ApiError ? e.message : '경매 목록을 불러올 수 없습니다.'); console.warn('[AdminAuction] list', e); });
  };
  useEffect(loadAuctions, [page]);

  const handleToggle = async () => {
    if (isSaving || enabled == null) return;
    setIsSaving(true); setError(null);
    try {
      const r = await setAuctionSetting(!enabled);
      setEnabled(r.auctionEnabled);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '변경에 실패했습니다.');
    } finally { setIsSaving(false); }
  };

  const act = async (a: AdminAuction, mode: 'settle' | 'cancel') => {
    if (actingId != null) return;
    const label = mode === 'settle' ? '강제 낙찰' : '강제 취소';
    if (!window.confirm(`(${a.coordX},${a.coordY}) 경매를 ${label}할까요?`)) return;
    setActingId(a.auctionId); setError(null); setMessage(null);
    try {
      if (mode === 'settle') await forceSettleAuction(a.auctionId);
      else await forceCancelAuction(a.auctionId);
      setMessage(`(${a.coordX},${a.coordY}) ${label} 완료`);
      loadAuctions();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : `${label}에 실패했습니다.`);
    } finally { setActingId(null); }
  };

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  return (
    <div className="h-full overflow-auto p-6">
      <h2 className="font-bold text-base mb-1">경매 관리</h2>
      <p className="text-muted text-xs mb-4">전역 신규 경매 제어 + 진행 중 경매 강제 종료.</p>

      {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}
      {message && <p className="text-gp text-xs mb-3">✓ {message}</p>}

      <div className="bg-panel border border-outline rounded-xl p-4 mb-6 max-w-lg">
        <div className="flex items-center justify-between mb-2">
          <p className="font-semibold text-sm">신규 경매 생성 (전역)</p>
          <span className="text-xs font-bold" style={{ color: enabled ? 'var(--color-gp)' : 'var(--color-flare)' }}>
            {enabled == null ? '' : enabled ? 'ON' : 'OFF'}
          </span>
        </div>
        <p className="text-dim text-[11px] mb-3 leading-relaxed">
          중지해도 이미 진행 중인 경매는 정상 종료됩니다. 다음 회차부터 신규 경매가 열리지 않습니다.
        </p>
        <button onClick={() => void handleToggle()} disabled={isSaving || enabled == null}
          className="w-full h-9 rounded-lg font-bold text-xs text-surface hover:brightness-110 disabled:opacity-40"
          style={{ background: enabled ? 'var(--color-flare)' : 'var(--color-gp)' }}>
          {isSaving ? '처리 중...' : enabled ? '전체 신규 경매 중지' : '전체 신규 경매 재개'}
        </button>
      </div>

      <h3 className="font-bold text-sm mb-2">진행 중 경매 <span className="text-muted font-normal">({totalCount})</span></h3>
      <table className="w-full text-xs">
        <thead className="text-dim text-[11px] border-b border-outline">
          <tr>
            <th className="text-left font-medium py-2 px-2">영토</th>
            <th className="text-left font-medium py-2 px-2">현재가</th>
            <th className="text-left font-medium py-2 px-2">최고 입찰자</th>
            <th className="text-left font-medium py-2 px-2">종료</th>
            <th className="text-right font-medium py-2 px-2 w-40"></th>
          </tr>
        </thead>
        <tbody>
          {auctions.map(a => (
            <tr key={a.auctionId} className="border-b border-outline-soft hover:bg-panel">
              <td className="py-2 px-2"><b>{a.continentName}</b> <span className="text-muted">({a.coordX},{a.coordY}) {a.grade}급</span></td>
              <td className="py-2 px-2">{a.currentPrice.toLocaleString()}</td>
              <td className="py-2 px-2">{a.currentBidderNickname ? <>{a.currentBidderNickname} <span className="text-dim">#{a.currentBidderId}</span></> : <span className="text-muted">없음</span>}</td>
              <td className="py-2 px-2 text-muted">{a.endAt.slice(0, 16).replace('T', ' ')}</td>
              <td className="py-2 px-2 text-right whitespace-nowrap">
                <button onClick={() => void act(a, 'settle')} disabled={actingId === a.auctionId || !a.currentBidderId}
                  title={!a.currentBidderId ? '입찰자가 없어 강제 낙찰 불가' : ''}
                  className="text-gp font-bold hover:brightness-125 disabled:opacity-30 mr-3">강제 낙찰</button>
                <button onClick={() => void act(a, 'cancel')} disabled={actingId === a.auctionId}
                  className="text-danger font-bold hover:brightness-125 disabled:opacity-40">강제 취소</button>
              </td>
            </tr>
          ))}
          {auctions.length === 0 && <tr><td colSpan={5} className="py-8 text-center text-muted">진행 중인 경매가 없습니다.</td></tr>}
        </tbody>
      </table>

      <div className="flex items-center justify-center gap-3 mt-4 text-xs">
        <button disabled={page <= 0} onClick={() => setPage(p => p - 1)}
          className="px-3 h-8 rounded-md border border-outline text-muted disabled:opacity-30 hover:text-foreground-soft">이전</button>
        <span className="text-dim">{page + 1} / {totalPages}</span>
        <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}
          className="px-3 h-8 rounded-md border border-outline text-muted disabled:opacity-30 hover:text-foreground-soft">다음</button>
      </div>
    </div>
  );
}
