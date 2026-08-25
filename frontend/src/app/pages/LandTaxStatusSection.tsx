import { useEffect, useState } from 'react';

import type { LandTaxStatus } from '../types/landTax';

const TAX_BRACKETS = [
  { label: '0개', tax: '면제', min: 0, max: 0 },
  { label: '1~3개', tax: '50 GP', min: 1, max: 3 },
  { label: '4~7개', tax: '150 GP', min: 4, max: 7 },
  { label: '8개+', tax: '400 GP', min: 8, max: Infinity },
];

interface Props {
  status: LandTaxStatus | null;
  isLoading: boolean;
  error: string | null;
}

function formatNextCharge(iso: string) {
  return new Date(iso).toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatCountdown(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return `${h}시간 ${m}분 ${sec}초`;
}

function Skeleton() {
  return <div className="h-10 bg-elevated rounded animate-pulse mx-auto w-20" />;
}

export function LandTaxStatusSection({ status, isLoading, error }: Props) {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  if (error) {
    return (
      <div className="bg-gold/10 border border-gold/25 rounded-xl px-4 py-2.5 mb-5">
        <span className="text-gold text-xs">⚠ {error}</span>
      </div>
    );
  }

  const finalTaxableCount = status ? Math.max(0, status.territoryCount - status.effectiveExemptCount) : 0;
  const nextChargeMs = status ? new Date(status.nextChargeAt).getTime() - now : 0;

  return (
    <>
      <div className="grid grid-cols-3 gap-4 mb-5">
        <div className="card p-5 text-center">
          <p className="text-muted mb-2 text-xs">보유 영토 수</p>
          {isLoading ? <Skeleton /> : <p className="text-gold font-bold text-[32px]">{status?.territoryCount ?? 0}</p>}
          <p className="text-muted mt-3 text-[11px]">면제 {status?.effectiveExemptCount ?? 0}개 · 홈 아일랜드 제외</p>
        </div>

        <div className="card p-5 text-center">
          <p className="text-muted mb-2 text-xs">일일 토지세</p>
          {isLoading ? (
            <Skeleton />
          ) : status && status.finalDailyGP === 0 ? (
            <p className="text-primary font-bold text-[26px] mt-1">면제 ✦</p>
          ) : (
            <p className="text-gp font-bold text-[32px]">
              {(status?.finalDailyGP ?? 0).toLocaleString()}
              <span className="text-sm text-muted ml-1">GP/일</span>
            </p>
          )}
          <p className="text-muted mt-3 text-[11px]">과세 영토 {finalTaxableCount}개</p>
        </div>

        <div className="card p-5 text-center">
          <p className="text-muted mb-2 text-xs">다음 납부</p>
          {isLoading ? (
            <Skeleton />
          ) : (
            <>
              <p className="text-foreground font-bold text-base mt-1">
                {status ? formatNextCharge(status.nextChargeAt) : '-'}
              </p>
              {status && <p className="text-primary mt-2 text-[12px]">⏱ {formatCountdown(nextChargeMs)}</p>}
            </>
          )}
        </div>
      </div>

      {status && status.seasonPassExemptBonus > 0 && (
        <div className="bg-gold/10 border border-gold/30 rounded-xl px-4 py-3 mb-5">
          <span className="text-gold text-[13px]">
            ⭐ 시즌패스 혜택 적용 중 — 면제 구간 +{status.seasonPassExemptBonus}개 추가
          </span>
        </div>
      )}

      <div className="card overflow-hidden mb-5">
        <div className="bg-elevated px-4 py-2.5 border-b border-outline">
          <span className="text-foreground font-semibold text-[13px]">토지세 누진 구조</span>
        </div>
        <div className="p-4 space-y-1.5">
          {TAX_BRACKETS.map(bracket => {
            const isCurrent = !!status && finalTaxableCount >= bracket.min && finalTaxableCount <= bracket.max;
            return (
              <div
                key={bracket.label}
                className={`flex justify-between items-center px-3 py-2 rounded-lg border ${isCurrent ? 'bg-primary/10 border-primary' : 'bg-panel-deep border-transparent'}`}
              >
                <span className={`text-xs ${isCurrent ? 'text-primary font-semibold' : 'text-muted'}`}>
                  과세 영토 {bracket.label}
                  {isCurrent && ' · 현재'}
                </span>
                <span className={`text-xs ${isCurrent ? 'text-primary font-semibold' : 'text-foreground'}`}>{bracket.tax}</span>
              </div>
            );
          })}
        </div>
      </div>
    </>
  );
}
