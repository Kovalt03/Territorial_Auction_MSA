const BENEFITS = [
  { icon: '💎', title: '섬 GP +50%', desc: '홈 아일랜드 GP 생산량 50% 증가' },
  { icon: '👷', title: '건축 장인 +1', desc: '동시에 지을 수 있는 건물 수 1 증가' },
  { icon: '🔨', title: '건설 시간 -20%', desc: '건설·업그레이드 시간 20% 감소 (15레벨 보상으로 +10%)' },
  { icon: '🏛', title: '토지세 면제 +2개', desc: '토지세 면제 구간 2개 추가' },
];

interface Props {
  hasPass: boolean;
}

export function SeasonBenefits({ hasPass }: Props) {
  return (
    <div className="card overflow-hidden mb-5">
      <div className="bg-elevated px-4 py-2.5 border-b border-outline">
        <span className="text-foreground font-semibold text-[13px]">프리미엄 기본 혜택</span>
      </div>
      <div className="p-4 space-y-2">
        {BENEFITS.map(b => (
          <div key={b.title} className="flex items-center gap-3 bg-panel-deep rounded-xl p-3">
            <span className="text-xl">{b.icon}</span>
            <div className="flex-1">
              <p className="text-gold font-semibold text-[13px]">{b.title}</p>
              <p className="text-muted text-[11px]">{b.desc}</p>
            </div>
            {hasPass && (
              <span className="text-gp text-[11px] border border-gp/40 bg-gp/10 rounded px-2 py-0.5">
                ✓ 활성
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
