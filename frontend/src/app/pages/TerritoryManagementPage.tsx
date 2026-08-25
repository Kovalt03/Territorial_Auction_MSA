import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router';

import { useMyBids } from '../hooks/useMyBids';
import { useVault } from '../hooks/useVault';
import { subscribeMultiple } from '../hooks/useStompClient';

import { GNB } from '../components/GNB';
import { MyBidActivityList } from './MyBidActivityList';
import { MyTerritoryList } from './MyTerritoryList';
import { MyTradeHistoryList } from './MyTradeHistoryList';
import { LandTaxView } from './LandTaxView';

type Tab = 'active' | 'mine' | 'history' | 'bids' | 'tax';

const TABS: { id: Tab; label: string }[] = [
  { id: 'active', label: '경매 진행' },
  { id: 'mine', label: '내 영토' },
  { id: 'history', label: '거래 내역' },
  { id: 'bids', label: '입찰 현황' },
  { id: 'tax', label: '토지세' },
];

const VALID_TABS = TABS.map(t => t.id) as string[];

export function TerritoryManagementPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const rawTab = searchParams.get('tab');
  const tab: Tab = VALID_TABS.includes(rawTab ?? '') ? (rawTab as Tab) : 'active';

  const { bids: myBids, isLoading: bidsLoading, refresh: refreshBids } = useMyBids();
  const { territories, isLoading: territoriesLoading } = useVault();
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const activeBids = myBids.filter(b => b.status === 'BIDDING');
  const endedBids = myBids.filter(b => b.status !== 'BIDDING');

  const activeBidAuctionIds = activeBids.map(b => b.auctionId).join(',');
  useEffect(() => {
    if (!activeBidAuctionIds) return;
    const ids = activeBidAuctionIds.split(',').map(Number);
    return subscribeMultiple(ids.map(id => `/sub/auction/${id}`), refreshBids);
  }, [activeBidAuctionIds, refreshBids]);

  const setTab = (next: Tab) => {
    setSearchParams(prev => {
      const params = new URLSearchParams(prev);
      params.set('tab', next);
      return params;
    });
  };

  const counts: Record<Tab, number> = {
    active: activeBids.length,
    mine: territories.length,
    history: endedBids.length,
    bids: myBids.length,
    tax: 0,
  };

  return (
    <div className="page-root">
      <GNB />

      <div className="page-body">
        <h1 className="text-foreground font-bold mb-5 text-[26px]">🗺  영토 관리</h1>

        <div className="card overflow-hidden mb-5">
          <div className="flex overflow-x-auto">
            {TABS.map(t => (
              <button
                key={t.id}
                onClick={() => setTab(t.id)}
                className={`flex-1 min-w-[88px] py-3 font-semibold text-[13px] transition-colors relative ${tab === t.id ? 'text-primary bg-primary/10' : 'text-muted'}`}
              >
                {t.label}
                {counts[t.id] > 0 && (
                  <span className={`ml-1.5 px-1.5 py-0.5 rounded-full text-[10px] ${tab === t.id ? 'bg-primary text-surface' : 'bg-outline text-muted'}`}>
                    {counts[t.id]}
                  </span>
                )}
                {tab === t.id && <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />}
              </button>
            ))}
          </div>
        </div>

        {tab === 'tax' ? (
          <LandTaxView />
        ) : (
          <div className="card p-4">
            {tab === 'history' ? (
              <MyTradeHistoryList bids={endedBids} isLoading={bidsLoading} />
            ) : tab === 'mine' ? (
              <MyTerritoryList territories={territories} isLoading={territoriesLoading} />
            ) : (
              <MyBidActivityList bids={tab === 'active' ? activeBids : myBids} isLoading={bidsLoading} now={now} />
            )}
          </div>
        )}
      </div>
    </div>
  );
}
