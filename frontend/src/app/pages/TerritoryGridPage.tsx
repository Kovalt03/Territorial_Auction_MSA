import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router';

import { useApp } from '../context/AppContext';
import { fetchTerritoryDetail } from '../api/map';
import {
  fetchTerritoryBuildings, placeTerritoryBuilding,
  placeFromInventoryOnTerritory, upgradeTerritoryBuilding,
  repairBuilding, repairAllBuildings,
} from '../api/territoryBuilding';
import {
  fetchBuildingTypes, fetchBuildingInventory,
  storeBuilding as storeBuildingApi, moveBuilding as moveBuildingApi,
} from '../api/island';
import { ApiError } from '../api/client';

import { GNB } from '../components/GNB';
import { HealthBar } from '../components/HealthBar';

import type { TerritoryDetailResponse, TerritoryGridBuilding } from '../types/territory';
import type { BuildingTypeInfo, InventoryItem } from '../types/island';

import {
  type BuildingType, type Cell,
  buildingColors, buildingLabels, buildingNames, UNIT_LABELS,
  emptyGrid, buildGrid, isUnderConstruction, remainingLabel,
} from './islandGrid';
import { IslandToast } from './IslandToast';
import { IslandResearchPanel } from './IslandResearchPanel';
import { TerritoryGridBuildModal } from './TerritoryGridBuildModal';
import { TerritoryGridBuildingActionPanel } from './TerritoryGridBuildingActionPanel';
import { TerritoryGridInventoryModal } from './TerritoryGridInventoryModal';
import { TerritoryDeployModal } from './TerritoryDeployModal';
import { IslandTrainUnitModal } from './IslandTrainUnitModal';
import { useMilitary } from '../hooks/useMilitary';
import { deployUnit, recallUnit, produceUnit, fetchTerritoryGarrison, fetchResearch, startResearch, fetchUnitTypes } from '../api/military';
import type { GarrisonUnit, ResearchStatus, UnitTypeCatalog } from '../types/military';

const GARRISON_CAP: Record<string, number> = { castle: 5, residence: 5, tower: 3, wall: 2 };

