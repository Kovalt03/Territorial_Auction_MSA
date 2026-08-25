import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { useApp } from '../context/AppContext';
import { fetchSiegeEvents, fetchMySiegeHistory } from '../api/siege';

import { GNB } from '../components/GNB';

import type { SiegeEventItem, MySiegeHistory } from '../api/siege';

function zoneEffect(zone: number): string {
  return zone === 1 ? '성 점령' : zone === 2 ? '생산 마비' : zone === 3 ? '저장소 약탈' : '';
}

function remaining(iso: string): string {
  const secs = Math.max(0, Math.floor((new Date(iso).getTime() - Date.now()) / 1000));
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  const s = secs % 60;
  return h > 0 ? `${h}시간 ${m}분` : m > 0 ? `${m}분 ${s}초` : `${s}초`;
}

export function SiegesPage() {
  const navigate = useNavigate();
  const { userId } = useApp();
  const [ongoing, setOngoing] = useState<SiegeEventItem[]>([]);
  const [history, setHistory] = useState<MySiegeHistory | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const load = () => {
      fetchSiegeEvents('PENDING')
        .then(r => setOngoing(r.sieges.filter(s => s.attacker.userId === userId || s.defender.userId === userId)))
        .catch(e => { setError('공성 현황을 불러올 수 없습니다.'); console.warn('[SiegesPage] events', e); });
      fetchMySiegeHistory()
        .then(setHistory)
        .catch(e => console.warn('[SiegesPage] history', e));
    };
    load();
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [userId]);

  // 진행 중 공성 완료 시각이 지나면 목록에서 제거되도록 now 로 재평가 (표시용)
  const active = ongoing.filter(s => new Date(s.resolveAt).getTime() > now - 60_000);

  return (
    <div className="page-root">
      <GNB />
      <div className="flex-1 overflow-y-auto p-6 max-w-3xl mx-auto w-full">
        <h1 className="text-danger font-bold text-2xl mb-1">⚔ 공성 현황</h1>
        <p className="text-muted text-xs mb-5">내가 공격하거나 방어 중인 공성전과 지난 전적</p>

        {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}

        {/* 진행 중 */}
        <h2 className="text-foreground font-semibold text-sm mb-2">진행 중</h2>
        <div className="space-y-2 mb-6">
          {active.length === 0 && (
            <p className="text-muted text-xs bg-panel-deep rounded-xl p-4 text-center">진행 중인 공성전이 없습니다</p>
          )}
          {active.map(s => {
            const isDefender = s.defender.userId === userId;
            return (
              <div
                key={s.siegeId}
                className={`flex items-center gap-2 rounded-xl p-3 border ${isDefender ? 'border-danger/60 bg-danger/5' : 'border-gp/50 bg-gp/5'}`}
              >
                <button
                  onClick={() => navigate(`/app/territory/${s.targetTerritory.id}`)}
                  className="flex-1 text-left"
                >
                  <div className="flex items-center gap-2">
                    <span className={`text-[11px] font-bold ${isDefender ? 'text-danger' : 'text-gp'}`}>
                      {isDefender ? '🛡 방어' : '⚔ 공격'} · ({s.targetTerritory.coordX}, {s.targetTerritory.coordY})
                    </span>
                    <span className="text-gold text-[11px] font-bold">{remaining(s.resolveAt)} 후 정산</span>
                  </div>
                  <p className="text-muted text-[10px] mt-0.5">
                    {isDefender ? `공격자: ${s.attacker.nickname}` : `방어자: ${s.defender.nickname}`}
                    {' · '}Zone {s.attackZone} ({zoneEffect(s.attackZone)})
                    {s.targetBuilding && ` · 정밀: ${s.targetBuilding.displayName ?? s.targetBuilding.name}`}
                  </p>
                </button>
                {!isDefender && (
                  <button
                    onClick={() => navigate(`/app/siege?target=${s.targetTerritory.id}`)}
                    className="flex-shrink-0 text-[11px] font-semibold text-gp border border-gp/50 rounded-lg px-2.5 py-1.5 hover:bg-gp/10 transition-colors"
                  >
                    🏰 공성 가기
                  </button>
                )}
              </div>
            );
          })}
        </div>

        {/* 이력 */}
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-foreground font-semibold text-sm">이력</h2>
          {history && (
            <span className="text-[11px] text-muted">
              <span className="text-gp">{history.wins}승</span> · <span className="text-danger">{history.losses}패</span>
            </span>
          )}
        </div>
        <div className="space-y-1.5">
          {(!history || history.history.length === 0) && (
            <p className="text-muted text-xs bg-panel-deep rounded-xl p-4 text-center">공성 이력이 없습니다</p>
          )}
          {history?.history.map(h => {
            const win = h.result === 'WIN';
            return (
              <div key={h.siegeId} className="flex items-center gap-2 bg-panel-deep rounded-lg px-3 py-2">
                <span className="text-[11px] text-muted flex-1">
                  {h.role === 'ATTACKER' ? '⚔ 공격' : '🛡 방어'} · {h.territoryGrade}급 영토 #{h.territoryId}
                </span>
                <span className={`text-[11px] font-bold ${win ? 'text-gp' : 'text-danger'}`}>
                  {win ? '승리' : '패배'}
                </span>
                {h.role === 'ATTACKER' && (
                  <button
                    onClick={() => navigate(`/app/siege?target=${h.territoryId}`)}
                    className="flex-shrink-0 text-[10px] font-semibold text-gp border border-gp/40 rounded-md px-2 py-1 hover:bg-gp/10 transition-colors"
                  >
                    🏰 재공성
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
