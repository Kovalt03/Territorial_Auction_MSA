interface Props {
  auctionId: number | null;
  currentBid: number;
  minBid: number;
  myBid: number;
  ap: number;
  bidAmount: number;
  bidError: string | null;
  isOutbid: boolean;
  isHighestBidder: boolean;
  isBidding: boolean;
  isAuctionEnded: boolean;
  gradeColor: string;
  onChangeBidAmount: (v: number) => void;
  onOpenConfirm: () => void;
}

const QUICK_ADD = [500, 1000, 5000];

export function BidPanel({
  auctionId, currentBid, minBid, myBid, ap,
  bidAmount, bidError, isOutbid, isHighestBidder, isBidding, isAuctionEnded, gradeColor,
  onChangeBidAmount, onOpenConfirm,
}: Props) {
  const canBid = !!auctionId && !isAuctionEnded && !isHighestBidder && bidAmount >= minBid && ap >= bidAmount;
  const ctaBg = canBid ? (isOutbid ? '#ff4444' : '#00f5ff') : '#2a3050';
  const ctaText = canBid ? (isOutbid ? '#fff' : '#060a14') : 'var(--color-muted)';
  const ctaBorder = canBid ? (isOutbid ? '#ff4444' : '#00f5ff') : '#354064';

  const helpText = !auctionId ? '현재 경매 없음'
    : isAuctionEnded ? '🔒 경매 종료됨'
    : isHighestBidder ? '✓ 최고 입찰 중'
    : bidAmount < minBid ? `최소 ${minBid.toLocaleString()}`
    : ap < bidAmount ? 'AP 부족'
    : `잔여 ${(ap - bidAmount).toLocaleString()}`;

  const helpColor = !auctionId ? '#7788a5'
    : isAuctionEnded ? '#ff5555'
    : isHighestBidder ? '#00ff88'
    : bidAmount < minBid || ap < bidAmount ? '#ff5555'
    : '#00ff88';

  return (
    <div
      className="flex-1 rounded-xl p-3"
      style={{
        background: isOutbid ? '#1a0a0a' : 'var(--color-panel-deep)',
        border: `2px solid ${isOutbid ? '#ff4444' : gradeColor + '80'}`,
      }}
    >
      {isOutbid && (
        <div className="flex items-center gap-1.5 mb-3 px-2 py-1.5 rounded-lg bg-danger/15 border border-danger/40">
          <span className="text-xs">🔺</span>
          <div className="flex-1 min-w-0">
            <p className="text-danger font-bold text-[11px]">상회 입찰됨!</p>
            <p className="text-muted truncate text-[9px]">
              {myBid.toLocaleString()} → {currentBid.toLocaleString()} AP
            </p>
          </div>
          <button
            onClick={() => onChangeBidAmount(minBid)}
            className="px-2 h-6 rounded font-bold text-[9px] flex-shrink-0 bg-danger text-white"
          >
            재입찰
          </button>
        </div>
      )}

      <p className="font-semibold mb-2 text-[11px]" style={{ color: isOutbid ? '#ff5555' : gradeColor }}>
        {!auctionId ? '경매 없음' : isOutbid ? '🔺 재입찰하기' : '⚡ 입찰하기'}
      </p>

      <div
        className="flex items-center justify-between mb-2 px-2 py-1.5 rounded-lg"
        style={{ background: (isOutbid ? '#ff4444' : gradeColor) + '12', border: `1px solid ${isOutbid ? '#ff4444' : gradeColor}30` }}
      >
        <span className="text-muted text-[10px]">현재가</span>
        <span className="font-bold text-sm" style={{ color: isOutbid ? '#ff5555' : gradeColor }}>
          {currentBid.toLocaleString()} AP
        </span>
      </div>

      <div className="flex items-center gap-1.5 mb-1.5">
        <input
          type="number"
          value={bidAmount}
          onChange={e => onChangeBidAmount(Number(e.target.value))}
          disabled={!auctionId || isAuctionEnded}
          className="flex-1 h-8 bg-outline-soft border border-outline rounded-lg px-2 text-[13px] text-foreground outline-none focus:border-primary transition-colors font-bold disabled:opacity-40"
        />
        <span className="text-muted text-[10px]">AP</span>
      </div>

      <div className="flex gap-1 mb-2">
        {QUICK_ADD.map(add => (
          <button
            key={add}
            onClick={() => onChangeBidAmount(bidAmount + add)}
            disabled={!auctionId || isAuctionEnded}
            className="flex-1 h-6 rounded text-[10px] text-foreground-soft hover:text-white transition-colors disabled:opacity-40 bg-outline-soft border border-outline"
          >
            +{add >= 1000 ? `${add / 1000}K` : add}
          </button>
        ))}
        <button
          onClick={() => onChangeBidAmount(minBid)}
          disabled={!auctionId || isAuctionEnded}
          className="px-1.5 h-6 rounded text-[9px] text-muted hover:text-foreground-soft transition-colors disabled:opacity-40 bg-[#1a2030] border border-elevated"
        >
          초기화
        </button>
      </div>

      {bidError && (
        <p className="text-danger mb-1.5 text-[10px]">⚠ {bidError}</p>
      )}

      <button
        onClick={onOpenConfirm}
        disabled={!canBid || isBidding}
        className="w-full h-9 rounded-xl text-xs font-bold transition-all hover:brightness-110 disabled:opacity-40 disabled:cursor-not-allowed border"
        style={{ background: ctaBg, color: ctaText, borderColor: ctaBorder }}
      >
        {isAuctionEnded ? '🔒 경매 종료' : isBidding ? '처리 중...' : isOutbid ? '🔺 재입찰' : '⚡ 입찰'}
      </button>
      <p className="text-center mt-1 text-[9px]" style={{ color: helpColor }}>
        {helpText}
      </p>
    </div>
  );
}