export function TerritoryGridPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { ap, gp, userId, syncGP } = useApp();
  const territoryId = Number(id);

  const { data: militaryData, reload: reloadMilitary } = useMilitary();
  const [deployBuilding, setDeployBuilding] = useState<{ buildingId: number; name: string; capacityPerLevel: number } | null>(null);
  const [garrison, setGarrison] = useState<GarrisonUnit[]>([]);
  const [isGarrisonBusy, setIsGarrisonBusy] = useState(false);
  const [showTrain, setShowTrain] = useState(false);
  const [trainUnitTypeId, setTrainUnitTypeId] = useState<number | null>(null);
  const [trainQuantity, setTrainQuantity] = useState(1);
  const [trainLevel, setTrainLevel] = useState(1);
  const [isTraining, setIsTraining] = useState(false);
  const [research, setResearch] = useState<ResearchStatus | null>(null);
  // 훈련 모달 선택 목록은 보유 유닛이 아니라 전체 종류 카탈로그를 소스로 한다.
  const [unitCatalog, setUnitCatalog] = useState<UnitTypeCatalog[]>([]);
  useEffect(() => {
    fetchUnitTypes().then(setUnitCatalog).catch(e => console.warn('[TerritoryGridPage] unit types load failed', e));
  }, []);
  const researchedLevels = Object.fromEntries(
    (research?.units ?? []).map(u => [u.unitTypeId, u.researchedLevel]),
  ) as Record<number, number>;
  const [detail, setDetail] = useState<TerritoryDetailResponse | null>(null);
  const [buildings, setBuildings] = useState<TerritoryGridBuilding[]>([]);
  const [catalog, setCatalog] = useState<BuildingTypeInfo[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [error, setError] = useState<string | null>(null);

  const [toast, setToast] = useState<{ message: string; isError: boolean } | null>(null);
  const showToast = useCallback((message: string, isError = true) => {
    setToast({ message, isError });
    setTimeout(() => setToast(null), 3500);
  }, []);

  const reloadDetail = useCallback(() => {
    if (!territoryId) return;
    fetchTerritoryDetail(territoryId)
      .then(setDetail)
      .catch(e => { setError(e instanceof ApiError ? e.message : '영토 정보를 불러올 수 없습니다.'); console.warn('[TerritoryGridPage] detail', e); });
  }, [territoryId]);

  const reloadBuildings = useCallback(() => {
    if (!territoryId) return;
    fetchTerritoryBuildings(territoryId)
      .then(setBuildings)
      .catch(e => { setError(e instanceof ApiError ? e.message : '건물 목록을 불러올 수 없습니다.'); console.warn('[TerritoryGridPage] buildings', e); });
  }, [territoryId]);

  const reloadInventory = useCallback(() => {
    fetchBuildingInventory().then(setInventory).catch(e => console.warn('[TerritoryGridPage] inventory', e));
  }, []);

  useEffect(() => { reloadDetail(); reloadBuildings(); reloadInventory(); }, [reloadDetail, reloadBuildings, reloadInventory]);
  useEffect(() => {
    fetchBuildingTypes().then(setCatalog).catch(e => console.warn('[TerritoryGridPage] building types', e));
  }, []);

  const gridSize = detail?.gridSize ?? 10;
  const z1 = detail?.zone1Radius ?? 2;
  const z2 = detail?.zone2Radius ?? 4;
  const [grid, setGrid] = useState<Cell[][]>(() => emptyGrid(10, 2, 4));
  useEffect(() => {
    setGrid(buildGrid(gridSize, z1, z2, buildings));
  }, [gridSize, z1, z2, buildings]);

  // 건설 중인 건물이 있는 동안만 1초마다 갱신하고, 완료 시점에 다시 불러온다.
  const [now, setNow] = useState(() => Date.now());
  const hasConstruction = buildings.some(b => isUnderConstruction(b.buildCompleteAt, now));
  // 완공된 병영의 최고 레벨 — 훈련 모달 상위 유닛 잠금 판정.
  const territoryBarracksLevel = buildings
    .filter(b => b.type.toLowerCase() === 'barracks' && !b.isDestroyed && !isUnderConstruction(b.buildCompleteAt, now))
    .reduce((max, b) => Math.max(max, b.level), 0);
  useEffect(() => {
    if (!hasConstruction) return;
    const timer = setInterval(() => {
      const next = Date.now();
      setNow(next);
      if (!buildings.some(b => isUnderConstruction(b.buildCompleteAt, next))) reloadBuildings();
    }, 1000);
    return () => clearInterval(timer);
  }, [hasConstruction, buildings, reloadBuildings]);

  const isOwner = detail?.owner?.userId != null && detail.owner.userId === userId;

  const [selectedCell, setSelectedCell] = useState<{ x: number; y: number } | null>(null);
  const [showBuild, setShowBuild] = useState(false);
  const [selectedBuilding, setSelectedBuilding] = useState<BuildingType | null>(null);
  const [buildError, setBuildError] = useState('');
  const [showBuildingAction, setShowBuildingAction] = useState(false);
  const [moveMode, setMoveMode] = useState(false);
  const [moveSourceCell, setMoveSourceCell] = useState<{ x: number; y: number } | null>(null);
  const [showInventory, setShowInventory] = useState(false);
  const [deployFromInventoryIdx, setDeployFromInventoryIdx] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const selectedCellData = selectedCell ? grid[selectedCell.y]?.[selectedCell.x] : null;
  const zoneColor = { 1: '#ff000020', 2: '#ffd70010', 3: '#00f5ff08' };
  const zoneBorder = { 1: '#ff0000', 2: '#ffd700', 3: '#00f5ff' };

  const catalogByType = new Map(catalog.map(c => [c.name.toLowerCase(), c]));
  const iconFor = (t: BuildingType) => catalogByType.get(t)?.icon ?? buildingLabels[t] ?? '🏗';
  const colorFor = (t: BuildingType) => catalogByType.get(t)?.colorHex ?? buildingColors[t] ?? '#8892b0';
  const nameFor = (t: BuildingType) => catalogByType.get(t)?.displayName ?? buildingNames[t] ?? t;

  const cancelModes = () => {
    setMoveMode(false);
    setMoveSourceCell(null);
    setDeployFromInventoryIdx(null);
  };

  const run = async (fn: () => Promise<void>, fallback: string) => {
    if (busy) return;
    setBusy(true);
    try {
      await fn();
    } catch (e) {
      showToast(e instanceof ApiError ? e.message : fallback);
    } finally {
      setBusy(false);
    }
  };

  const handleCellClick = (x: number, y: number, cell: Cell) => {
    if (moveMode && moveSourceCell) {
      if (cell.type !== 'empty') return;
      const buildingId = grid[moveSourceCell.y][moveSourceCell.x].buildingId;
      if (!buildingId) return;
      void run(async () => {
        await moveBuildingApi(buildingId, x, y);
        cancelModes();
        reloadBuildings();
        showToast('건물을 이동했습니다', false);
      }, '이동에 실패했습니다');
      return;
    }
    if (deployFromInventoryIdx !== null) {
      if (cell.type !== 'empty') return;
      const item = inventory[deployFromInventoryIdx];
      void run(async () => {
        await placeFromInventoryOnTerritory(item.inventoryId, territoryId, x, y);
        cancelModes();
        reloadBuildings();
        reloadInventory();
        showToast('건물을 배치했습니다', false);
      }, '배치에 실패했습니다');
      return;
    }
    setSelectedCell({ x, y });
    if (cell.type === 'empty') setShowBuild(true);
    else setShowBuildingAction(true);
  };

  const handleBuild = () => {
    setBuildError('');
    if (!selectedBuilding) { setBuildError('건물을 선택해주세요.'); return; }
    if (!selectedCell) { setBuildError('그리드에서 빈 셀을 선택해주세요.'); return; }
    const type = catalogByType.get(selectedBuilding);
    if (!type) { setBuildError('건물 정보를 찾을 수 없습니다.'); return; }
    void (async () => {
      try {
        await placeTerritoryBuilding(territoryId, type.buildingTypeId, selectedCell.x, selectedCell.y);
        // 건설은 영토 저장소 GP에서 차감 — 금고(vault)와 무관. 영토 상세를 다시 불러온다.
        setShowBuild(false);
        setSelectedBuilding(null);
        reloadBuildings();
        reloadDetail();
        showToast(`${nameFor(selectedBuilding)} 건설을 시작했습니다`, false);
      } catch (e) {
        setBuildError(e instanceof ApiError ? e.message : '건설에 실패했습니다.');
      }
    })();
  };

  const handleStartMove = () => {
    if (!selectedCell) return;
    setMoveSourceCell(selectedCell);
    setMoveMode(true);
    setShowBuildingAction(false);
  };

  const reloadGarrison = useCallback(() => {
    fetchTerritoryGarrison(territoryId)
      .then(setGarrison)
      .catch(e => console.warn('[TerritoryGridPage] garrison load failed', e));
  }, [territoryId]);

  const handleOpenGarrison = () => {
    if (!selectedCellData?.buildingId) return;
    const cap = GARRISON_CAP[selectedCellData.type] ?? 0;
    setDeployBuilding({ buildingId: selectedCellData.buildingId, name: nameFor(selectedCellData.type), capacityPerLevel: cap });
    setShowBuildingAction(false);
    reloadGarrison();
  };

  const territoryUnits = militaryData?.locations.find(
    l => l.locationType === 'TERRITORY' && l.locationId === territoryId,
  );

  const handleOpenTrain = () => {
    fetchResearch().then(setResearch).catch(e => console.warn('[TerritoryGridPage] research load failed', e));
    setTrainUnitTypeId((unitCatalog.find(u => u.requiredBarracksLevel <= territoryBarracksLevel) ?? unitCatalog[0])?.unitTypeId ?? null);
    setTrainLevel(1);
    setTrainQuantity(1);
    setShowBuildingAction(false);
    setShowTrain(true);
  };

  const handleTrain = async () => {
    if (!trainUnitTypeId || trainQuantity < 1) return;
    setIsTraining(true);
    try {
      const res = await produceUnit(trainUnitTypeId, trainQuantity, territoryId, 'TERRITORY', trainLevel);
      reloadMilitary();
      reloadDetail();
      showToast(`유닛 ${res.quantity}기 훈련 완료`, false);
      setShowTrain(false);
    } catch (e) {
      showToast(e instanceof ApiError ? e.message : '훈련에 실패했습니다', true);
    } finally {
      setIsTraining(false);
    }
  };

  const handleDeploy = async (p: { buildingId: number; unitTypeId: number; quantity: number; sourceLocationId: number; sourceLocationType: 'ISLAND' | 'TERRITORY' }) => {
    setIsGarrisonBusy(true);
    try {
      const res = await deployUnit({ territoryId, ...p });
      reloadMilitary();
      reloadBuildings();
      showToast(`유닛 ${res.deployedCount}기를 주둔시켰습니다`, false);
      setDeployBuilding(null);
    } catch (e) {
      showToast(e instanceof ApiError ? e.message : '주둔에 실패했습니다', true);
    } finally {
      setIsGarrisonBusy(false);
    }
  };

  const handleRecall = async (unitTypeId: number, quantity: number) => {
    setIsGarrisonBusy(true);
    try {
      const res = await recallUnit(territoryId, unitTypeId, quantity);
      reloadMilitary();
      reloadGarrison();
      showToast(`유닛 ${res.recalledCount}기를 회수했습니다`, false);
    } catch (e) {
      showToast(e instanceof ApiError ? e.message : '회수에 실패했습니다', true);
    } finally {
      setIsGarrisonBusy(false);
    }
  };

  const handleStoreBuilding = () => {
    const buildingId = selectedCellData?.buildingId;
    if (!buildingId) return;
    void run(async () => {
      await storeBuildingApi(buildingId);
      setShowBuildingAction(false);
      reloadBuildings();
      reloadInventory();
      showToast('보관함에 넣었습니다', false);
    }, '보관에 실패했습니다');
  };

  const handleRepair = () => {
    const buildingId = selectedCellData?.buildingId;
    if (!buildingId) return;
    void run(async () => {
      const res = await repairBuilding(buildingId);
      setShowBuildingAction(false);
      reloadBuildings();
      reloadDetail();
      showToast(
        `수리 시작 — ${remainingLabel(res.buildCompleteAt, Date.now())} 후 완료 (수리 중 비활성)`,
        false,
      );
    }, '수리에 실패했습니다');
  };

  const handleRepairAll = () => {
    void run(async () => {
      const res = await repairAllBuildings('TERRITORY', territoryId);
      reloadBuildings();
      reloadDetail();
      showToast(
        res.repairedCount > 0
          ? `${res.repairedCount}개 건물 수리 시작 (금고… ${res.totalCost.toLocaleString()} GP 소모)`
          : '수리할 손상 건물이 없거나 저장소 GP가 부족합니다',
        res.repairedCount === 0,
      );
    }, '전체 수리에 실패했습니다');
  };

  const handleUpgradeBuilding = () => {
    const buildingId = selectedCellData?.buildingId;
    if (!buildingId) return;
    void run(async () => {
      const res = await upgradeTerritoryBuilding(buildingId);
      // 업그레이드도 영토 저장소 GP에서 차감 — 금고와 무관.
      setShowBuildingAction(false);
      reloadBuildings();
      reloadDetail();
      showToast(
        res.buildCompleteAt
          ? `Lv.${res.newLevel} 업그레이드 시작 — ${remainingLabel(res.buildCompleteAt, Date.now())} 후 완료`
          : `Lv.${res.newLevel}으로 업그레이드 완료`,
        false,
      );
    }, '업그레이드에 실패했습니다');
  };

  // 나의 섬과 동일한 탭 사이드바 레이아웃.
  const [showZones, setShowZones] = useState(true);
  const [activeTab, setActiveTab] = useState<'buildings' | 'decoration' | 'resources' | 'units' | 'expand'>('buildings');
  const [isResearching, setIsResearching] = useState(false);
  const [researchError, setResearchError] = useState<string | null>(null);

  const reloadResearch = useCallback(() => {
    fetchResearch().then(setResearch).catch(e => console.warn('[TerritoryGridPage] research load failed', e));
  }, []);
  useEffect(() => { reloadResearch(); }, [reloadResearch]);

  const handleResearch = async (unitTypeId: number) => {
    setIsResearching(true);
    setResearchError(null);
    try {
      const res = await startResearch(unitTypeId);
      syncGP(res.vaultGpRemaining);
      reloadResearch();
      showToast('연구를 시작했습니다', false);
    } catch (e) {
      setResearchError(e instanceof ApiError ? e.message : '연구 시작에 실패했습니다');
    } finally {
      setIsResearching(false);
    }
  };

  // 실제 건물 개수 = 앵커 칸(!isBody)만 센다. 2x2 건물이 칸 수(4)로 부풀지 않도록.
  const countBuildings = (type: BuildingType) => grid.flat().filter(c => c.type === type && !c.isBody).length;
  const statDesc = (c: BuildingTypeInfo) => {
    const parts: string[] = [];
    if (c.gpProductionRate) parts.push(`GP +${c.gpProductionRate}/시간`);
    if (c.foodProductionRate) parts.push(`식량 +${c.foodProductionRate}/시간`);
    if (c.unitCapacityPerLevel) parts.push(`유닛 +${c.unitCapacityPerLevel}/레벨`);
    if (c.defensePower) parts.push(`방어력 +${c.defensePower}`);
    if (parts.length === 0) {
      const byName: Record<string, string> = {
        BARRACKS: '유닛 생산 · 레벨↑ 상위 유닛 해금',
        STORAGE: 'GP·식량 저장 (약탈 대상)',
        RESEARCH_LAB: '유닛 레벨 연구 · 레벨↑ 연구 상한↑',
      };
      return byName[c.name] ?? '장식';
    }
    return parts.join(' · ');
  };
  const buildingCard = (c: BuildingTypeInfo) => {
    const type = c.name.toLowerCase() as BuildingType;
    const color = colorFor(type);
    const count = countBuildings(type);
    return (
      <div key={c.buildingTypeId} className="bg-panel-deep rounded-xl p-2.5 flex items-center gap-2">
        <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: color + '25', border: `1px solid ${color}50` }}>
          <span className="text-base">{iconFor(type)}</span>
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between">
            <span className="text-xs" style={{ color }}>{nameFor(type)}</span>
            <span className="text-muted text-[10px]">×{count}</span>
          </div>
          <p className="text-muted text-[10px]">{statDesc(c)}</p>
        </div>
      </div>
    );
  };
  const functionalCatalog = catalog.filter(c => c.category !== 'DECORATIVE');
  const decorativeCatalog = catalog.filter(c => c.category === 'DECORATIVE');
  // 총 방어력 = 배치 건물 방어력(방벽·타워) + 주둔 유닛 방어력(배치 수 × 방어력).
  const buildingDefense = grid.flat()
    .filter(c => c.type !== 'empty' && !c.isBody)
    .reduce((sum, c) => sum + (catalogByType.get(c.type)?.defensePower ?? 0), 0);
  const garrisonDefense = (militaryData?.locations.find(l => l.locationType === 'TERRITORY' && l.locationId === territoryId)?.units ?? [])
    .reduce((sum, u) => sum + u.defensePower * u.deployedCount, 0);
  const totalDefense = buildingDefense + garrisonDefense;
  const zoneBg = (zone: 1 | 2 | 3) => (showZones ? zoneColor[zone] : 'var(--color-surface)');

  if (!territoryId) return <div className="page-root"><GNB /><p className="text-danger p-6">잘못된 영토입니다.</p></div>;

  return (
    <div className="page-root">
      <GNB />
      {toast && <IslandToast message={toast.message} isError={toast.isError} onClose={() => setToast(null)} />}

      <div className="bg-surface border-b border-outline px-5 py-3 flex items-center gap-4 flex-shrink-0">
        <button onClick={() => navigate('/app/map')} className="text-muted hover:text-foreground mr-1">←</button>
        <div className="w-10 h-10 bg-elevated rounded-xl border border-outline flex items-center justify-center">
          <span className="text-xl">🏰</span>
        </div>
        <div>
          <h1 className="text-foreground font-bold text-xl">{detail ? `영토 (${detail.coordX}, ${detail.coordY})` : `영토 #${id}`}</h1>
          <p className="text-muted text-xs">{detail?.continentName ?? '—'} · {detail?.grade ?? '-'}급 영토 · {gridSize}×{gridSize} 그리드</p>
        </div>
        <div className="flex items-center gap-2 ml-4">
          <div className="h-7 px-3 rounded-lg bg-[#ffd70020] border border-gold flex items-center">
            <span className="text-gold font-bold text-[11px]">{detail?.grade ?? '-'}급</span>
          </div>
          {detail?.isInvincible && (
            <div className="flex items-center gap-1 bg-elevated border border-gp rounded-lg px-2 py-1">
              <div className="w-2 h-2 bg-gp rounded-full animate-pulse" />
              <span className="text-gp text-[11px]">🛡 무적 보호 중</span>
            </div>
          )}
        </div>
        <div className="flex items-center gap-4 ml-auto">
          <div className="text-right" title="계정 금고(vault)의 GP.">
            <p className="text-muted text-[10px]">금고 GP</p>
            <p className="text-gp font-bold text-base">💎 {gp.toLocaleString()}</p>
          </div>
          <div className="text-right" title="이 영토 저장소의 GP. 건물 건설·유닛 생산에 차감되는 값. 최대치는 저장소 레벨이 관리.">
            <p className="text-muted text-[10px]">영토 저장 GP <span className="text-[9px]">(📦 저장소 관리)</span></p>
            <p className="font-bold text-base">
              <span className="text-gold">🏰 {(detail?.storedGp ?? 0).toLocaleString()}</span>
              <span className="text-muted text-[11px]"> / {(detail?.storageCapacity ?? 0).toLocaleString()}</span>
            </p>
          </div>
          <div className="text-right">
            <p className="text-muted text-[10px]">총 방어력</p>
            <p className="text-danger font-bold text-base">{totalDefense.toLocaleString()}</p>
          </div>
          <label className="flex items-center gap-2 cursor-pointer">
            <div
              className="w-10 h-5 rounded-full relative transition-colors"
              style={{ background: showZones ? '#00ff8840' : '#2a3050', border: `1px solid ${showZones ? '#00ff88' : '#354064'}` }}
              onClick={() => setShowZones(p => !p)}
            >
              <div className="absolute top-0.5 w-4 h-4 rounded-full transition-all" style={{ background: showZones ? '#00ff88' : '#7788a5', left: showZones ? 20 : 2 }} />
            </div>
            <span className="text-muted text-[11px]">존 표시</span>
          </label>
          <button
            onClick={() => navigate(`/app/territory/${territoryId}`)}
            className="h-8 px-3 rounded-lg border border-primary/50 text-primary text-[12px] hover:bg-primary/10 transition-colors"
          >
            영토 상세 →
          </button>
        </div>
      </div>

      {error && <p className="text-danger text-xs px-5 py-2">⚠ {error}</p>}

      {moveMode && (
        <div className="flex items-center justify-between px-5 py-2 flex-shrink-0 border-b border-[#ffd70060]" style={{ background: '#1a1200' }}>
          <div className="flex items-center gap-2">
            <span className="text-sm">🔄</span>
            <span className="text-gold font-semibold text-[13px]">
              이동 모드 — 이동할 빈 셀을 클릭하세요
              {moveSourceCell && <span className="text-muted ml-2 text-[11px]">출발: ({moveSourceCell.x}, {moveSourceCell.y})</span>}
            </span>
          </div>
          <button onClick={cancelModes} className="h-7 px-3 rounded-lg border transition-colors text-xs" style={{ color: '#ffd700', borderColor: '#ffd70060' }}>취소</button>
        </div>
      )}
      {deployFromInventoryIdx !== null && inventory[deployFromInventoryIdx] && (
        <div className="flex items-center justify-between px-5 py-2 flex-shrink-0 border-b border-[#00ff8860]" style={{ background: '#001a10' }}>
          <div className="flex items-center gap-2">
            <span className="text-sm">📦</span>
            <span className="text-gp font-semibold text-[13px]">
              배치 모드 — 배치할 빈 셀을 클릭하세요
              <span className="text-muted ml-2 text-[11px]">
                ({nameFor(inventory[deployFromInventoryIdx].buildingType.toLowerCase() as BuildingType)})
              </span>
            </span>
          </div>
          <button onClick={cancelModes} className="h-7 px-3 rounded-lg border transition-colors text-xs" style={{ color: '#00ff88', borderColor: '#00ff8860' }}>취소</button>
        </div>
      )}

      <div className="flex flex-1 overflow-hidden">
        <div className="flex-1 flex flex-col overflow-hidden bg-[#070c18]">
          {/* Legend + selection hint */}
          <div className="px-4 pt-3 pb-2 flex justify-between items-center flex-shrink-0">
            <div className="flex gap-3">
              {[3, 2, 1].map(z => (
                <div key={z} className="flex items-center gap-1">
                  <div className="w-3 h-3 rounded-sm border" style={{ background: zoneColor[z as 1|2|3], borderColor: zoneBorder[z as 1|2|3] }} />
                  <span className="text-muted text-[10px]">Zone {z}</span>
                </div>
              ))}
            </div>
            <span className="text-muted text-[10px]">
              {selectedCell
                ? `선택: (${selectedCell.x}, ${selectedCell.y}) - ${selectedCellData && selectedCellData.type !== 'empty' ? nameFor(selectedCellData.type) : '빈 셀'}`
                : '셀을 클릭하여 선택'}
            </span>
          </div>
          <div className="flex-1 p-5 overflow-auto">
            <div className="grid gap-1 max-w-[680px] mx-auto" style={{ gridTemplateColumns: `repeat(${gridSize}, 1fr)` }}>
              {grid.map((row, y) =>
                row.map((cell, x) => {
                  const isSelected = selectedCell?.x === x && selectedCell?.y === y;
                  const isMoveSource = moveSourceCell?.x === x && moveSourceCell?.y === y;
                  const isActionTarget = (moveMode || deployFromInventoryIdx !== null) && cell.type === 'empty';
                  const building = isUnderConstruction(cell.buildCompleteAt, now);
                  const color = colorFor(cell.type);
                  const zone = (cell.zone ?? 3) as 1 | 2 | 3;
                  const bg = isMoveSource ? color + '80'
                    : cell.type !== 'empty' ? color + '60'
                    : zoneBg(zone);
                  return (
                    <div
                      key={`${x}-${y}`}
                      onClick={() => handleCellClick(x, y, cell)}
                      className="relative aspect-square rounded cursor-pointer flex items-center justify-center transition-all hover:opacity-90"
                      style={{
                        background: bg,
                        border: isMoveSource
                          ? '2px solid #ffd700'
                          : isSelected
                            ? '2px solid #00f5ff'
                            : isActionTarget
                              ? '1px dashed #00ff8880'
                              : cell.type !== 'empty'
                                ? `1px solid ${color}80`
                                : showZones ? `1px solid ${zoneBorder[zone]}30` : '1px solid var(--color-outline-soft)',
                        boxShadow: isMoveSource ? '0 0 8px #ffd700' : isSelected ? '0 0 8px #00f5ff' : undefined,
                      }}
                    >
                      {cell.type !== 'empty' ? (
                        <div className="flex flex-col items-center justify-center h-full w-full p-1">
                          {!cell.isBody && (
                            <span className="text-[13px] leading-none">{building ? '🔨' : iconFor(cell.type)}</span>
                          )}
                          {!cell.isBody && building && (
                            <span className="text-[7px] text-gold font-bold">{remainingLabel(cell.buildCompleteAt, now)}</span>
                          )}
                          {!cell.isBody && !building && cell.level && (
                            <div className="w-full mt-0.5">
                              <HealthBar hp={cell.hp!} maxHp={cell.maxHp!} color={color} height="h-1" />
                              <span className="text-[7px]" style={{ color }}>Lv.{cell.level}</span>
                            </div>
                          )}
                        </div>
                      ) : (
                        <span className={isActionTarget ? 'text-sm' : 'text-base'} style={{ color: isActionTarget ? '#00ff8860' : '#354064' }}>
                          {isActionTarget ? '⊕' : '+'}
                        </span>
                      )}
                    </div>
                  );
                })
              )}
            </div>
          </div>
        </div>

        {/* Right — tabbed sidebar (나의 섬 동일 레이아웃) */}
        <div className="w-[260px] bg-surface border-l border-outline flex flex-col flex-shrink-0">
          <div className="flex border-b border-outline">
            {(['buildings', 'decoration', 'resources', 'units', 'expand'] as const).map(tabId => (
              <button key={tabId} onClick={() => setActiveTab(tabId)} className={`flex-1 py-2.5 text-[11px] transition-colors border-b-2 ${activeTab === tabId ? 'text-gp border-gp bg-[#00ff8810]' : 'text-muted border-transparent bg-transparent'}`}>
                {{ buildings: '건물', decoration: '장식', resources: '자원', units: '유닛', expand: '확장' }[tabId]}
              </button>
            ))}
          </div>

          <div className="flex-1 overflow-y-auto">
            {activeTab === 'buildings' && (
              <div className="p-3 space-y-2">
                {functionalCatalog.length > 0 ? functionalCatalog.map(buildingCard) : <p className="text-muted text-[11px] text-center py-6">건물 종류를 불러오는 중…</p>}
              </div>
            )}
            {activeTab === 'decoration' && (
              <div className="p-3 space-y-2">
                {decorativeCatalog.length > 0 ? decorativeCatalog.map(buildingCard) : <p className="text-muted text-[11px] text-center py-6">장식 건물이 없습니다</p>}
              </div>
            )}
            {activeTab === 'resources' && (
              <div className="p-3 space-y-3">
                <div className="bg-panel-deep rounded-xl p-3"><div className="flex justify-between"><span className="text-gold font-semibold text-xs">⚡ AP</span><span className="text-gold font-bold text-sm">{ap.toLocaleString()}</span></div></div>
                <div className="bg-panel-deep rounded-xl p-3"><div className="flex justify-between"><span className="text-gp font-semibold text-xs">🏰 영토 저장 GP</span><span className="text-gp font-bold text-sm">{(detail?.storedGp ?? 0).toLocaleString()} / {(detail?.storageCapacity ?? 0).toLocaleString()}</span></div></div>
                <IslandResearchPanel research={research} isBusy={isResearching} error={researchError} onResearch={handleResearch} />
              </div>
            )}
            {activeTab === 'units' && (
              <div className="p-3 space-y-2">
                <div className="flex items-center justify-between mb-1">
                  <p className="text-muted font-semibold text-xs">주둔 유닛</p>
                  {territoryUnits && <span className="text-[10px] text-gold">🌾 식량 {(territoryUnits.storedFood ?? 0).toLocaleString()}</span>}
                </div>
                {(territoryUnits?.units ?? []).map(u => {
                  const fb = UNIT_LABELS[u.name] ?? { label: u.name, icon: '⚔', color: '#e0e8ff' };
                  const meta = { label: u.displayName ?? fb.label, icon: u.icon ?? fb.icon, color: u.colorHex ?? fb.color };
                  return (
                    <div key={u.unitTypeId} className="bg-panel-deep rounded-xl p-3">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-base">{meta.icon}</span>
                        <div className="flex-1">
                          <div className="flex justify-between"><span className="text-xs" style={{ color: meta.color }}>{meta.label}</span><span className="text-muted text-[11px]">{u.quantity}개</span></div>
                          <p className="text-muted text-[9px]">대기 {u.idleCount} · 배치 {u.deployedCount} · 공격력 {u.attackPower}</p>
                        </div>
                      </div>
                    </div>
                  );
                })}
                {(territoryUnits?.units ?? []).length === 0 && <p className="text-muted text-xs text-center py-4">주둔 유닛이 없습니다</p>}
                <button onClick={handleOpenTrain} className="w-full h-9 border border-secondary rounded-xl text-secondary text-xs hover:bg-[#8b50ff20] transition-colors">유닛 훈련하기</button>
              </div>
            )}
            {activeTab === 'expand' && (
              <div className="p-3 flex flex-col items-center justify-center gap-3 py-12">
                <span className="text-[40px]">🚧</span>
                <p className="text-foreground font-semibold text-sm">준비 중</p>
                <p className="text-muted text-[11px] text-center">영토 확장 기능은 추후 업데이트 예정입니다</p>
              </div>
            )}
          </div>

          {isOwner && (
            <div className="p-3 border-t border-outline space-y-2">
              <button onClick={() => { setSelectedCell(null); setShowBuild(true); }} className="w-full h-9 border border-primary rounded-xl text-primary text-xs hover:bg-primary/10 transition-colors">🏗 건물 건설</button>
              <button onClick={() => setShowInventory(true)} className="relative w-full h-9 border border-secondary text-secondary rounded-xl text-xs transition-colors hover:bg-[#8b50ff20]">
                📦 보관함
                {inventory.length > 0 && (<span className="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-secondary text-white text-[10px] flex items-center justify-center">{inventory.length}</span>)}
              </button>
              <button
                onClick={handleRepairAll}
                disabled={busy}
                className="w-full h-9 border border-gp text-gp rounded-xl text-xs transition-colors hover:bg-gp/10 disabled:opacity-40"
                title="손상된 모든 건물을 시간제 수리(저장소 GP 차감). 수리 중 건물은 비활성."
              >
                🔧 전체 수리
              </button>
              <button onClick={() => navigate('/app/map')} className="w-full h-9 bg-elevated border border-outline rounded-xl text-muted text-xs">🗺 월드맵으로</button>
            </div>
          )}
        </div>
      </div>

      {showBuild && (
        <TerritoryGridBuildModal
          selectedCell={selectedCell}
          selectedZone={selectedCellData?.zone}
          gp={gp}
          catalog={catalog}
          selectedBuilding={selectedBuilding}
          buildError={buildError}
          onSelectBuilding={setSelectedBuilding}
          onClose={() => { setShowBuild(false); setSelectedBuilding(null); setBuildError(''); }}
          onBuild={handleBuild}
        />
      )}

      {showBuildingAction && selectedCell && selectedCellData && selectedCellData.type !== 'empty' && (
        <TerritoryGridBuildingActionPanel
          selectedCell={selectedCell}
          cellData={selectedCellData}
          isUnderConstruction={isUnderConstruction(selectedCellData.buildCompleteAt, now)}
          color={colorFor(selectedCellData.type)}
          name={nameFor(selectedCellData.type)}
          busy={busy}
          onStartMove={handleStartMove}
          onStoreBuilding={handleStoreBuilding}
          onUpgrade={handleUpgradeBuilding}
          onVaultTransfer={() => navigate('/app/vault')}
          onGarrison={handleOpenGarrison}
          onTrain={handleOpenTrain}
          onRepair={handleRepair}
          onClose={() => setShowBuildingAction(false)}
        />
      )}

      {showTrain && (
        <IslandTrainUnitModal
          units={unitCatalog}
          islandGp={detail?.storedGp ?? 0}
          storedFood={territoryUnits?.storedFood ?? 0}
          maxBarracksLevel={territoryBarracksLevel}
          trainUnitTypeId={trainUnitTypeId}
          trainQuantity={trainQuantity}
          trainLevel={trainLevel}
          researchedLevels={researchedLevels}
          isTraining={isTraining}
          onSelectUnit={id => { setTrainUnitTypeId(id); setTrainLevel(1); }}
          onChangeQuantity={setTrainQuantity}
          onChangeLevel={setTrainLevel}
          onTrain={handleTrain}
          onClose={() => setShowTrain(false)}
        />
      )}

      {deployBuilding && (
        <TerritoryDeployModal
          building={deployBuilding}
          locations={militaryData?.locations ?? []}
          garrison={garrison}
          isBusy={isGarrisonBusy}
          onDeploy={handleDeploy}
          onRecall={handleRecall}
          onClose={() => setDeployBuilding(null)}
        />
      )}

      {showInventory && (
        <TerritoryGridInventoryModal
          inventory={inventory}
          catalog={catalog}
          onDeploy={(idx) => { setDeployFromInventoryIdx(idx); setShowInventory(false); }}
          onClose={() => setShowInventory(false)}
        />
      )}
    </div>
  );
}
