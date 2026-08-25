import { useEffect, useRef } from 'react';

import type { SeasonRewardItem, RewardTrack } from '../types/season';

interface CellProps {
  reward: SeasonRewardItem | undefined;
  hasPass: boolean;
  claimingId: number | null;
  onClaim: (rewardId: number) => void;
}

function RewardCell({ reward, hasPass, claimingId, onClaim }: CellProps) {
  if (!reward) {
    return <div className="w-32 h-24 rounded-xl border border-dashed border-outline/40" />;
  }
  const isPremium = reward.track === 'PREMIUM';
  const color = isPremium ? '#ffd700' : '#00f5ff';
  const premiumLocked = isPremium && !hasPass;

  let status = 'Lv.미달';
  if (reward.isClaimed) status = '✓ 완료';
  else if (reward.canClaim) status = '수령하기';
  else if (premiumLocked) status = '🔒 패스 필요';

  const active = reward.canClaim && claimingId !== reward.rewardId;

  return (
    <button
      onClick={() => active && onClaim(reward.rewardId)}
      disabled={!active}
      className="w-32 h-24 rounded-xl border p-2.5 flex flex-col justify-between text-left transition-all disabled:cursor-default"
      style={{
        borderColor: reward.canClaim ? color : '#354064',
        background: reward.isClaimed ? color + '20' : reward.canClaim ? color + '12' : '#1a1f35',
        opacity: reward.isClaimed || (!reward.canClaim && !premiumLocked) ? 0.6 : 1,
      }}
    >
      <p
        className="text-[12px] font-medium leading-snug line-clamp-2"
        style={{ color: reward.canClaim || reward.isClaimed ? color : '#8892b0' }}
      >
        {reward.rewardName}
      </p>
      <span className="text-[11px] font-bold" style={{ color: reward.canClaim ? color : '#7788a5' }}>
        {status}
      </span>
    </button>
  );
}

interface Props {
  rewards: SeasonRewardItem[];
  currentLevel: number;
  hasPass: boolean;
  claimingId: number | null;
  onClaim: (rewardId: number) => void;
}

export function SeasonRewardTrack({ rewards, currentLevel, hasPass, claimingId, onClaim }: Props) {
  const levels = [...new Set(rewards.map(r => r.level))].sort((a, b) => a - b);
  const find = (level: number, track: RewardTrack) =>
    rewards.find(r => r.level === level && r.track === track);

  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    // 세로 휠 입력을 가로 스크롤로 변환 — onWheel은 passive라 preventDefault 불가
    const handleWheel = (e: WheelEvent) => {
      if (e.deltaY === 0 || el.scrollWidth <= el.clientWidth) return;
      e.preventDefault();
      el.scrollLeft += e.deltaY;
    };
    el.addEventListener('wheel', handleWheel, { passive: false });
    return () => el.removeEventListener('wheel', handleWheel);
  }, []);

  return (
    <div className="card overflow-hidden mb-5">
      <div className="bg-elevated px-4 py-2.5 border-b border-outline flex items-center justify-between">
        <span className="text-foreground font-semibold text-[13px]">레벨 보상 트랙</span>
        <span className="text-muted text-[10px]">🟡 프리미엄 · 🔵 무료</span>
      </div>
      <div className="flex">
        {/* 트랙 라벨 (고정) */}
        <div className="flex-shrink-0 flex flex-col gap-1.5 py-4 pl-4 pr-2">
          <span className="h-24 flex items-center text-gold font-bold text-[11px]">🟡 프리미엄</span>
          <span className="h-6 flex items-center text-muted text-[10px]">레벨</span>
          <span className="h-24 flex items-center text-primary font-bold text-[11px]">🔵 무료</span>
        </div>
        {/* 스크롤 영역 */}
        <div ref={scrollRef} className="flex-1 overflow-x-auto py-4 pr-4">
          <div className="flex gap-3 min-w-max">
            {levels.map(level => (
              <div key={level} className="flex flex-col items-center gap-1.5">
                <RewardCell
                  reward={find(level, 'PREMIUM')}
                  hasPass={hasPass}
                  claimingId={claimingId}
                  onClaim={onClaim}
                />
                <div
                  className={`h-6 flex items-center px-2.5 rounded-full text-[10px] font-bold ${currentLevel >= level ? 'bg-gold text-surface' : 'bg-elevated text-muted'}`}
                >
                  Lv.{level}
                </div>
                <RewardCell
                  reward={find(level, 'FREE')}
                  hasPass={hasPass}
                  claimingId={claimingId}
                  onClaim={onClaim}
                />
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
