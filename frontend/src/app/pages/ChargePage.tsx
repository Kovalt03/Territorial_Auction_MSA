import { useState } from 'react';
import { useNavigate } from 'react-router';

import { useApp } from '../context/AppContext';
import { chargeAp } from '../api/user';

import { GNB } from '../components/GNB';
import { Button } from '../components/Button';

const packages = [
  { id: 0, ap: 5000, price: 5000, discount: 0, color: '#e0e8ff', borderColor: '#e0e8ff' },
  { id: 1, ap: 10000, price: 9000, originalPrice: 10000, discount: 10, color: '#00f5ff', borderColor: '#00f5ff', label: '10% 할인' },
  { id: 2, ap: 30000, price: 24000, originalPrice: 30000, discount: 20, color: '#ffd700', borderColor: '#ffd700', label: 'BEST 20% 할인', best: true },
  { id: 3, ap: 50000, price: 35000, originalPrice: 50000, discount: 30, color: '#8b50ff', borderColor: '#8b50ff', label: 'VIP 30% 할인' },
];

const payMethods = [
  { id: 'card', icon: '💳', label: '신용카드 / 체크카드' },
  { id: 'kakao', icon: '💛', label: '카카오페이' },
  { id: 'naver', icon: '💚', label: '네이버페이' },
  { id: 'toss', icon: '💙', label: '토스페이' },
];

