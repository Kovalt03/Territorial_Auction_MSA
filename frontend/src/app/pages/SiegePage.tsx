import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router';

import { declareSiege, fetchSiegeTarget } from '../api/siege';
import { fetchTerritoryDetail } from '../api/map';
import { fetchAttackTokens } from '../api/military';
import { useMilitary } from '../hooks/useMilitary';
import { useFetch } from '../hooks/useFetch';
import { ApiError } from '../api/client';

import { GNB } from '../components/GNB';
import { Button } from '../components/Button';
import { UNIT_LABELS } from './islandGrid';

import type { TerritoryDetailResponse } from '../types/territory';
import type { StructureEntry, SiegeTargetIntel, SiegeTargetBuilding } from '../api/siege';

const SIEGE_TIME_LIMIT_SEC = 7200;
const STAGING_CAP_PER = 10; // 주둔지 1개당 공격 병력 상한
const STAGING_COST_GP = 500; // 주둔지 1개 건설비(공격자 금고 GP)

// 대상 영토 인접 타일(체비쇼프 1) — 주둔지 자동 배치용
const ADJACENT_OFFSETS = [
  [0, -1], [-1, 0], [1, 0], [0, 1], [-1, -1], [1, 1], [-1, 1], [1, -1],
] as const;
const GRID_MAX = 49;

function buildStagingStructures(t: TerritoryDetailResponse, count: number): StructureEntry[] {
  const clamp = (v: number) => Math.max(0, Math.min(GRID_MAX, v));
  return ADJACENT_OFFSETS.slice(0, count).map(([dx, dy]) => ({
    type: 'STAGING' as const,
    coordX: clamp(t.coordX + dx),
    coordY: clamp(t.coordY + dy),
  }));
}

// effect: 해당 Zone 공략 성공 시 실제 효과 (백엔드 LOOT/DEBUFF/인계와 대응). HP는 intel(대상 정찰)에서 실시간 조회.
const zones = [
  { id: 1, name: 'Zone 1 — 핵심 (성)', effect: '성 HP 파괴 → 영토 즉시 점령', color: '#ff3333' },
  { id: 2, name: 'Zone 2 — 내부 (병영·생산소)', effect: '생산 건물 파괴 → 12h 생산 마비', color: '#ffd700' },
  { id: 3, name: 'Zone 3 — 외곽 (저장소·방벽)', effect: '저장소 GP 50% 약탈 → 내 금고', color: '#00f5ff' },
];

const ZONE_COLOR: Record<number, string> = { 1: '#ff3333', 2: '#ffd700', 3: '#00f5ff' };

const BUILDING_ICON: Record<string, string> = {
  CASTLE: '🏯', STORAGE: '📦', WORKSHOP: '🏭', BARRACKS: '⚔️',
  RESIDENCE: '🏠', FARMLAND: '🌾', WALL: '🧱', TOWER: '🗼', RESEARCH_LAB: '🔬',
};

function Countdown({ seconds }: { seconds: number }) {
  const [left, setLeft] = useState(seconds);
  useEffect(() => {
    if (left <= 0) return;
    const t = setTimeout(() => setLeft(l => l - 1), 1000);
    return () => clearTimeout(t);
  }, [left]);
  const h = Math.floor(left / 3600);
  const m = Math.floor((left % 3600) / 60);
  const s = left % 60;
  return (
    <span>
      {String(h).padStart(2, '0')}:{String(m).padStart(2, '0')}:{String(s).padStart(2, '0')}
    </span>
  );
}

