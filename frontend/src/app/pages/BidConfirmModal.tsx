interface Props {
  territoryCoord: { x: number; y: number };
  continentName: string;
  bidAmount: number;
  currentBid: number;
  ap: number;
  isOutbid: boolean;
  isBidding: boolean;
  gradeColor: string;
  onCancel: () => void;
  onConfirm: () => void;
}

export function BidConfirmModal({
  territoryCoord, continentName,
  bidAmount, currentBid, ap,
  isOutbid, isBidding, gradeColor,
  onCancel, onConfirm,
}: Props) {
  return (
    <div className="modal-overlay">
      <div
        className="bg-panel border-2 rounded-2xl p-8 max-w-sm mx-4 text-center"
        style={{ borderColor: isOutbid ? '#ff4444' : gradeColor }}
      >
        <span className="text-[40px]">{isOutbid ? '🔺' : '⚡'}</span>
        <h3
          className="font-bold text-xl mt-3 mb-2"
          style={{ color: isOutbid ? '#ff5555' : gradeColor }}
        >
          {isOutbid ? '재입찰 확인' : '입찰 확인'}
        </h3>
        <p className="text-muted mb-5 text-[13px]">
          영토 ({territoryCoord.x}, {territoryCoord.y}) · {continentName}
        </p>
        <div className="bg-elevated rounded-xl py-4 mb-6 space-y-2">
          <div className="flex justify-between px-4">
            <span className="text-muted text-[13px]">입찰 금액</span>
            <span className="font-bold text-base" style={{ color: isOutbid ? '#ff5555' : gradeColor }}>
              {bidAmount.toLocaleString()} AP
            </span>
          </div>
          <div className="flex justify-between px-4">
            <span className="text-muted text-[13px]">현재가 대비</span>
            <span className="text-gp text-[13px]">+{(bidAmount - currentBid).toLocaleString()} AP</span>
          </div>
          <div className="flex justify-between px-4">
            <span className="text-muted text-[13px]">입찰 후 잔여</span>
            <span className="text-foreground text-[13px]">{(ap - bidAmount).toLocaleString()} AP</span>
          </div>
        </div>
        <div className="flex gap-3">
          <button onClick={onCancel} className="btn-cancel">취소</button>
          <button
            onClick={onConfirm}
            disabled={isBidding}
            className="flex-1 h-11 rounded-xl font-bold text-sm disabled:opacity-50"
            style={{ background: isOutbid ? '#ff4444' : gradeColor, color: isOutbid ? '#fff' : '#0a0e1a' }}
          >
            {isBidding ? '처리 중...' : isOutbid ? '재입찰하기' : '입찰하기'}
          </button>
        </div>
      </div>
    </div>
  );
}
