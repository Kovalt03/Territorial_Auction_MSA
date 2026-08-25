import { Fragment } from 'react';

import type { AdminTerritory, StatusFilter, GradeFilter } from '../types/admin';

const GRADE_COLOR: Record<string, string> = {
  S: 'var(--color-gold)',
  A: 'var(--color-primary)',
  B: 'var(--color-secondary)',
  C: 'var(--color-gp)',
  D: 'var(--color-muted)',
};
const STATUS_LABEL: Record<string, string> = { BIDDING: '경매중', OCCUPIED: '점유', IDLE: '유휴' };
const GRADES = ['S', 'A', 'B', 'C', 'D'];
const CELL = 46;

interface Props {
  territories: AdminTerritory[];
  selectedTerritoryId: number | null;
  onSelect: (id: number) => void;
  statusFilter: StatusFilter;
  gradeFilter: GradeFilter;
  disabledOnly: boolean;
  selectMode?: boolean;
  selectedIds?: Set<number>;
  onToggleSelect?: (id: number) => void;
}

export function TerritoryGrid({
  territories, selectedTerritoryId, onSelect, statusFilter, gradeFilter, disabledOnly,
  selectMode = false, selectedIds, onToggleSelect,
}: Props) {
  if (territories.length === 0) return <p className="text-muted text-sm">영토가 없습니다.</p>;

  const xs = territories.map(t => t.coordX);
  const ys = territories.map(t => t.coordY);
  const minX = Math.min(...xs);
  const minY = Math.min(...ys);
  const cols = Math.max(...xs) - minX + 1;
  const rows = Math.max(...ys) - minY + 1;

  const byCoord = new Map<string, AdminTerritory>();
  territories.forEach(t => byCoord.set(`${t.coordX},${t.coordY}`, t));

  const matches = (t: AdminTerritory) =>
    (statusFilter === 'ALL' || t.status === statusFilter) &&
    (gradeFilter === 'ALL' || t.grade === gradeFilter) &&
    (!disabledOnly || !t.auctionEnabled);

  return (
    <div className="inline-block">
      <div className="inline-grid gap-0.5"
        style={{ gridTemplateColumns: `28px repeat(${cols}, ${CELL}px)`, gridTemplateRows: `18px repeat(${rows}, ${CELL}px)` }}>
        <div />
        {Array.from({ length: cols }, (_, c) => (
          <div key={`x${c}`} className="text-[10px] text-dim text-center self-center">{minX + c}</div>
        ))}
        {Array.from({ length: rows }, (_, r) => {
          const y = minY + r;
          return (
            <Fragment key={`row${y}`}>
              <div className="text-[10px] text-dim text-right pr-1 self-center">{y}</div>
              {Array.from({ length: cols }, (_, c) => {
                const t = byCoord.get(`${minX + c},${y}`);
                if (!t) return <div key={`c${c}`} />;
                const isSel = selectMode
                  ? !!selectedIds?.has(t.territoryId)
                  : t.territoryId === selectedTerritoryId;
                return (
                  <Cell key={`c${c}`} territory={t} selected={isSel} multi={selectMode}
                    dimmed={!matches(t)}
                    onClick={() => (selectMode ? onToggleSelect?.(t.territoryId) : onSelect(t.territoryId))} />
                );
              })}
            </Fragment>
          );
        })}
      </div>
      <Legend />
    </div>
  );
}

interface CellProps {
  territory: AdminTerritory;
  selected: boolean;
  multi: boolean;
  dimmed: boolean;
  onClick: () => void;
}

function Cell({ territory: t, selected, multi, dimmed, onClick }: CellProps) {
  const enabled = t.auctionEnabled;
  const color = GRADE_COLOR[t.grade] ?? 'var(--color-foreground)';
  return (
    <button onClick={onClick}
      title={`(${t.coordX}, ${t.coordY}) · ${t.grade}급 · ${STATUS_LABEL[t.status] ?? t.status}${enabled ? '' : ' · 경매중지'}`}
      style={{
        width: CELL,
        height: CELL,
        background: enabled
          ? `color-mix(in srgb, ${color} 18%, var(--color-panel-deep))`
          : 'var(--color-panel-deep)',
        outline: selected ? '2px solid var(--color-primary)' : 'none',
        outlineOffset: selected ? 1 : undefined,
        opacity: dimmed ? 0.1 : 1,
      }}
      className="relative rounded-md flex flex-col overflow-hidden border border-outline hover:brightness-150">
      <span className="flex-1 flex items-center justify-center font-black text-[16px]"
        style={{ color: enabled ? color : 'var(--color-muted)' }}>
        {t.grade}
      </span>
      {t.status !== 'IDLE' && (
        <span className="text-[8px] font-bold text-center leading-[14px] h-[14px]"
          style={{
            background: t.status === 'BIDDING' ? 'var(--color-flare)' : 'var(--color-dim)',
            color: 'var(--color-surface)',
          }}>
          {STATUS_LABEL[t.status]}
        </span>
      )}
      {multi && selected && (
        <span className="absolute top-0 right-0.5 text-[11px] font-black leading-none" style={{ color: 'var(--color-primary)' }}>✓</span>
      )}
      {!enabled && (
        <span className="absolute top-0.5 left-1 text-[11px] leading-none" style={{ color: 'var(--color-danger)' }}>⏸</span>
      )}
    </button>
  );
}

function Legend() {
  return (
    <div className="mt-4 flex flex-wrap gap-x-4 gap-y-2 text-[10px] text-muted items-center">
      <span className="flex items-center gap-1.5">
        등급
        {GRADES.map(g => (
          <span key={g} className="inline-flex items-center justify-center w-5 h-5 rounded font-black border border-outline bg-panel"
            style={{ color: GRADE_COLOR[g] }}>{g}</span>
        ))}
      </span>
      <span className="flex items-center gap-1">
        <span className="inline-block px-1 rounded text-[8px] font-bold text-surface" style={{ background: 'var(--color-flare)' }}>경매중</span>
      </span>
      <span className="flex items-center gap-1">
        <span className="inline-block px-1 rounded text-[8px] font-bold text-surface" style={{ background: 'var(--color-dim)' }}>점유</span>
      </span>
      <span className="flex items-center gap-1"><span className="text-danger">⏸</span>경매중지</span>
      <span className="flex items-center gap-1">
        <span className="inline-block w-4 h-4 rounded bg-panel" style={{ outline: '2px solid var(--color-primary)', outlineOffset: 1 }} />선택
      </span>
    </div>
  );
}