export function SiegePage() {
  const navigate = useNavigate();
  const { data: militaryData } = useMilitary();
  const { data: tokens } = useFetch(fetchAttackTokens, '공격권 정보를 불러올 수 없습니다.');
  const [selectedZone, setSelectedZone] = useState(3);
  // unitTypeId → 커밋 수량
  const [forces, setForces] = useState<Record<number, number>>({});
  const [stagingCount, setStagingCount] = useState(1);
  const [showConfirm, setShowConfirm] = useState(false);
  const [isSiegeStarted, setIsSiegeStarted] = useState(false);
  const [siegeError, setSiegeError] = useState<string | null>(null);

  const [targetInput, setTargetInput] = useState('');
  const [targetTerritory, setTargetTerritory] = useState<TerritoryDetailResponse | null>(null);
  const [targetError, setTargetError] = useState<string | null>(null);
  const [isSearching, setIsSearching] = useState(false);

  const [intel, setIntel] = useState<SiegeTargetIntel | null>(null);
  const [attackMode, setAttackMode] = useState<'normal' | 'precision'>('normal');
  const [targetBuildingId, setTargetBuildingId] = useState<number | null>(null);

  const targetId = parseInt(targetInput, 10);

  const searchTargetById = useCallback(async (id: number) => {
    if (!id) return;
    setIsSearching(true);
    setTargetError(null);
    setTargetTerritory(null);
    setIntel(null);
    setAttackMode('normal');
    setTargetBuildingId(null);
    try {
      const detail = await fetchTerritoryDetail(id);
      if (!detail.owner) {
        setTargetError('점령자가 없는 영토는 공격할 수 없습니다.');
        return;
      }
      setTargetTerritory(detail);
      fetchSiegeTarget(id)
        .then(setIntel)
        .catch(e => {
          console.warn('[SiegePage] intel fetch failed', e);
          setIntel(null);
        });
    } catch {
      setTargetError('영토를 찾을 수 없습니다. ID를 확인해주세요.');
    } finally {
      setIsSearching(false);
    }
  }, []);

  // 공격 구역·방식이 바뀌면 정밀 대상 선택을 초기화(다른 존 건물이 남지 않도록). 건물 클릭 시엔
  // 구역·방식·대상을 한 번에 지정하므로, 초기화를 effect가 아니라 각 핸들러에서 명시적으로 처리한다.
  const handleSelectZone = (zoneId: number) => {
    setSelectedZone(zoneId);
    setTargetBuildingId(null);
  };
  const handleSelectMode = (mode: 'normal' | 'precision') => {
    setAttackMode(mode);
    setTargetBuildingId(null);
  };
  // 영토 그리드에서 건물을 클릭 → 그 건물의 구역으로 전환 + 정밀 모드 + 대상 지정.
  const handlePickBuilding = (b: SiegeTargetBuilding) => {
    if (b.isUnderConstruction) return;
    setSelectedZone(b.zone);
    setAttackMode('precision');
    setTargetBuildingId(b.buildingId);
  };

  const handleSearchTarget = () => void searchTargetById(targetId);

  // 맵에서 '공성전 선언'으로 넘어오면 ?target=<영토ID> 를 받아 자동으로 대상을 선택한다.
  const [searchParams] = useSearchParams();
  useEffect(() => {
    const t = searchParams.get('target');
    const id = t ? parseInt(t, 10) : 0;
    if (id) {
      setTargetInput(t!);
      void searchTargetById(id);
    }
  }, [searchParams, searchTargetById]);

  const zone = zones.find(z => z.id === selectedZone)!;

  // 대기 유닛을 타입별로 합산(위치 무관) — 공격 병력 후보
  const idleUnits = (() => {
    const map = new Map<number, { unitTypeId: number; name: string; displayName: string | null; icon: string | null; colorHex: string | null; attackPower: number; idle: number }>();
    militaryData?.locations.forEach(l => l.units.forEach(u => {
      if (u.idleCount <= 0) return;
      const ex = map.get(u.unitTypeId);
      if (ex) ex.idle += u.idleCount;
      else map.set(u.unitTypeId, { unitTypeId: u.unitTypeId, name: u.name, displayName: u.displayName, icon: u.icon, colorHex: u.colorHex, attackPower: u.attackPower, idle: u.idleCount });
    }));
    return [...map.values()];
  })();

  const totalUnits = Object.values(forces).reduce((a, b) => a + b, 0);
  const attackPower = idleUnits.reduce((sum, u) => sum + u.attackPower * (forces[u.unitTypeId] ?? 0), 0);
  const capacity = stagingCount * STAGING_CAP_PER;
  const overCapacity = totalUnits > capacity;
  const structureCost = stagingCount * STAGING_COST_GP;
  const normalTokens = tokens?.normalCount ?? 0;
  const precisionTokens = tokens?.precisionCount ?? 0;
  const isPrecision = attackMode === 'precision';
  // 일반=일반 공격권, 정밀=정밀 공격권 소모.
  const activeTokens = isPrecision ? precisionTokens : normalTokens;
  const hasNoToken = tokens != null && activeTokens <= 0;
  const zoneBuildings =
    intel?.buildings.filter(b => b.zone === selectedZone && !b.isUnderConstruction) ?? [];
  const needsBuilding = isPrecision && targetBuildingId == null;
  const targetBuildingName = zoneBuildings.find(b => b.buildingId === targetBuildingId);

  // 영토 10×10 그리드의 각 칸이 어떤 건물에 속하는지 매핑(width/height 반영).
  // 건물이 겹칠 때(테스트 데이터 등) 각 건물의 좌상단(아이콘) 칸이 다른 건물 몸통에 덮이지
  // 않도록, 좌상단 칸은 몸통 칸보다 우선 소유하게 한다.
  const buildingCellMap = (() => {
    const map = new Map<string, SiegeTargetBuilding>();
    intel?.buildings.forEach(b => {
      for (let dx = 0; dx < b.width; dx++) {
        for (let dy = 0; dy < b.height; dy++) {
          const cx = b.posX + dx;
          const cy = b.posY + dy;
          if (cx < 0 || cx >= 10 || cy < 0 || cy >= 10) continue;
          const key = `${cx},${cy}`;
          const existing = map.get(key);
          const isThisTopLeft = cx === b.posX && cy === b.posY;
          const existingIsTopLeft =
            existing != null && cx === existing.posX && cy === existing.posY;
          if (existing == null || (isThisTopLeft && !existingIsTopLeft)) {
            map.set(key, b);
          }
        }
      }
    });
    return map;
  })();

  const handleStart = async () => {
    if (!targetTerritory) { setSiegeError('대상 영토를 먼저 검색해주세요.'); return; }
    if (totalUnits === 0) { setSiegeError('공격 병력을 1기 이상 선택해주세요.'); return; }
    if (overCapacity) { setSiegeError(`병력이 주둔지 수용량(${capacity})을 초과합니다.`); return; }
    if (isPrecision && targetBuildingId == null) { setSiegeError('정밀 공격할 건물을 선택해주세요.'); return; }
    setShowConfirm(false);
    setSiegeError(null);
    try {
      await declareSiege({
        targetTerritoryId: targetTerritory.territoryId,
        targetBuildingId: isPrecision ? targetBuildingId : null,
        attackZone: selectedZone,
        forces: idleUnits
          .filter(u => (forces[u.unitTypeId] ?? 0) > 0)
          .map(u => ({ unitTypeId: u.unitTypeId, quantity: forces[u.unitTypeId] })),
        structures: buildStagingStructures(targetTerritory, stagingCount),
      });
      setIsSiegeStarted(true);
    } catch (e) {
      setSiegeError(
        e instanceof ApiError && e.status >= 400 && e.status < 500
          ? e.message
          : '공성전 선언에 실패했습니다. 조건을 확인하고 다시 시도해주세요.',
      );
    }
  };

  const unitMeta = (u: { name: string; displayName: string | null; icon: string | null; colorHex: string | null }) => {
    const fb = UNIT_LABELS[u.name] ?? { label: u.name, icon: '⚔', color: '#e0e8ff' };
    return { label: u.displayName ?? fb.label, icon: u.icon ?? fb.icon, color: u.colorHex ?? fb.color };
  };

  return (
    <div className="page-root">
      <GNB />

      <div className="flex-1 flex overflow-hidden">
        {/* Left - Attack Setup */}
        <div className="w-[360px] bg-surface border-r border-outline flex flex-col">
          <div className="p-4 border-b border-outline">
            <h2 className="text-danger font-bold mb-3 text-lg">⚔ 공성전 준비</h2>
            <div className="flex gap-2">
              <input
                type="number"
                value={targetInput}
                onChange={e => { setTargetInput(e.target.value); setTargetTerritory(null); setTargetError(null); }}
                onKeyDown={e => e.key === 'Enter' && !isSearching && void handleSearchTarget()}
                placeholder="영토 ID 입력"
                className="flex-1 bg-elevated border border-outline rounded-lg px-3 h-9 text-foreground text-xs outline-none focus:border-danger transition-colors"
              />
              <Button
                variant="danger"
                size="sm"
                onClick={() => void handleSearchTarget()}
                disabled={!targetInput || isSearching}
              >
                {isSearching ? '검색 중...' : '검색'}
              </Button>
            </div>
            {targetError && <p className="text-danger text-[11px] mt-1.5">⚠ {targetError}</p>}
            {targetTerritory && (
              <div className="mt-2 bg-[#2a0a0a] border border-[#ff333360] rounded-lg px-3 py-2">
                <p className="text-danger text-xs font-semibold">
                  ({targetTerritory.coordX}, {targetTerritory.coordY}) · {targetTerritory.continentName}
                </p>
                <p className="text-muted text-[11px]">
                  {targetTerritory.grade}급 · {targetTerritory.owner?.nickname ?? '미점령'}
                </p>
              </div>
            )}
          </div>

          {/* Zone Selection */}
          <div className="p-4 border-b border-outline">
            <p className="text-muted font-semibold mb-3 text-xs">공격 구역 선택</p>
            {zones.map(z => {
              const zi = intel?.zones.find(v => v.zone === z.id);
              const cur = zi?.currentHp ?? 0;
              const max = zi?.maxHp ?? 0;
              const pct = max > 0 ? (cur / max) * 100 : 0;
              const hpLabel = !zi ? '대상 선택 시 표시' : max === 0 ? '방어 건물 없음' : `${cur} / ${max} HP`;
              return (
                <button
                  key={z.id}
                  onClick={() => handleSelectZone(z.id)}
                  className="w-full mb-2 rounded-xl p-3 text-left transition-all"
                  style={{
                    background: selectedZone === z.id ? z.color + '20' : 'var(--color-panel-deep)',
                    border: `1px solid ${selectedZone === z.id ? z.color : '#354064'}`,
                  }}
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-[13px] font-semibold" style={{ color: z.color }}>{z.name}</span>
                    {selectedZone === z.id && <span className="text-primary text-[11px]">선택됨</span>}
                  </div>
                  <div className="bg-panel h-2 rounded-full overflow-hidden">
                    <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, background: z.color }} />
                  </div>
                  <p className="mt-1 text-[10px]" style={{ color: z.color }}>{hpLabel}</p>
                  <p className="mt-0.5 text-[10px] text-muted">▸ {z.effect}</p>
                </button>
              );
            })}
          </div>

          {/* 공격 방식 — 일반(존 분산) / 정밀(건물 지정) */}
          <div className="p-4 border-b border-outline">
            <p className="text-muted font-semibold mb-2 text-xs">공격 방식</p>
            <div className="flex gap-2 mb-2">
              {([['normal', '일반', '존 전체 분산'], ['precision', '정밀', '건물 1개 집중']] as const).map(([mode, label, sub]) => (
                <button
                  key={mode}
                  onClick={() => handleSelectMode(mode)}
                  className="flex-1 rounded-xl py-2 text-center transition-all"
                  style={{
                    background: attackMode === mode ? '#8b50ff20' : 'var(--color-panel-deep)',
                    border: `1px solid ${attackMode === mode ? '#8b50ff' : '#354064'}`,
                    color: attackMode === mode ? '#8b50ff' : '#8892b0',
                  }}
                >
                  <span className="text-[13px] font-bold block">{label}</span>
                  <span className="text-[10px] block opacity-80">{sub}</span>
                </button>
              ))}
            </div>
            <p className="text-muted text-[10px]">
              {isPrecision
                ? '정밀 공격권을 소모해 선택한 건물 하나에 피해·효과를 집중합니다.'
                : '일반 공격권을 소모해 공격 구역의 건물에 피해를 분산합니다.'}
            </p>
            {isPrecision && (
              <div className="mt-2 space-y-1.5">
                {zoneBuildings.length === 0 && (
                  <p className="text-muted text-[10px]">
                    {intel ? '이 구역에 정밀 공격할 건물이 없습니다.' : '대상 영토를 먼저 검색하세요.'}
                  </p>
                )}
                {zoneBuildings.map(b => {
                  const isSelected = targetBuildingId === b.buildingId;
                  return (
                    <button
                      key={b.buildingId}
                      onClick={() => setTargetBuildingId(b.buildingId)}
                      className="w-full flex items-center justify-between rounded-lg px-2.5 py-1.5 text-left transition-all"
                      style={{
                        background: isSelected ? '#8b50ff20' : 'var(--color-panel-deep)',
                        border: `1px solid ${isSelected ? '#8b50ff' : '#354064'}`,
                      }}
                    >
                      <span className="text-[11px]" style={{ color: isSelected ? '#8b50ff' : '#e0e8ff' }}>
                        {b.displayName ?? b.name}
                      </span>
                      <span className="text-muted text-[10px]">{b.currentHp}/{b.maxHp} HP</span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* 공성 건물 — 주둔지 (공격 병력 상한 제공, 금고 GP 결제) */}
          <div className="p-4 border-b border-outline">
            <p className="text-muted font-semibold mb-1 text-xs">주둔지 수 (공격 병력 상한)</p>
            <p className="text-muted text-[10px] mb-2">
              주둔지 1개 = 병력 +{STAGING_CAP_PER}기 · 개당 {STAGING_COST_GP} GP(금고). 몇 개를 지을지 고릅니다.
            </p>
            <div className="flex items-center gap-2">
              {[1, 2, 3].map(n => (
                <button
                  key={n}
                  onClick={() => setStagingCount(n)}
                  className="flex-1 rounded-xl py-2 text-center transition-all"
                  style={{
                    background: stagingCount === n ? '#ff333320' : 'var(--color-panel-deep)',
                    border: `1px solid ${stagingCount === n ? '#ff3333' : '#354064'}`,
                    color: stagingCount === n ? '#ff3333' : '#8892b0',
                  }}
                >
                  <span className="text-[13px] font-bold block">주둔지 ×{n}</span>
                  <span className="text-[10px] block opacity-80">병력 {n * STAGING_CAP_PER}기</span>
                </button>
              ))}
            </div>
            <p className="text-muted text-[10px] mt-2">
              선택: 수용량 {capacity}기 · 금고 {structureCost.toLocaleString()} GP · 대상 인접 타일 자동 배치
            </p>
          </div>

          {/* 공격 병력 — 보유 대기 유닛에서 선택 */}
          <div className="p-4 border-b border-outline flex-1 overflow-y-auto">
            <p className="text-muted font-semibold mb-3 text-xs">공격 병력 (대기 유닛)</p>
            {idleUnits.length === 0 && (
              <p className="text-muted text-[11px] py-3 text-center">대기 유닛이 없습니다. 병영에서 먼저 훈련하세요.</p>
            )}
            {idleUnits.map(u => {
              const m = unitMeta(u);
              const qty = forces[u.unitTypeId] ?? 0;
              return (
                <div key={u.unitTypeId} className="flex items-center gap-3 mb-3">
                  <span className="text-lg">{m.icon}</span>
                  <div className="flex-1">
                    <div className="flex justify-between mb-1">
                      <span className="text-xs" style={{ color: m.color }}>{m.label}</span>
                      <span className="text-muted text-[10px]">공격력 {u.attackPower} · 대기 {u.idle}</span>
                    </div>
                    <input
                      type="range"
                      min={0}
                      max={u.idle}
                      value={qty}
                      onChange={e => setForces(prev => ({ ...prev, [u.unitTypeId]: Number(e.target.value) }))}
                      className="w-full"
                      style={{ accentColor: m.color }}
                    />
                  </div>
                  <div className="w-10 h-8 bg-panel-deep border border-outline rounded-lg flex items-center justify-center">
                    <span className="text-[13px]" style={{ color: m.color }}>{qty}</span>
                  </div>
                </div>
              );
            })}
            <div className="bg-panel-deep rounded-xl p-3 mt-2">
              <div className="flex justify-between">
                <span className={overCapacity ? 'text-danger text-xs' : 'text-muted text-xs'}>
                  병력 {totalUnits} / 수용 {capacity}
                </span>
                <span className="text-danger font-bold text-xs">총 공격력: {attackPower}</span>
              </div>
              {overCapacity && <p className="text-danger text-[10px] mt-1">주둔지 수용량 초과 — 주둔지를 늘리거나 병력을 줄이세요</p>}
            </div>
          </div>

          <div className="p-4 space-y-2">
            <div className="flex items-center justify-between text-[11px]">
              <span className="text-muted">공격권 보유</span>
              <span className="text-foreground">
                일반 <span className={`font-bold ${!isPrecision ? (normalTokens > 0 ? 'text-gold' : 'text-danger') : 'text-muted'}`}>{normalTokens}</span>
                {' · '}정밀 <span className={`font-bold ${isPrecision ? (precisionTokens > 0 ? 'text-gold' : 'text-danger') : 'text-muted'}`}>{precisionTokens}</span>
              </span>
            </div>
            {hasNoToken && (
              <button
                onClick={() => navigate('/app/item-shop')}
                className="w-full text-[11px] font-semibold text-primary border border-primary/50 rounded-lg py-2 hover:bg-primary/10 transition-colors"
              >
                {isPrecision ? '정밀 공격권이 없습니다' : '공격권이 없습니다'} — 상점에서 구매하기 →
              </button>
            )}
            {siegeError && (
              <p className="text-danger text-xs text-center">⚠ {siegeError}</p>
            )}
            <Button
              variant="danger"
              size="lg"
              fullWidth
              onClick={() => setShowConfirm(true)}
              disabled={totalUnits === 0 || !targetTerritory || hasNoToken || needsBuilding}
            >
              {!targetTerritory
                ? '대상 영토를 선택하세요'
                : hasNoToken
                  ? '공격권 필요'
                  : needsBuilding
                    ? '정밀 대상 건물 선택'
                    : '⚔ 공성전 시작'}
            </Button>
          </div>
        </div>

        {/* Right - Siege Map + Progress */}
        <div className="flex-1 flex flex-col">
          <div className="flex-1 p-5 flex flex-col">
            <h2 className="text-foreground font-bold mb-4 text-xl">공성전 현황</h2>

            {/* Target territory */}
            <div className="card p-4 mb-4">
              <div className="flex items-center justify-between mb-3">
                <div>
                  {targetTerritory ? (
                    <>
                      <p className="text-foreground font-bold text-base">
                        ({targetTerritory.coordX}, {targetTerritory.coordY}) · {targetTerritory.continentName}
                      </p>
                      <p className="text-muted text-xs">
                        방어자: {targetTerritory.owner?.nickname ?? '미점령'} · {targetTerritory.grade}급 영토
                      </p>
                    </>
                  ) : (
                    <>
                      <p className="text-outline font-bold text-base">대상 영토 미선택</p>
                      <p className="text-muted text-xs">왼쪽에서 영토 ID를 검색하세요</p>
                    </>
                  )}
                </div>
                <div className="text-right">
                  <p className="text-gold font-bold text-lg">
                    <Countdown seconds={SIEGE_TIME_LIMIT_SEC} />
                  </p>
                  <p className="text-muted text-[11px]">공성 제한 시간</p>
                </div>
              </div>

              {zones.map(z => {
                const zi = intel?.zones.find(v => v.zone === z.id);
                const cur = zi?.currentHp ?? 0;
                const max = zi?.maxHp ?? 0;
                const pct = max > 0 ? (cur / max) * 100 : 0;
                return (
                  <div key={z.id} className="mb-2">
                    <div className="flex justify-between mb-1">
                      <span className="text-xs" style={{ color: z.color }}>{z.name}</span>
                      <span className="text-[11px]" style={{ color: z.color }}>
                        {!zi ? '—' : max === 0 ? '건물 없음' : `${cur} / ${max}`}
                      </span>
                    </div>
                    <div className="bg-surface h-3 rounded-full overflow-hidden">
                      <div className="h-full rounded-full transition-all" style={{ width: `${pct}%`, background: z.color }} />
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Siege preview grid (10x10) — 실제 건물을 배치, 클릭 시 정밀 공격 대상 지정 */}
            <div className="bg-surface border border-outline rounded-xl p-4 flex-1">
              <p className="text-muted mb-1 text-xs">영토 내부 구조</p>
              <p className="text-muted mb-3 text-[10px]">
                {intel
                  ? '건물을 클릭하면 그 구역으로 전환되며 정밀 공격 대상으로 지정됩니다.'
                  : '대상 영토를 검색하면 건물이 표시됩니다.'}
              </p>
              <div
                className="grid gap-1 max-w-[360px] mx-auto"
                style={{ gridTemplateColumns: 'repeat(10, 1fr)' }}
              >
                {Array.from({ length: 100 }, (_, i) => {
                  const x = i % 10, y = Math.floor(i / 10);
                  const b = buildingCellMap.get(`${x},${y}`);
                  if (b) {
                    const isTopLeft = x === b.posX && y === b.posY;
                    const isSelected = targetBuildingId === b.buildingId && isPrecision;
                    const col = ZONE_COLOR[b.zone] ?? '#8892b0';
                    return (
                      <button
                        key={i}
                        onClick={() => handlePickBuilding(b)}
                        title={`${b.displayName ?? b.name} · Zone ${b.zone} · ${b.currentHp}/${b.maxHp} HP${b.isUnderConstruction ? ' · 건설 중' : ''}`}
                        className="aspect-square rounded-sm flex items-center justify-center transition-all"
                        style={{
                          background: col + (isSelected ? '55' : '33'),
                          border: `1px solid ${isSelected ? '#8b50ff' : col}`,
                          boxShadow: isSelected ? '0 0 0 1px #8b50ff inset' : undefined,
                          opacity: b.isUnderConstruction ? 0.5 : 1,
                        }}
                      >
                        {isTopLeft && (
                          <span className="text-[11px] leading-none">
                            {BUILDING_ICON[b.name] ?? '▪'}
                          </span>
                        )}
                      </button>
                    );
                  }
                  const isCore = x >= 3 && x <= 6 && y >= 3 && y <= 6;
                  const isInner = x >= 2 && x <= 7 && y >= 2 && y <= 7;
                  const bg = isCore ? '#ff333330' : isInner ? '#ffd70015' : '#00f5ff08';
                  const border = isCore ? '#ff333360' : isInner ? '#ffd70040' : '#00f5ff20';
                  return (
                    <div
                      key={i}
                      className="aspect-square rounded-sm"
                      style={{ background: bg, border: `1px solid ${border}` }}
                    />
                  );
                })}
              </div>
              <div className="flex gap-4 mt-3 justify-center flex-wrap">
                {zones.map(z => (
                  <div key={z.id} className="flex items-center gap-1">
                    <div className="w-3 h-3 rounded-sm border" style={{ background: z.color + '30', borderColor: z.color + '60' }} />
                    <span className="text-muted text-[10px]">Zone {z.id}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {isSiegeStarted && (
            <div className="bg-[#2a0a0a] border-t-2 border-danger p-4">
              <div className="flex items-center gap-3">
                <div className="w-3 h-3 bg-danger rounded-full animate-pulse" />
                <span className="text-danger font-bold text-sm">공성전 진행 중 — {zone.name} 공격 중</span>
                <div className="ml-auto flex gap-2">
                  <button onClick={() => setIsSiegeStarted(false)} className="h-8 px-4 bg-elevated border border-outline rounded-lg text-muted text-xs">
                    철수
                  </button>
                </div>
              </div>
              <div className="mt-2 bg-[#1a0505] h-3 rounded-full overflow-hidden">
                <div className="h-full bg-danger rounded-full animate-pulse" style={{ width: '35%' }} />
              </div>
            </div>
          )}
        </div>
      </div>

      {showConfirm && (
        <div className="modal-overlay">
          <div className="bg-panel border-2 border-danger rounded-2xl p-8 max-w-sm mx-4 text-center">
            <span className="text-5xl">⚔</span>
            <h3 className="text-danger font-bold text-xl mt-3 mb-2">공성전 선언</h3>
            <p className="text-muted mb-5 text-[13px]">
              {zone.name}을 {totalUnits}명의 유닛으로 공격합니다.
              공격력: {attackPower}
            </p>
            <div className="bg-[#2a0a0a] border border-danger rounded-xl py-3 px-4 mb-5 text-left space-y-1">
              <p className="text-muted text-xs">
                대상: ({targetTerritory?.coordX}, {targetTerritory?.coordY}) · {targetTerritory?.continentName}
              </p>
              <p className="text-muted text-xs">방어자: {targetTerritory?.owner?.nickname}</p>
              <p className="text-muted text-xs">공격 구역: {zone.name}</p>
              <p className="text-muted text-xs">
                공격 방식: {isPrecision
                  ? `정밀 — ${targetBuildingName?.displayName ?? targetBuildingName?.name ?? '건물'}`
                  : '일반 (존 분산)'}
              </p>
              <p className="text-muted text-xs">총 유닛: {totalUnits}명 · 공격력: {attackPower}</p>
            </div>
            <div className="flex gap-3">
              <button onClick={() => setShowConfirm(false)}
                className="btn-cancel">취소</button>
              <Button
                variant="danger"
                onClick={() => void handleStart()}
                className="flex-1"
              >
                공격 개시
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
