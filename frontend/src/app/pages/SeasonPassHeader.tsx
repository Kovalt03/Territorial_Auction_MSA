import type { SeasonProgress } from '../types/season';

const MAX_LEVEL = 30;

interface Props {
  progress: SeasonProgress;
}

function daysUntil(iso: string): number {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86400000));
}

export function SeasonPassHeader({ progress }: Props) {
  const isMax = progress.currentLevel >= MAX_LEVEL;
  const xpPct = isMax
    ? 100
    : Math.min(100, Math.round((progress.currentXp / progress.nextLevelXp) * 100));
  const endsIn = daysUntil(progress.seasonEndsAt);

  return (
    <div className="card p-5 mb-5">
      <div className="flex items-center justify-between mb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-2xl">⭐</span>
            <h2 className="text-gold font-bold text-xl">{progress.seasonName} 패스</h2>
            <span
              className={`px-2 py-0.5 rounded text-[10px] font-bold ${progress.passType === 'PREMIUM' ? 'bg-gold/20 text-gold border border-gold/40' : 'bg-elevated text-muted'}`}
            >
              {progress.passType === 'PREMIUM' ? '프리미엄' : '무료'}
            </span>
          </div>
          <p className="text-muted text-[11px] mt-1">시즌 종료까지 D-{endsIn}</p>
        </div>
        <p className="text-gold font-bold text-[28px] leading-none">Lv.{progress.currentLevel}</p>
      </div>
      <div className="flex items-center gap-2">
        <div className="flex-1 h-3 bg-elevated rounded-full overflow-hidden">
          <div className="h-full bg-gold rounded-full transition-all" style={{ width: `${xpPct}%` }} />
        </div>
        <span className="text-muted text-[11px] tabular-nums flex-shrink-0">
          {isMax ? 'MAX' : `${progress.currentXp} / ${progress.nextLevelXp} XP`}
        </span>
      </div>
    </div>
  );
}
