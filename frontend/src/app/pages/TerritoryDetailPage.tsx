import { useState, useEffect, useMemo, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router';

import { useApp } from '../context/AppContext';
import { placeBidApi, fetchTerritoryAuctionHistory } from '../api/auction';
import { fetchMyWallet } from '../api/user';
import { fetchChatHistory } from '../api/chat';
import { fetchTerritoryDetail } from '../api/map';
import { fetchSiegeEvents, fetchSiegeTarget, type SiegeEventItem, type SiegeTargetIntel } from '../api/siege';
import type { TerritoryDetailResponse } from '../types/territory';
import { useTerritoryDetail } from '../hooks/useTerritoryDetail';
import { useMyBids } from '../hooks/useMyBids';
import { useWishlist } from '../hooks/useWishlist';
import { useStompSubscribe, useStompPublish } from '../hooks/useStompClient';
import { GNB } from '../components/GNB';
import { SiegeBuildingGrid } from '../components/SiegeBuildingGrid';
import { LineChart } from '../components/LineChart';
import { LoadingState } from '../components/LoadingState';
import type { MyBidEntry } from '../types/auction';
import { GRADE_COLOR, type Grade } from '../types/grade';

import { BidConfirmModal } from './BidConfirmModal';
import { TerritoryChat, type ChatMsg } from './TerritoryChat';
import { BidPanel } from './BidPanel';

type ListTab = 'bidding' | 'wishlist';
type ChartRange = '3일' | '7일' | '30일';

const RANGE_MS: Record<ChartRange, number> = {
  '3일': 3 * 86400_000,
  '7일': 7 * 86400_000,
  '30일': 30 * 86400_000,
};

interface AuctionWsMessage {
  auctionId: number;
  currentPrice: number;
  bidderId: number;
  bidderNickname: string;
  bidAt: string;
  endAt?: string;
}

interface ChatWsMessage {
  messageId: number;
  senderId: number;
  senderNickname: string;
  content: string;
  sentAt: string;
}

function getStatusLabel(status: string | undefined, isMyTerritory: boolean): string {
  if (!status) return '미점령';
  if (isMyTerritory) return '✓ 내 영토';
  if (status === 'BIDDING') return '⚡ 경매 중';
  if (status === 'OCCUPIED') return '점령됨';
  return '미점령';
}

function getStatusColor(status: string | undefined, isMyTerritory: boolean): string {
  if (isMyTerritory) return '#00ff88';
  if (status === 'BIDDING') return '#ffd700';
  return '#7788a5';
}

export function TerritoryDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { ap, syncAP, userId, isLoggedIn } = useApp();

  const territoryId = Number(id);
  const { territory, bids, isLoading, error, refreshBids, updateCurrentPrice } = useTerritoryDetail(territoryId);
  const { bids: myBids, refresh: refreshMyBids } = useMyBids();

  const gradeColor = GRADE_COLOR[(territory?.grade ?? 'B') as Grade] ?? '#00ff88';
  const gridSize = territory?.gridSize ?? 8;
  const currentBid = territory?.auction?.currentPrice ?? 0;
  const auctionId = territory?.auction?.auctionId ?? null;
  const minBid = Math.max(Math.ceil(currentBid * 1.05), currentBid + 10);

  const myBidEntry: MyBidEntry | undefined = myBids.find(b => b.territoryId === territoryId);
  const myBid = myBidEntry?.myBidAmount ?? 0;
  const isHighestBidder = !!myBidEntry?.isHighestBidder;
  const isOutbid = myBid > 0 && !isHighestBidder;
  const isMyTerritory = territory?.owner?.userId === userId;

  const [listTab, setListTab] = useState<ListTab>('bidding');
  const [bidSort, setBidSort] = useState<'time' | 'ap' | 'outbid'>('time');
  const [wishlistTerritories, setWishlistTerritories] = useState<TerritoryDetailResponse[]>([]);
  const [isLoadingWishlist, setIsLoadingWishlist] = useState(false);
  const [now, setNow] = useState(Date.now());
  const isAuctionEnded =
    !!territory?.auction && new Date(territory.auction.endAt).getTime() <= now;
  const [bidAmount, setBidAmount] = useState(currentBid + 100);
  const [showConfirm, setShowConfirm] = useState(false);
  const [isBidding, setIsBidding] = useState(false);
  const [bidError, setBidError] = useState<string | null>(null);
  const [bidDone, setBidDone] = useState(false);
  const [chartRange, setChartRange] = useState<ChartRange>('7일');
  const [chatMessages, setChatMessages] = useState<ChatMsg[]>([]);
  const [chatInput, setChatInput] = useState('');
  const { wishlistIds: localWishlist, toggle: toggleWishlist } = useWishlist();
  const stompPublish = useStompPublish();

  const chatRoomId = `room_territory_${territoryId}`;
  const auctionWsDest = auctionId ? `/sub/auction/${auctionId}` : null;

  // Real-time auction updates
  const handleAuctionMessage = useCallback((msg: AuctionWsMessage) => {
    if (msg.auctionId === auctionId) {
      updateCurrentPrice(msg.currentPrice);
      refreshBids(auctionId);
      refreshMyBids();
      // 상대방이 입찰하면 내 locked AP가 환불되므로 지갑 즉시 갱신
      if (msg.bidderId !== userId) {
        fetchMyWallet().then(wallet => syncAP(wallet.availableAP)).catch((e) => console.warn('[TerritoryDetailPage] wallet sync failed', e));
      }
    }
  }, [auctionId, refreshBids, updateCurrentPrice, refreshMyBids, userId, syncAP]);
  useStompSubscribe<AuctionWsMessage>(auctionWsDest, handleAuctionMessage);

  // Real-time chat
  const handleChatMessage = useCallback((msg: ChatWsMessage) => {
    const now = new Date(msg.sentAt);
    const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
    setChatMessages(prev => [...prev, { user: msg.senderNickname, text: msg.content, time, mine: msg.senderId === userId }]);
  }, [userId]);
  useStompSubscribe<ChatWsMessage>(`/sub/chat/${chatRoomId}`, handleChatMessage);

  // Load chat history on mount
  useEffect(() => {
    fetchChatHistory(chatRoomId, { size: 30 })
      .then(res => {
        const msgs = res.messages.map(m => {
          const d = new Date(m.sentAt);
          const time = `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;
          return { user: m.senderNickname, text: m.content, time, mine: m.senderId === userId };
        });
        setChatMessages(msgs);
      })
      .catch((e) => console.warn('[TerritoryDetailPage] chat history load failed', e));
  }, [chatRoomId, userId]);

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    setBidAmount(Math.max(Math.ceil(currentBid * 1.05), currentBid + 10));
  }, [currentBid]);

  const [auctionHistory, setAuctionHistory] = useState<{ price: number; wonAt: string }[]>([]);
  useEffect(() => {
    if (!territoryId) return;
    fetchTerritoryAuctionHistory(territoryId)
      .then(res => setAuctionHistory(res.histories.map(h => ({ price: h.finalPrice, wonAt: h.wonAt }))))
      .catch(() => setAuctionHistory([]));
  }, [territoryId]);

  // 이 영토가 공성 대상(진행 중)인지 — 상세 상단 '공성 중' 배지 + 공성 상세 패널에 쓴다.
  const [activeSiege, setActiveSiege] = useState<SiegeEventItem | null>(null);
  useEffect(() => {
    if (!territoryId) return;
    fetchSiegeEvents('PENDING')
      .then(r => setActiveSiege(r.sieges.find(s => s.targetTerritory.id === territoryId) ?? null))
      .catch(e => console.warn('[TerritoryDetail] siege lookup failed', e));
  }, [territoryId]);

  // 영토의 실제 건물 배치(intel). 점령 영토만 응답 — 미점령/비로그인은 조용히 무시하고 장식 격자로 폴백.
  const [siegeIntel, setSiegeIntel] = useState<SiegeTargetIntel | null>(null);
  useEffect(() => {
    if (!territoryId) return;
    fetchSiegeTarget(territoryId)
      .then(setSiegeIntel)
      .catch(() => setSiegeIntel(null));
  }, [territoryId]);

  const zoneEffect = (zone: number) =>
    zone === 1 ? '성 점령' : zone === 2 ? '생산 마비' : zone === 3 ? '저장소 약탈' : '';
  const remainingText = (iso: string) => {
    const secs = Math.max(0, Math.floor((new Date(iso).getTime() - now) / 1000));
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = secs % 60;
    return h > 0 ? `${h}시간 ${m}분` : m > 0 ? `${m}분 ${s}초` : `${s}초`;
  };

  const chartData = useMemo(() => {
    const cutoff = Date.now() - RANGE_MS[chartRange];
    return auctionHistory
      .filter(h => new Date(h.wonAt).getTime() >= cutoff)
      .map(h => h.price);
  }, [auctionHistory, chartRange]);

  const handleBid = async () => {
    if (!auctionId || isAuctionEnded) return;
    setIsBidding(true);
    setBidError(null);
    try {
      const result = await placeBidApi(auctionId, bidAmount);
      const wallet = await fetchMyWallet();
      syncAP(wallet.availableAP);
      updateCurrentPrice(result.newPrice);
      await refreshBids(auctionId);
      refreshMyBids();
      setBidDone(true);
      setShowConfirm(false);
      setTimeout(() => setBidDone(false), 2500);
    } catch {
      setBidError('입찰에 실패했습니다. AP를 확인하거나 다시 시도해주세요.');
      setShowConfirm(false);
    } finally {
      setIsBidding(false);
    }
  };

  const handleSendChat = () => {
    const text = chatInput.trim();
    if (!text || !isLoggedIn) return;
    stompPublish(`/pub/chat/${chatRoomId}`, { content: text });
    setChatInput('');
  };

  const activeBids = myBids.filter(b => b.status === 'BIDDING');

  useEffect(() => {
    if (listTab !== 'wishlist' || localWishlist.size === 0) {
      setWishlistTerritories([]);
      return;
    }
    setIsLoadingWishlist(true);
    Promise.all([...localWishlist].map(id => fetchTerritoryDetail(id)))
      .then(results => setWishlistTerritories(results))
      .catch((e) => console.warn('[TerritoryDetailPage] wishlist territories load failed', e))
      .finally(() => setIsLoadingWishlist(false));
  }, [listTab, localWishlist]);

  function sortedBidList(list: typeof myBids) {
    if (bidSort === 'ap') return [...list].sort((a, b) => b.currentPrice - a.currentPrice);
    if (bidSort === 'outbid') return [...list].sort((a, b) => {
      if (a.isHighestBidder !== b.isHighestBidder) return a.isHighestBidder ? 1 : -1;
      return 0;
    });
    return [...list].sort((a, b) => new Date(a.endAt).getTime() - new Date(b.endAt).getTime());
  }

  return (
    <div className="page-root">
      <GNB />

      <div className="flex flex-1 overflow-hidden">
        {/* ── Left Panel ── Bidding list / Wishlist */}
        <div className="w-[270px] bg-panel-deep border-r border-outline-soft flex flex-col flex-shrink-0">
          <div className="flex border-b border-outline-soft">
            {([['bidding', '입찰 중', '#00f5ff', activeBids.length], ['wishlist', '관심 등록', '#ffd700', localWishlist.size]] as const).map(([tab, label, color, cnt]) => (
              <button
                key={tab}
                onClick={() => setListTab(tab)}
                className="flex-1 py-3 text-xs font-semibold transition-colors"
                style={{
                  color: listTab === tab ? color : '#7788a5',
                  background: listTab === tab ? color + '10' : 'transparent',
                  borderBottom: listTab === tab ? `2px solid ${color}` : '2px solid transparent',
                }}
              >
                {label} {cnt}
              </button>
            ))}
          </div>

          {listTab === 'bidding' && activeBids.length > 0 && (
            <div className="flex items-center gap-1 px-3 py-2 border-b border-outline-soft">
              {([
                { val: 'time', label: '⏱' },
                { val: 'ap', label: '💰' },
                { val: 'outbid', label: '🔺' },
              ] as { val: 'time' | 'ap' | 'outbid'; label: string }[]).map(s => (
                <button
                  key={s.val}
                  onClick={() => setBidSort(s.val)}
                  className="flex-1 h-6 rounded-md text-[10px] font-semibold transition-colors"
                  style={{
                    background: bidSort === s.val ? '#00f5ff' : 'var(--color-outline-soft)',
                    color: bidSort === s.val ? '#060a14' : '#7788a5',
                    border: `1px solid ${bidSort === s.val ? '#00f5ff' : '#2a3a5a'}`,
                  }}
                >
                  {s.label} {s.val === 'time' ? '시간' : s.val === 'ap' ? 'AP' : '상회'}
                </button>
              ))}
            </div>
          )}

          <div className="flex-1 overflow-y-auto p-3 space-y-2">
            {listTab === 'bidding' && sortedBidList(activeBids).map(b => {
              const isLeading = b.isHighestBidder;
              const isLosing = !b.isHighestBidder;
              const isCurrent = b.territoryId === territoryId;
              const borderColor = isLosing ? '#ff4444' : isLeading ? '#00ff88' : isCurrent ? '#00f5ff' : 'var(--color-outline-soft)';
              return (
                <div
                  key={b.auctionId}
                  onClick={() => navigate(`/app/territory/${b.territoryId}`)}
                  className="rounded-xl p-3 cursor-pointer transition-all hover:brightness-110"
                  style={{
                    background: isLeading ? '#0a1f12' : isCurrent ? 'var(--color-outline-soft)' : 'var(--color-panel-deep)',
                    border: `1px solid ${borderColor}`,
                  }}
                >
                  {isLosing && (
                    <div className="flex items-center gap-1 mb-2 px-2 py-1 rounded-lg" style={{ background: '#ff222215', border: '1px solid #ff444440' }}>
                      <span className="text-[9px]">🔺</span>
                      <span className="text-danger font-bold text-[9px]">상회 입찰됨</span>
                      <span className="text-muted ml-auto text-[9px]">내 입찰 {b.myBidAmount.toLocaleString()}</span>
                    </div>
                  )}
                  {isLeading && (
                    <div className="flex items-center gap-1 mb-2 px-2 py-1 rounded-lg" style={{ background: '#00ff8815', border: '1px solid #00ff8840' }}>
                      <span className="text-[9px]">✓</span>
                      <span className="text-gp font-bold text-[9px]">최고 입찰 중</span>
                      <div className="ml-auto w-1.5 h-1.5 bg-gp rounded-full animate-pulse" />
                    </div>
                  )}
                  <div className="flex items-center gap-1.5 mb-0.5">
                    <span className="text-foreground font-semibold text-xs">
                      ({b.coordX}, {b.coordY})
                    </span>
                    <span
                      className="px-1.5 py-0.5 rounded font-bold text-[9px]"
                      style={{ color: GRADE_COLOR[b.grade as Grade] ?? '#8892b0', background: (GRADE_COLOR[b.grade as Grade] ?? '#8892b0') + '20' }}
                    >
                      {b.grade}급
                    </span>
                  </div>
                  <p className="text-muted mb-2 text-[9px]">{b.continentName}</p>
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-muted text-[10px]">현재가</span>
                    <span className="text-[11px] font-bold" style={{ color: isLosing ? '#ff5555' : isLeading ? '#00ff88' : '#00f5ff' }}>
                      {b.currentPrice.toLocaleString()} AP
                    </span>
                  </div>
                  <div className="flex items-center gap-1.5 pt-1.5 border-t border-outline-soft">
                    <div className="flex items-center gap-1 flex-1">
                      <div className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${isLosing ? 'bg-danger' : 'bg-gp animate-pulse'}`} />
                      <span className="text-muted text-[9px]">내 입찰 {b.myBidAmount.toLocaleString()}</span>
                    </div>
                    {(() => {
                      const diff = new Date(b.endAt).getTime() - now;
                      const timeStr = diff <= 0 ? '종료' : (() => {
                        const h = Math.floor(diff / 3600000);
                        const m = Math.floor((diff % 3600000) / 60000);
                        const s = Math.floor((diff % 60000) / 1000);
                        return h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
                      })();
                      const isUrgent = diff > 0 && diff < 300000;
                      return (
                        <span className="font-bold tabular-nums text-[9px]" style={{ color: isUrgent ? '#ff8c00' : 'var(--color-muted)' }}>
                          {timeStr}
                        </span>
                      );
                    })()}
                  </div>
                </div>
              );
            })}

            {listTab === 'bidding' && activeBids.length === 0 && (
              <div className="text-center py-8">
                <p className="text-muted text-[13px]">입찰 중인 영토가 없습니다</p>
                <button onClick={() => navigate('/app/map')} className="mt-3 px-4 py-1.5 bg-outline-soft border border-outline rounded-lg text-muted text-[11px] hover:text-foreground-soft transition-colors">
                  지도로 이동 →
                </button>
              </div>
            )}

            {listTab === 'wishlist' && isLoadingWishlist && <LoadingState />}

            {listTab === 'wishlist' && !isLoadingWishlist && wishlistTerritories.map(t => {
              const isCurrent = t.territoryId === territoryId;
              const hasAuction = t.auction !== null;
              return (
                <div
                  key={t.territoryId}
                  onClick={() => navigate(`/app/territory/${t.territoryId}`)}
                  className="rounded-xl p-3 cursor-pointer transition-all hover:brightness-110"
                  style={{
                    background: isCurrent ? 'var(--color-outline-soft)' : 'var(--color-panel-deep)',
                    border: `1px solid ${isCurrent ? '#ffd700' : hasAuction ? '#ffd70040' : 'var(--color-outline-soft)'}`,
                  }}
                >
                  <div className="flex items-center justify-between mb-1">
                    <div className="flex items-center gap-1.5">
                      <span className="text-foreground font-semibold text-xs">
                        ({t.coordX}, {t.coordY})
                      </span>
                      <span
                        className="px-1.5 py-0.5 rounded font-bold text-[9px]"
                        style={{ color: GRADE_COLOR[t.grade as Grade] ?? '#8892b0', background: (GRADE_COLOR[t.grade as Grade] ?? '#8892b0') + '20' }}
                      >
                        {t.grade}급
                      </span>
                    </div>
                    <button
                      onClick={e => { e.stopPropagation(); void toggleWishlist(t.territoryId); }}
                      className="text-xs text-flare bg-transparent border-0 cursor-pointer p-0"
                    >
                      ♥
                    </button>
                  </div>
                  <p className="text-muted mb-2 text-[9px]">{t.continentName}</p>
                  {hasAuction ? (
                    <div className="flex items-center justify-between">
                      <span className="text-gold font-bold text-[9px]">경매 중</span>
                      <span className="text-gold font-bold text-[10px]">
                        {t.auction!.currentPrice.toLocaleString()} AP
                      </span>
                    </div>
                  ) : (
                    <p className="text-muted text-[9px]">
                      {t.status === 'OCCUPIED' ? (t.owner ? `${t.owner.nickname} 점령` : '점령됨') : '미점령'}
                    </p>
                  )}
                </div>
              );
            })}

            {listTab === 'wishlist' && !isLoadingWishlist && localWishlist.size === 0 && (
              <div className="text-center py-8">
                <p className="text-muted text-[13px]">관심 등록된 영토가 없습니다</p>
                <button onClick={() => navigate('/app/map')} className="mt-3 px-4 py-1.5 bg-outline-soft border border-outline rounded-lg text-muted text-[11px] hover:text-foreground-soft transition-colors">
                  지도로 이동 →
                </button>
              </div>
            )}
          </div>
        </div>

        {/* ── Right Panel ── Territory Detail */}
        <div className="flex-1 flex flex-col overflow-hidden">
          <div className="flex-1 flex flex-col overflow-hidden p-5">

            {isLoading && <LoadingState message="영토 정보 불러오는 중..." className="flex-1" />}

            {error && !isLoading && (
              <div className="flex-1 flex items-center justify-center">
                <p className="text-danger text-sm">{error}</p>
              </div>
            )}

            {!isLoading && !error && territory && (
              <>
                {/* Header */}
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <div className="flex items-center gap-2 mb-0.5">
                      <h1 className="text-foreground font-bold text-[22px]">
                        영토 ({territory.coordX}, {territory.coordY})
                      </h1>
                      <span className="px-2 py-0.5 rounded font-bold text-[11px]" style={{ color: gradeColor, background: gradeColor + '20', border: `1px solid ${gradeColor}50` }}>
                        {territory.grade}급
                      </span>
                      {activeSiege && (
                        <span className="px-2 py-0.5 rounded-lg font-bold text-[11px] animate-pulse" style={{ color: '#ff3333', background: '#ff222215', border: '1px solid #ff444440' }}>
                          🔴 공성 중 — {activeSiege.attacker.nickname} 공격
                        </span>
                      )}
                      {isOutbid && (
                        <span className="px-2 py-0.5 rounded-lg font-bold text-[11px] animate-pulse" style={{ color: '#ff5555', background: '#ff222215', border: '1px solid #ff444440' }}>
                          🔺 상회 입찰됨
                        </span>
                      )}
                      {bidDone && (
                        <span className="px-2 py-0.5 rounded-lg font-bold text-[11px]" style={{ color: '#00ff88', background: '#00ff8815', border: '1px solid #00ff8840' }}>
                          ✓ 입찰 완료
                        </span>
                      )}
                    </div>
                    <p className="text-muted text-[13px]">
                      {territory.continentName} · {gridSize}×{gridSize} 그리드
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    {isMyTerritory && (
                      <button
                        onClick={() => navigate(`/app/territory-grid/${territory.territoryId}`)}
                        className="h-9 px-4 rounded-xl border border-primary/50 text-primary text-[13px] hover:bg-primary/10 transition-colors"
                      >
                        🏗 건물 관리
                      </button>
                    )}
                    <button
                      onClick={() => toggleWishlist(territory.territoryId)}
                      className="h-9 px-4 rounded-xl border text-[13px] transition-colors"
                      style={{
                        color: localWishlist.has(territory.territoryId) ? '#ff1493' : '#7788a5',
                        borderColor: localWishlist.has(territory.territoryId) ? '#ff1493' : '#354064',
                        background: localWishlist.has(territory.territoryId) ? '#ff149320' : '#2a3050',
                      }}
                    >
                      {localWishlist.has(territory.territoryId) ? '♥ 관심 등록됨' : '♡ 관심 등록'}
                    </button>
                    <button onClick={() => navigate('/app/map')} className="text-muted hover:text-foreground text-xl px-2">✕</button>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4 flex-1 min-h-0">
                  {/* Left column — 공성 상세 + 건물 배치 */}
                  <div className="overflow-y-auto pr-1">
                    {activeSiege && (
                      <div className="card p-4 mb-4" style={{ border: '1px solid #ff333360' }}>
                        <p className="text-danger font-bold text-xs mb-2">⚔ 공성 상세 — 지금 공격받는 위치</p>
                        <div className="space-y-1.5 text-[12px]">
                          <div className="flex justify-between"><span className="text-muted">공격자</span><span className="text-foreground">{activeSiege.attacker.nickname}</span></div>
                          <div className="flex justify-between"><span className="text-muted">공격 구역</span><span className="text-foreground">Zone {activeSiege.attackZone} — {zoneEffect(activeSiege.attackZone)}</span></div>
                          <div className="flex justify-between"><span className="text-muted">공격 방식</span><span className="text-foreground">{activeSiege.targetBuilding ? `정밀 — ${activeSiege.targetBuilding.displayName ?? activeSiege.targetBuilding.name}` : '일반 (구역 전체 분산)'}</span></div>
                          <div className="flex justify-between"><span className="text-muted">정산까지</span><span className="text-gold font-bold">{remainingText(activeSiege.resolveAt)}</span></div>
                        </div>
                        <p className="text-muted text-[10px] mt-2">아래 그리드에서 빨간 테두리·강조된 칸이 공격받는 건물/구역입니다.</p>
                      </div>
                    )}
                    <div className="card p-4">
                      <div className="flex items-center justify-between mb-3">
                        <p className="text-muted text-xs">
                          {siegeIntel && siegeIntel.buildings.length > 0 ? '영토 건물 배치' : '영토 미리보기'}
                        </p>
                        <span className="font-bold text-[11px]" style={{ color: gradeColor }}>{gridSize}×{gridSize} ({territory.grade}급)</span>
                      </div>
                      <div className="max-w-[300px] mx-auto">
                        {siegeIntel && siegeIntel.buildings.length > 0 ? (
                          <>
                            <SiegeBuildingGrid
                              buildings={siegeIntel.buildings}
                              gridSize={gridSize}
                              highlightZone={activeSiege?.attackZone ?? null}
                              targetBuildingId={activeSiege?.targetBuilding?.buildingId ?? null}
                            />
                            <div className="flex gap-3 mt-2 justify-center flex-wrap">
                              {[1, 2, 3].map(z => (
                                <div key={z} className="flex items-center gap-1">
                                  <div className="w-2.5 h-2.5 rounded-sm" style={{ background: (z === 1 ? '#ff3333' : z === 2 ? '#ffd700' : '#00f5ff') + '55' }} />
                                  <span className="text-muted text-[9px]">Zone {z}</span>
                                </div>
                              ))}
                            </div>
                          </>
                        ) : (
                          <div className="grid gap-0.5" style={{ gridTemplateColumns: `repeat(${gridSize}, 1fr)` }}>
                            {Array.from({ length: gridSize * gridSize }, (_, i) => {
                              const x = i % gridSize, y = Math.floor(i / gridSize);
                              const half = gridSize / 2;
                              const isCore = x >= half - 1 && x < half + 1 && y >= half - 1 && y < half + 1;
                              const q = gridSize / 4;
                              const isInner = x >= q && x < gridSize - q && y >= q && y < gridSize - q && !isCore;
                              const bg = isCore ? gradeColor + '35' : isInner ? '#8b50ff20' : '#00f5ff10';
                              const border = isCore ? gradeColor + '70' : isInner ? '#8b50ff40' : '#00f5ff20';
                              return <div key={i} className="aspect-square rounded-sm" style={{ background: bg, border: `1px solid ${border}` }} />;
                            })}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Right column */}
                  <div className="flex flex-col gap-4 min-h-0">
                    {/* Price chart */}
                    <div className="card p-4">
                      <div className="flex items-center justify-between mb-3">
                        <p className="text-muted text-xs">가격 추이</p>
                        <div className="flex gap-1">
                          {(['3일', '7일', '30일'] as ChartRange[]).map(r => (
                            <button
                              key={r}
                              onClick={() => setChartRange(r)}
                              className="px-2 h-6 rounded-md text-[10px] font-semibold transition-colors"
                              style={{
                                color: chartRange === r ? '#060a14' : '#7788a5',
                                background: chartRange === r ? gradeColor : '#2a3050',
                                border: `1px solid ${chartRange === r ? gradeColor : '#354064'}`,
                              }}
                            >
                              {r}
                            </button>
                          ))}
                        </div>
                      </div>

                      {chartData.length > 0 && (
                        <div className="flex justify-between mb-1">
                          <span className="text-muted text-[9px]">{Math.min(...chartData).toLocaleString()}</span>
                          <span className="text-[9px]" style={{ color: gradeColor }}>{Math.max(...chartData).toLocaleString()} AP</span>
                        </div>
                      )}

                      <LineChart data={chartData} color={gradeColor} />
                    </div>

                    {/* Bid panel + Bid history */}
                    <div className="flex gap-3">
                      <BidPanel
                        auctionId={auctionId}
                        currentBid={currentBid}
                        minBid={minBid}
                        myBid={myBid}
                        ap={ap}
                        bidAmount={bidAmount}
                        bidError={bidError}
                        isOutbid={isOutbid}
                        isHighestBidder={isHighestBidder}
                        isBidding={isBidding}
                        isAuctionEnded={isAuctionEnded}
                        gradeColor={gradeColor}
                        onChangeBidAmount={setBidAmount}
                        onOpenConfirm={() => setShowConfirm(true)}
                      />

                      {/* Bid history */}
                      <div className="flex-1 card overflow-hidden flex flex-col">
                        <div className="bg-elevated px-3 py-2 border-b border-outline">
                          <span className="text-foreground font-semibold text-xs">입찰 이력</span>
                        </div>
                        <div className="flex-1 overflow-y-auto divide-y divide-outline-soft">
                          {bids.slice().reverse().slice(0, 8).map((bid, i) => (
                            <div key={`${bid.bidAt}-${bid.bidderNickname ?? i}`} className="flex items-center justify-between px-3 py-2">
                              <div className="flex items-center gap-1.5">
                                {i === 0 && <div className="w-1.5 h-1.5 bg-gp rounded-full flex-shrink-0" />}
                                <span className="text-foreground text-[11px]">{bid.bidderNickname ?? '시작가'}</span>
                              </div>
                              <div className="flex flex-col items-end">
                                <span className="text-gold font-semibold text-[11px]">{bid.price.toLocaleString()} AP</span>
                                <span className="text-muted text-[9px]">
                                  {new Date(bid.bidAt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}
                                </span>
                              </div>
                            </div>
                          ))}
                          {bids.length === 0 && (
                            <p className="text-center text-muted text-[11px] py-6">입찰 내역 없음</p>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Stats */}
                    <div className="card p-4">
                      <p className="text-muted font-semibold mb-3 text-xs">영토 스탯</p>
                      <div className="grid grid-cols-2 gap-x-4 gap-y-2">
                        {[
                          { label: 'GP 생산', val: `+${territory.baseProductionRate}/분`, color: '#00ff88' },
                          { label: '무적 여부', val: territory.isInvincible ? '무적 상태' : '일반', color: territory.isInvincible ? '#ffd700' : '#7788a5' },
                          { label: '현재 소유자', val: territory.owner?.nickname ?? '없음', color: '#7788a5' },
                          { label: '상태', val: getStatusLabel(territory.status, isMyTerritory), color: getStatusColor(territory.status, isMyTerritory) },
                        ].map(s => (
                          <div key={s.label} className="flex flex-col gap-0.5">
                            <span className="text-muted text-[10px]">{s.label}</span>
                            <span className="text-xs font-semibold" style={{ color: s.color }}>{s.val}</span>
                          </div>
                        ))}
                      </div>
                    </div>

                    <TerritoryChat
                      continentName={territory.continentName}
                      messages={chatMessages}
                      input={chatInput}
                      onChangeInput={setChatInput}
                      onSend={handleSendChat}
                    />
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {showConfirm && territory && (
        <BidConfirmModal
          territoryCoord={{ x: territory.coordX, y: territory.coordY }}
          continentName={territory.continentName}
          bidAmount={bidAmount}
          currentBid={currentBid}
          ap={ap}
          isOutbid={isOutbid}
          isBidding={isBidding}
          gradeColor={gradeColor}
          onCancel={() => setShowConfirm(false)}
          onConfirm={() => void handleBid()}
        />
      )}
    </div>
  );
}
