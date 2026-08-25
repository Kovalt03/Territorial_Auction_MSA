import { UNIT_LABELS } from './islandGrid';

import type { ResearchStatus, UnitResearchInfo } from '../types/military';

interface Props {
  research: ResearchStatus | null;
  isBusy: boolean;
  error: string | null;
  onResearch: (unitTypeId: number) => void;
}

function remaining(completeAt: string): string {
  const secs = Math.max(0, Math.floor((new Date(completeAt).getTime() - Date.now()) / 1000));
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
}

function meta(u: UnitResearchInfo) {
  const fb = UNIT_LABELS[u.name] ?? { label: u.name, icon: '⚔', color: '#e0e8ff' };
  return { label: u.displayName ?? fb.label, icon: u.icon ?? fb.icon, color: u.colorHex ?? fb.color };
}

export function IslandResearchPanel({ research, isBusy, error, onResearch }: Props) {
  const labLevel = research?.researchLabLevel ?? 0;
  // 연구는 계정당 한 번에 하나 — 진행 중인 연구가 있으면 다른 연구를 시작할 수 없다.
  const anyResearching = (research?.units ?? []).some(u => !!u.pendingLevel && !!u.researchCompleteAt);

  return (
    <div className="bg-panel-deep rounded-xl p-3">
      <div className="flex items-center justify-between mb-2">
        <p className="text-[#ff44cc] font-semibold text-xs">🔬 연구</p>
        <span className="text-muted text-[10px]">
          연구소 Lv.{labLevel} · 연구 상한 Lv.{labLevel + 1}
        </span>
      </div>

      {labLevel === 0 && (
        <p className="text-muted text-[10px] mb-2">
          연구소를 지으면 유닛 레벨을 연구해 강화할 수 있습니다.
        </p>
      )}
      {error && <p className="text-danger text-[10px] mb-2">⚠ {error}</p>}

      <div className="space-y-2">
        {research?.units.map(u => {
          const m = meta(u);
          const isResearching = !!u.pendingLevel && !!u.researchCompleteAt;
          const atMax = u.researchedLevel >= u.maxLevel;
          const canResearch =
            !isResearching && !atMax && !anyResearching && labLevel >= u.researchedLevel; // 목표=현재+1, 필요 연구소=목표-1
          return (
            <div key={u.unitTypeId} className="flex items-center gap-2">
              <span className="text-base">{m.icon}</span>
              <div className="flex-1 min-w-0">
                <div className="flex justify-between">
                  <span className="text-[11px]" style={{ color: m.color }}>{m.label}</span>
                  <span className="text-muted text-[10px]">
                    Lv.{u.researchedLevel}/{u.maxLevel}
                  </span>
                </div>
                {isResearching ? (
                  <p className="text-gold text-[10px]">
                    🔬 Lv.{u.pendingLevel} 연구 중 — {remaining(u.researchCompleteAt!)} 남음
                  </p>
                ) : atMax ? (
                  <p className="text-muted text-[10px]">최대 레벨</p>
                ) : (
                  <p className="text-muted text-[10px]">
                    다음 Lv.{u.researchedLevel + 1} · 금고 {u.nextCostGp?.toLocaleString()} GP
                  </p>
                )}
              </div>
              {!isResearching && !atMax && (
                <button
                  onClick={() => onResearch(u.unitTypeId)}
                  disabled={isBusy || !canResearch}
                  title={anyResearching ? '다른 연구가 진행 중입니다 (한 번에 하나)' : !canResearch ? '연구소 레벨이 부족합니다' : ''}
                  className="text-[10px] px-2 py-1 rounded-lg border border-[#ff44cc]/50 text-[#ff44cc] hover:bg-[#ff44cc]/10 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  연구
                </button>
              )}
            </div>
          );
        })}
        {!research && <p className="text-muted text-[10px]">불러오는 중...</p>}
      </div>
    </div>
  );
}
