import { useEffect, useState } from 'react';

import { changeTerritoryGrade, changeTerritoryAuction, forceStartAuction } from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminTerritory } from '../types/admin';

const GRADES = ['S', 'A', 'B', 'C', 'D'];
const GRADE_COLOR: Record<string, string> = {
  S: '#ffd700', A: '#00f5ff', B: '#8b50ff', C: '#00ff88', D: '#7788a5',
};

interface Props {
  territory: AdminTerritory;
  onChanged: () => void;
}

export function TerritoryEditPanel({ territory, onChanged }: Props) {
  const [grade, setGrade] = useState(territory.grade);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isToggling, setIsToggling] = useState(false);
  const [isStarting, setIsStarting] = useState(false);

  const isIdle = territory.status === 'IDLE';
  const isOccupied = territory.status === 'OCCUPIED';

  useEffect(() => {
    setGrade(territory.grade);
    setReason('');
    setError(null);
  }, [territory.territoryId]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleApplyGrade = async () => {
    if (isSaving || grade === territory.grade) return;
    setIsSaving(true); setError(null);
    try {
      await changeTerritoryGrade(territory.territoryId, grade, reason);
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '변경에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleAuction = async () => {
    if (isToggling) return;
    setIsToggling(true); setError(null);
    try {
      await changeTerritoryAuction(territory.territoryId, !territory.auctionEnabled, reason);
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '경매 설정 변경에 실패했습니다.');
    } finally {
      setIsToggling(false);
    }
  };

  const handleForceStart = async () => {
    if (isStarting || !isIdle) return;
    setIsStarting(true); setError(null);
    try {
      await forceStartAuction(territory.territoryId);
      onChanged();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '경매 시작에 실패했습니다.');
    } finally {
      setIsStarting(false);
    }
  };

  return (
    <>
      <p className="font-bold text-sm mb-1">영토 ({territory.coordX}, {territory.coordY})</p>
      <p className="text-muted text-[11px] mb-4">
        현재 {territory.grade}급 · {territory.status}
        {territory.ownerNickname ? ` · ${territory.ownerNickname}` : ''}
      </p>

      <label className="block text-dim mb-1.5 text-[11px] font-medium">등급 변경</label>
      <div className="flex gap-1 mb-3">
        {GRADES.map(g => (
          <button key={g} onClick={() => setGrade(g)} disabled={isOccupied}
            style={{ borderColor: grade === g ? GRADE_COLOR[g] : undefined, color: grade === g ? GRADE_COLOR[g] : undefined }}
            className={`flex-1 h-8 rounded-lg border text-xs font-bold disabled:opacity-40 ${grade === g ? '' : 'border-outline text-muted'}`}>
            {g}
          </button>
        ))}
      </div>

      {isOccupied ? (
        <p className="text-flare text-[11px] mb-4 leading-relaxed">
          점유 중인 영토는 등급을 변경할 수 없습니다. 점유가 끝난 뒤 변경하세요.
        </p>
      ) : (
        <>
          <label className="block text-dim mb-1.5 text-[11px] font-medium">사유</label>
          <input value={reason} onChange={e => setReason(e.target.value)} placeholder="변경 사유"
            className="w-full bg-elevated border border-outline rounded-md px-2 h-9 text-foreground outline-none focus:border-primary text-xs mb-4" />

          <button onClick={() => void handleApplyGrade()} disabled={isSaving || grade === territory.grade}
            className="w-full h-10 rounded-lg bg-primary text-surface font-bold text-sm hover:brightness-110 disabled:opacity-40">
            {isSaving ? '적용 중...' : '등급 변경 적용'}
          </button>
        </>
      )}

      <div className="border-t border-outline mt-4 pt-4">
        <label className="block text-dim mb-1.5 text-[11px] font-medium">경매 상태</label>
        <p className="text-[11px] mb-1.5 font-semibold" style={{ color: territory.auctionEnabled ? '#00ff88' : '#ff8c00' }}>
          {territory.auctionEnabled ? '● 경매 활성' : '⏸ 경매 중지됨'}
        </p>
        <p className="text-muted text-[10px] mb-3 leading-relaxed">
          중지하면 이 영토는 IDLE이어도 신규 경매가 열리지 않습니다. 진행 중인 경매는 그대로 종료됩니다.
        </p>
        <button onClick={() => void handleToggleAuction()} disabled={isToggling}
          className="w-full h-9 rounded-lg border border-outline text-xs font-bold hover:bg-panel disabled:opacity-40"
          style={{ color: territory.auctionEnabled ? '#ff8c00' : '#00ff88' }}>
          {isToggling ? '처리 중...' : territory.auctionEnabled ? '경매 중지' : '경매 재개'}
        </button>

        <button onClick={() => void handleForceStart()} disabled={isStarting || !isIdle}
          title={isIdle ? '' : 'IDLE 상태의 영토만 즉시 시작할 수 있습니다.'}
          className="w-full h-9 mt-2 rounded-lg bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">
          {isStarting ? '시작 중...' : '경매 강제 시작'}
        </button>
        {!isIdle && (
          <p className="text-dim text-[10px] mt-1.5 leading-relaxed">
            IDLE 상태에서만 재경매 대기를 건너뛰고 즉시 시작할 수 있습니다. (현재 {territory.status})
          </p>
        )}
      </div>

      {error && <p className="text-danger text-[11px] mt-3">{error}</p>}
    </>
  );
}
