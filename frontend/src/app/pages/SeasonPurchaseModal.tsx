import { Button } from '../components/Button';

interface Props {
  ap: number;
  cost: number;
  isProcessing: boolean;
  error: string | null;
  onClose: () => void;
  onConfirm: () => void;
}

export function SeasonPurchaseModal({ ap, cost, isProcessing, error, onClose, onConfirm }: Props) {
  return (
    <div className="modal-overlay">
      <div className="bg-panel border-2 border-gold rounded-2xl p-8 max-w-sm mx-4 text-center">
        <span className="text-5xl">⭐</span>
        <h3 className="text-gold font-bold text-xl mt-3 mb-2">프리미엄 패스 구매</h3>
        <div className="bg-elevated rounded-xl py-4 mb-3">
          <p className="text-muted text-xs">차감 AP</p>
          <p className="text-gold font-bold text-[28px]">{cost.toLocaleString()} AP</p>
          <p className="text-muted text-[11px]">
            잔여: {ap.toLocaleString()} → {(ap - cost).toLocaleString()} AP
          </p>
        </div>
        <p className="text-muted text-[11px] mb-4">
          구매 시 현재 시즌 종료까지 프리미엄 보상·혜택이 적용됩니다.
        </p>
        {error && <p className="text-danger mb-3 text-xs">⚠ {error}</p>}
        <div className="flex gap-3">
          <button onClick={onClose} className="btn-cancel">
            취소
          </button>
          <Button onClick={onConfirm} disabled={ap < cost || isProcessing} className="flex-1">
            {isProcessing ? '처리 중...' : '구매하기'}
          </Button>
        </div>
      </div>
    </div>
  );
}
