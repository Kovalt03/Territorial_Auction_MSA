import type { StatusFilter, GradeFilter } from '../types/admin';

const STATUSES: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'BIDDING', label: '경매중' },
  { value: 'OCCUPIED', label: '점유' },
  { value: 'IDLE', label: '유휴' },
];
const GRADES: GradeFilter[] = ['ALL', 'S', 'A', 'B', 'C', 'D'];

interface Props {
  status: StatusFilter;
  onStatus: (s: StatusFilter) => void;
  grade: GradeFilter;
  onGrade: (g: GradeFilter) => void;
  disabledOnly: boolean;
  onDisabledOnly: (b: boolean) => void;
}

const chip = (active: boolean, activeCls = 'border-primary text-primary') =>
  `px-2.5 h-7 rounded-md text-[11px] font-semibold border ${
    active ? activeCls : 'border-outline text-muted hover:text-foreground-soft'
  }`;

export function TerritoryFilters({ status, onStatus, grade, onGrade, disabledOnly, onDisabledOnly }: Props) {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mb-4">
      <div className="flex items-center gap-1">
        <span className="text-[11px] text-dim mr-1">상태</span>
        {STATUSES.map(s => (
          <button key={s.value} onClick={() => onStatus(s.value)} className={chip(status === s.value)}>
            {s.label}
          </button>
        ))}
      </div>

      <div className="flex items-center gap-1">
        <span className="text-[11px] text-dim mr-1">등급</span>
        {GRADES.map(g => (
          <button key={g} onClick={() => onGrade(g)} className={chip(grade === g)}>
            {g === 'ALL' ? '전체' : g}
          </button>
        ))}
      </div>

      <button onClick={() => onDisabledOnly(!disabledOnly)} className={chip(disabledOnly, 'border-danger text-danger')}>
        ⏸ 경매중지만
      </button>
    </div>
  );
}
