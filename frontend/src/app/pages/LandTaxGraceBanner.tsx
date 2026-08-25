import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import type { LandTaxLogItem } from '../types/landTax';

// 백엔드 LandTaxPolicy.GRACE_PERIOD_HOURS 와 동일 — 미납 후 강제 경매 전환까지의 유예 시간
const GRACE_PERIOD_HOURS = 24;

interface Props {
  latestLog: LandTaxLogItem | null;
}

function formatRemaining(ms: number) {
  const totalMin = Math.max(0, Math.floor(ms / 60000));
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
}

export function LandTaxGraceBanner({ latestLog }: Props) {
  const navigate = useNavigate();
  const [now, setNow] = useState(Date.now());

  const isInGrace = latestLog?.status === 'FAILED';

  useEffect(() => {
    if (!isInGrace) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [isInGrace]);

  if (!latestLog) return null;

  if (latestLog.status === 'EVICTED') {
    return (
      <div className="bg-[#ff8c0020] border border-[#ff8c00] rounded-xl px-4 py-3 mb-4 flex items-center justify-between gap-3">
        <div>
          <p className="text-[#ff8c00] font-semibold text-sm">⚠ 토지세 미납으로 영토가 강제 경매 전환됐습니다</p>
          <p className="text-muted text-[11px] mt-0.5">알림에서 전환된 영토를 확인하세요.</p>
        </div>
        <button
          onClick={() => navigate('/app/notifications')}
          className="flex-shrink-0 h-8 px-3 border border-[#ff8c00] rounded-lg text-[#ff8c00] text-xs hover:bg-[#ff8c0010] transition-colors"
        >
          알림 보기
        </button>
      </div>
    );
  }

  if (isInGrace) {
    const graceExpiresAt = new Date(latestLog.chargedAt).getTime() + GRACE_PERIOD_HOURS * 3600 * 1000;
    const remaining = graceExpiresAt - now;
    const isExpired = remaining <= 0;
    return (
      <div className="bg-danger/10 border border-danger/40 rounded-xl px-4 py-3 mb-4">
        <p className="text-danger font-semibold text-sm">⚠ 토지세 납부에 실패했습니다</p>
        <p className="text-muted text-[11px] mt-0.5">
          {isExpired ? (
            '유예 기간이 만료되어 곧 영토가 강제 경매 전환됩니다.'
          ) : (
            <>
              GP를 확보하지 않으면 <span className="text-danger font-semibold">{formatRemaining(remaining)}</span> 후 가장 낮은 등급부터 영토가 강제 경매 전환됩니다.
            </>
          )}
        </p>
      </div>
    );
  }

  return null;
}