export function ChargePage() {
  const navigate = useNavigate();
  const { syncAP } = useApp();
  const [selectedPkg, setSelectedPkg] = useState(2);
  const [selectedPay, setSelectedPay] = useState('card');
  const [isProcessing, setIsProcessing] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [chargeResult, setChargeResult] = useState<{ availableAP: number; chargedAmount: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pkg = packages[selectedPkg];

  const handlePay = async () => {
    setIsProcessing(true);
    setError(null);
    const orderId = `order-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const paymentKey = `mock-${selectedPay}-${orderId}`;
    try {
      const result = await chargeAp(pkg.ap, paymentKey, orderId);
      syncAP(result.availableAP);
      setChargeResult({ availableAP: result.availableAP, chargedAmount: result.chargedAmount });
      setIsSuccess(true);
    } catch {
      setError('결제에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div className="page-root">
      <GNB />

      <div className="page-body">
        <h1 className="text-foreground font-bold mb-1 text-2xl">💎  AP (Auction Point) 충전</h1>
        <p className="text-muted mb-6 text-sm">경매 입찰, 아이템 구매에 사용하는 프리미엄 포인트</p>

        {error && (
          <div className="bg-danger/10 border border-danger/25 rounded-xl px-4 py-2.5 mb-4">
            <span className="text-danger text-[13px]">⚠ {error}</span>
          </div>
        )}

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          {packages.map((p) => (
            <button
              key={p.id}
              onClick={() => setSelectedPkg(p.id)}
              className="relative bg-panel rounded-2xl p-4 text-left transition-all hover:scale-105"
              style={{
                border: `${p.best ? 2 : 1}px solid ${selectedPkg === p.id ? p.borderColor : '#354064'}`,
                boxShadow: selectedPkg === p.id ? `0 0 20px ${p.color}30` : undefined,
              }}
            >
              {p.label && (
                <div className="mb-3 h-6 rounded-xl px-3 flex items-center w-fit" style={{ background: p.color }}>
                  <span className="text-surface font-bold text-xs">{p.label}</span>
                </div>
              )}
              {!p.label && <div className="h-6 mb-3" />}
              <p className="font-bold text-[26px]" style={{ color: p.color }}>{p.ap.toLocaleString()} AP</p>
              {p.originalPrice && (
                <p className="text-muted line-through text-[13px]">₩{p.originalPrice.toLocaleString()}</p>
              )}
              <p className="text-foreground font-semibold text-xl">₩{p.price.toLocaleString()}</p>
              <div
                className="mt-4 h-11 rounded-xl flex items-center justify-center font-semibold text-sm"
                style={{
                  background: selectedPkg === p.id ? p.color : '#2a3050',
                  border: `1px solid ${selectedPkg === p.id ? p.color : '#354064'}`,
                  color: selectedPkg === p.id ? '#0a0e1a' : p.color,
                }}
              >
                {selectedPkg === p.id ? '✓ 선택됨' : '선택하기'}
              </div>
            </button>
          ))}
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="card overflow-hidden">
            <div className="bg-elevated px-4 py-2.5 border-b-2 border-primary">
              <span className="text-foreground font-semibold text-[13px]">결제 수단 선택</span>
            </div>
            <div className="p-4 space-y-3">
              {payMethods.map(pm => (
                <button
                  key={pm.id}
                  onClick={() => setSelectedPay(pm.id)}
                  className={`w-full h-12 rounded-xl flex items-center px-4 gap-3 transition-all bg-elevated border ${selectedPay === pm.id ? 'border-primary' : 'border-outline'}`}
                >
                  <span className="text-lg">{pm.icon}</span>
                  <span className="text-foreground text-sm">{pm.label}</span>
                  {selectedPay === pm.id && (
                    <div className="ml-auto w-6 h-6 bg-primary rounded-xl flex items-center justify-center">
                      <span className="text-surface font-bold text-xs">✓</span>
                    </div>
                  )}
                </button>
              ))}
            </div>
          </div>

          <div className="card overflow-hidden">
            <div className="bg-elevated px-4 py-2.5 border-b-2 border-primary">
              <span className="text-foreground font-semibold text-[13px]">결제 요약</span>
            </div>
            <div className="p-5">
              <p className="text-muted mb-1 text-xs">선택한 상품</p>
              <p className="text-foreground font-bold mb-4 text-lg">{pkg.ap.toLocaleString()} AP 패키지</p>
              <div className="h-px bg-outline mb-4" />
              <div className="space-y-3 mb-4">
                {pkg.originalPrice && (
                  <div className="flex justify-between">
                    <span className="text-muted text-[13px]">정가</span>
                    <span className="text-foreground text-[13px]">₩{pkg.originalPrice.toLocaleString()}</span>
                  </div>
                )}
                {pkg.discount > 0 && (
                  <div className="flex justify-between">
                    <span className="text-muted text-[13px]">할인</span>
                    <span className="text-foreground text-[13px]">−₩{(pkg.originalPrice! - pkg.price).toLocaleString()} ({pkg.discount}%)</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-muted text-[13px]">최종 결제액</span>
                  <span className="text-gold font-bold text-[13px]">₩{pkg.price.toLocaleString()}</span>
                </div>
              </div>
              <div className="h-px bg-outline mb-4" />
              <div className="flex justify-between items-center mb-5">
                <span className="text-muted text-[11px]">받게 될 AP</span>
                <span className="text-primary font-bold text-[22px]">{pkg.ap.toLocaleString()} AP</span>
              </div>
              <Button
                onClick={handlePay}
                disabled={isProcessing}
                size="xl"
                fullWidth
              >
                {isProcessing ? '처리 중...' : `₩${pkg.price.toLocaleString()} 결제하기`}
              </Button>
            </div>
          </div>
        </div>
      </div>

      {isSuccess && chargeResult && (
        <div className="modal-overlay">
          <div className="bg-panel border-2 border-primary rounded-2xl p-8 text-center max-w-sm mx-4">
            <div className="text-5xl mb-4">💎</div>
            <h3 className="text-primary font-bold text-xl mb-2">충전 완료!</h3>
            <div className="bg-elevated rounded-xl py-4 px-6 mb-3">
              <p className="text-muted text-xs">충전 완료</p>
              <p className="text-primary font-bold text-[28px]">+{chargeResult.chargedAmount.toLocaleString()} AP</p>
            </div>
            <div className="bg-elevated rounded-xl py-3 px-6 mb-6">
              <p className="text-muted text-xs">현재 보유 AP</p>
              <p className="text-gold font-bold text-[22px]">{chargeResult.availableAP.toLocaleString()} AP</p>
            </div>
            <Button
              onClick={() => { setIsSuccess(false); navigate('/app/map'); }}
              size="lg"
              fullWidth
            >
              확인
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
