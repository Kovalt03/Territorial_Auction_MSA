import { useEffect, useState } from 'react';

import { fetchUserActiveBids, fetchUserBids, fetchUserTerritories } from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminUserActiveBid, AdminUserBid, AdminUserTerritory } from '../types/admin';

type Tab = 'active' | 'history' | 'territories';
const TABS: { key: Tab; label: string }[] = [
  { key: 'active', label: '입찰중' },
  { key: 'history', label: '입찰 내역' },
  { key: 'territories', label: '보유 영토' },
];

interface Props {
  userId: number;
}

export function UserActivityPanel({ userId }: Props) {
  const [tab, setTab] = useState<Tab>('active');
  const [active, setActive] = useState<AdminUserActiveBid[]>([]);
  const [history, setHistory] = useState<AdminUserBid[]>([]);
  const [territories, setTerritories] = useState<AdminUserTerritory[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError(null);
    const onErr = (e: unknown) => {
      setError(e instanceof ApiError ? e.message : '불러올 수 없습니다.');
      console.warn('[UserActivity] fetch failed', e);
    };
    if (tab === 'active') fetchUserActiveBids(userId).then(r => setActive(r.activeBids)).catch(onErr);
    else if (tab === 'history') fetchUserBids(userId, 0).then(r => setHistory(r.bids)).catch(onErr);
    else fetchUserTerritories(userId, 0).then(r => setTerritories(r.territories)).catch(onErr);
  }, [tab, userId]);

  return (
    <div>
      <div className="flex gap-1 mb-3">
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-3 h-7 rounded-md text-[11px] font-semibold border ${tab === t.key ? 'border-primary text-primary' : 'border-outline text-muted hover:text-foreground-soft'}`}>
            {t.label}
          </button>
        ))}
      </div>
      {error && <p className="text-danger text-[11px] mb-2">{error}</p>}
      {tab === 'active' && <ActiveList items={active} />}
      {tab === 'history' && <HistoryList items={history} />}
      {tab === 'territories' && <TerritoryList items={territories} />}
    </div>
  );
}

function Coord({ name, x, y, grade }: { name: string; x: number; y: number; grade: string }) {
  return (
    <span>
      <b>{name}</b> <span className="text-muted">({x},{y}) {grade}급</span>
    </span>
  );
}

function Empty({ text }: { text: string }) {
  return <p className="text-muted text-[11px] py-4 text-center">{text}</p>;
}

function ActiveList({ items }: { items: AdminUserActiveBid[] }) {
  if (items.length === 0) return <Empty text="진행 중인 입찰이 없습니다." />;
  return (
    <div className="space-y-1.5">
      {items.map(b => (
        <div key={b.auctionId} className="flex items-center justify-between bg-elevated rounded-md px-3 py-2 text-[11px]">
          <Coord name={b.continentName} x={b.coordX} y={b.coordY} grade={b.grade} />
          <span className="flex items-center gap-2">
            <span className="text-muted">내 {b.myBidPrice} / 현재 {b.currentPrice}</span>
            {b.topBidder ? <span className="text-gp font-bold">최고가</span> : <span className="text-flare font-bold">밀림</span>}
          </span>
        </div>
      ))}
    </div>
  );
}

function HistoryList({ items }: { items: AdminUserBid[] }) {
  if (items.length === 0) return <Empty text="입찰 내역이 없습니다." />;
  return (
    <div className="space-y-1.5">
      {items.map((b, i) => (
        <div key={`${b.auctionId}-${i}`} className="flex items-center justify-between bg-elevated rounded-md px-3 py-2 text-[11px]">
          <Coord name={b.continentName} x={b.coordX} y={b.coordY} grade={b.grade} />
          <span className="flex items-center gap-2">
            <span className="text-muted">{b.myBidPrice} · {b.bidAt.slice(0, 10)}</span>
            {b.ongoing ? <span className="text-flare font-bold">진행중</span> : <span className="text-dim">종료</span>}
          </span>
        </div>
      ))}
    </div>
  );
}

function TerritoryList({ items }: { items: AdminUserTerritory[] }) {
  if (items.length === 0) return <Empty text="보유한 영토가 없습니다." />;
  return (
    <div className="space-y-1.5">
      {items.map(t => (
        <div key={t.territoryId} className="flex items-center justify-between bg-elevated rounded-md px-3 py-2 text-[11px]">
          <Coord name={t.continentName} x={t.coordX} y={t.coordY} grade={t.grade} />
          <span className="text-right">
            <span className="text-muted">{t.status}</span>
            {t.occupiedUntil && (
              <span className="block text-[10px] text-dim">점유 만료 {t.occupiedUntil.slice(0, 16).replace('T', ' ')}</span>
            )}
          </span>
        </div>
      ))}
    </div>
  );
}
