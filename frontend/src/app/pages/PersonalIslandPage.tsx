import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { GNB } from '../components/GNB';
import { LoadingState } from '../components/LoadingState';
import { useApp } from '../context/AppContext';
import { useIsland } from '../hooks/useIsland';
import { useMilitary } from '../hooks/useMilitary';
import { storeBuilding as storeBuildingApi, moveBuilding as moveBuildingApi, placeIslandBuilding, fetchBuildingInventory, placeFromInventoryOnIsland, harvestIslandGp, upgradeBuilding as upgradeBuildingApi, rushBuilding as rushBuildingApi, activateProductionBoost, fetchBuildingTypes } from '../api/island';
import { produceUnit, fetchResearch, startResearch, fetchUnitTypes } from '../api/military';
import type { ResearchStatus, UnitTypeCatalog } from '../types/military';
import { ApiError } from '../api/client';
import type { InventoryItem, BuildingTypeInfo } from '../types/island';
import {
  type BuildingType, type Cell,
  buildingColors, buildingLabels, buildingNames,
  UNIT_LABELS,
  emptyGrid, buildGridFromIsland, findOriginCell, clearBuildingCells, isUnderConstruction, remainingLabel,
} from './islandGrid';
import { IslandToast } from './IslandToast';
import { IslandBuildModal } from './IslandBuildModal';
import { IslandBuildingActionPanel } from './IslandBuildingActionPanel';
import { IslandTrainUnitModal } from './IslandTrainUnitModal';
import { IslandResearchPanel } from './IslandResearchPanel';
import { IslandInventoryModal } from './IslandInventoryModal';
import { IslandDecorationShopModal } from './IslandDecorationShopModal';

// 백엔드 StoragePolicy.*_CAPACITY_PER_LEVEL 과 일치 — 성·저장소 레벨당 GP·식량 저장 용량.
const STORAGE_CAP_PER_LEVEL = 5000;

// 섬 등급 사다리 — 백엔드 db/island-grades.yml 과 일치. 성(Castle) 레벨업 시 castleLevelRequired가 맞는 등급으로 자동 승격된다.
// 성 최대 레벨은 현재 3(BuildingPolicy.MAX_LEVEL)이라 A·S 는 아직 도달 불가(미출시).
const CASTLE_MAX_LEVEL = 3;
const ISLAND_GRADES = [
  { name: 'D', gridSize: 10, castleLevelRequired: 1 },
  { name: 'C', gridSize: 12, castleLevelRequired: 2 },
  { name: 'B', gridSize: 16, castleLevelRequired: 3 },
  { name: 'A', gridSize: 18, castleLevelRequired: 5 },
  { name: 'S', gridSize: 20, castleLevelRequired: 6 },
] as const;

export function PersonalIslandPage() {
  const navigate = useNavigate();
  const { ap, gp, username, syncAP, syncGP } = useApp();
  const { island, reload: reloadIsland } = useIsland();
  const { data: militaryData, isLoading: isMilitaryLoading, reload: reloadMilitary } = useMilitary();
  // 유닛·식량은 위치별로 그룹핑돼 내려온다 — 이 페이지는 섬 위치만 본다.
  const islandMilitary = militaryData?.locations.find(l => l.locationType === 'ISLAND');
  const islandUnits = islandMilitary?.units ?? [];
  const islandFood = islandMilitary?.storedFood ?? 0;
  const gridSize = island?.gridSize ?? 10;
  const [selectedCell, setSelectedCell] = useState<{ x: number; y: number } | null>(null);
  const [showBuild, setShowBuild] = useState(false);
  const [grid, setGrid] = useState<Cell[][]>(() => emptyGrid(10, 2, 4));

  useEffect(() => {
    if (island) setGrid(buildGridFromIsland(island));
  }, [island]);

  // 건설 중인 건물이 있는 동안만 1초마다 남은 시간을 갱신하고, 완료 시점에 섬을 다시 불러온다.
  const [now, setNow] = useState(() => Date.now());
  // 서버 buildersInUse 는 폴링 사이에 낡으므로 타이머 기준으로 다시 센다.
  const buildersInUse = island?.buildings.filter(b => isUnderConstruction(b.buildCompleteAt, now)).length ?? 0;
  const builderCount = island?.builderCount ?? 1;
  const isBuilderFull = buildersInUse >= builderCount;
  // 완공된 병영의 최고 레벨 — 훈련 모달에서 상위 유닛 잠금 판정에 쓴다.
  const islandBarracksLevel = (island?.buildings ?? [])
    .filter(b => b.type.toLowerCase() === 'barracks' && !b.isDestroyed && !isUnderConstruction(b.buildCompleteAt, now))
    .reduce((max, b) => Math.max(max, b.level), 0);
  // 성 레벨 — 섬 등급 승격 기준(성은 섬당 1개). 확장 탭에서 현재/다음 등급 판정에 쓴다.
  const castleLevel = (island?.buildings ?? [])
    .filter(b => b.type.toLowerCase() === 'castle' && !b.isDestroyed)
    .reduce((max, b) => Math.max(max, b.level), 0);
  const hasConstruction = buildersInUse > 0;
  // 건설 중이거나 생산 부스터가 남아있는 동안 매초 시간을 갱신한다(카운트다운·완료 반영).
  const boostEndMs = island?.productionBoostUntil ? new Date(island.productionBoostUntil).getTime() : 0;
  const isTicking = hasConstruction || boostEndMs > now;
  useEffect(() => {
    if (!isTicking) return;
    const timer = setInterval(() => {
      const next = Date.now();
      setNow(next);
      const stillBuilding = island?.buildings.some(b => isUnderConstruction(b.buildCompleteAt, next));
      if (hasConstruction && !stillBuilding) void reloadIsland();
    }, 1000);
    return () => clearInterval(timer);
  }, [isTicking, hasConstruction, island, reloadIsland]);
  const [selectedBuilding, setSelectedBuilding] = useState<BuildingType | null>(null);
  const [catalog, setCatalog] = useState<BuildingTypeInfo[]>([]);
  useEffect(() => {
    fetchBuildingTypes().then(setCatalog).catch(e => console.warn('[PersonalIslandPage] building types load failed', e));
  }, []);
  // 관리자 지정 아이콘/색 우선, 없으면 기본 매핑
  const catalogByType = new Map(catalog.map(c => [c.name.toLowerCase(), c]));
  const iconFor = (t: BuildingType) => catalogByType.get(t)?.icon ?? buildingLabels[t] ?? '🏗';
  const colorFor = (t: BuildingType) => catalogByType.get(t)?.colorHex ?? buildingColors[t] ?? '#8892b0';
  const nameFor = (t: BuildingType) => catalogByType.get(t)?.displayName ?? buildingNames[t] ?? t;
  const statDesc = (c: BuildingTypeInfo) => {
    const parts: string[] = [];
    if (c.gpProductionRate) parts.push(`GP +${c.gpProductionRate}/시간`);
    if (c.foodProductionRate) parts.push(`식량 +${c.foodProductionRate}/시간`);
    if (c.unitCapacityPerLevel) parts.push(`유닛 +${c.unitCapacityPerLevel}/레벨`);
    if (c.defensePower) parts.push(`방어력 +${c.defensePower}`);
    // 병영·저장소·연구소는 기능이 숫자 속성이 아니라 코드 규칙에 있어 별도 설명한다.
    if (parts.length === 0) {
      const byName: Record<string, string> = {
        BARRACKS: '유닛 생산 · 레벨↑ 상위 유닛 해금',
        STORAGE: `GP·식량 저장 +${STORAGE_CAP_PER_LEVEL.toLocaleString()}/레벨 (약탈 대상)`,
        RESEARCH_LAB: '유닛 레벨 연구 · 레벨↑ 연구 상한↑',
      };
      return byName[c.name] ?? '장식';
    }
    return parts.join(' · ');
  };
  const [buildError, setBuildError] = useState('');
  const [showZones, setShowZones] = useState(true);
  const [activeTab, setActiveTab] = useState<'buildings' | 'decoration' | 'resources' | 'units' | 'expand'>('buildings');

  // Building action panel (for occupied cells)
  const [showBuildingAction, setShowBuildingAction] = useState(false);

  // Move mode
  const [moveMode, setMoveMode] = useState(false);
  const [moveSourceCell, setMoveSourceCell] = useState<{ x: number; y: number } | null>(null);

  // Inventory (서버 보관함)
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [showInventory, setShowInventory] = useState(false);
  const [showShop, setShowShop] = useState(false);
  const [deployFromInventoryIdx, setDeployFromInventoryIdx] = useState<number | null>(null);

  // 연구
  const [research, setResearch] = useState<ResearchStatus | null>(null);
  const [isResearching, setIsResearching] = useState(false);
  const [researchError, setResearchError] = useState<string | null>(null);
  const reloadResearch = useCallback(() => {
    fetchResearch()
      .then(setResearch)
      .catch(e => console.warn('[PersonalIslandPage] research load failed', e));
  }, []);
  useEffect(() => { reloadResearch(); }, [reloadResearch]);

  const handleResearch = async (unitTypeId: number) => {
    setIsResearching(true);
    setResearchError(null);
    try {
      const res = await startResearch(unitTypeId);
      syncGP(res.vaultGpRemaining); // 연구비는 금고에서 차감 — 헤더 금고 GP 즉시 반영
      reloadResearch();
      showToast('연구를 시작했습니다', false);
    } catch (e) {
      setResearchError(e instanceof ApiError ? e.message : '연구 시작에 실패했습니다');
    } finally {
      setIsResearching(false);
    }
  };

  // 유닛 훈련 모달 — 선택 목록은 보유 유닛이 아니라 전체 종류 카탈로그를 소스로 한다.
  const [unitCatalog, setUnitCatalog] = useState<UnitTypeCatalog[]>([]);
  useEffect(() => {
    fetchUnitTypes().then(setUnitCatalog).catch(e => console.warn('[PersonalIslandPage] unit types load failed', e));
  }, []);
  const [showTrainModal, setShowTrainModal] = useState(false);
  const [trainUnitTypeId, setTrainUnitTypeId] = useState<number | null>(null);
  const [trainQuantity, setTrainQuantity] = useState(1);
  const [trainLevel, setTrainLevel] = useState(1);
  const [isTraining, setIsTraining] = useState(false);
  // unitTypeId → 연구 해금 레벨
  const researchedLevels = Object.fromEntries(
    (research?.units ?? []).map(u => [u.unitTypeId, u.researchedLevel]),
  ) as Record<number, number>;

  // 건설 위치 선택 모드 (사이드바 버튼 → 셀 클릭)
  const [buildPending, setBuildPending] = useState(false);

  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [toast, setToast] = useState<{ message: string; isError: boolean } | null>(null);
  const showToast = useCallback((message: string, isError = true) => {
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    setToast({ message, isError });
    toastTimerRef.current = setTimeout(() => setToast(null), 3500);
  }, []);

  const reloadInventory = useCallback(() => {
    fetchBuildingInventory().then(setInventory).catch((e) => console.warn('[PersonalIslandPage] inventory reload failed', e));
  }, []);

  const handleUpgradeBuilding = async () => {
    const buildingId = selectedCellData?.buildingId;
    if (!buildingId) return;
    try {
      const result = await upgradeBuildingApi(buildingId);
      // 업그레이드는 섬 저장소 GP에서 차감된다 — 금고(vault)와 무관. 섬만 다시 불러온다.
      void reloadIsland();
      setShowBuildingAction(false);
      showToast(
        result.buildCompleteAt
          ? `Lv.${result.newLevel} 업그레이드 시작 — ${remainingLabel(result.buildCompleteAt, Date.now())} 후 완료 (${result.upgradeCost.toLocaleString()} GP 소모)`
          : `Lv.${result.newLevel}으로 업그레이드 완료 (${result.upgradeCost.toLocaleString()} GP 소모)`,
        false,
      );
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : '업그레이드에 실패했습니다');
    }
  };

  const handleRush = async () => {
    const buildingId = selectedCellData?.buildingId;
    if (!buildingId) return;
    try {
      const result = await rushBuildingApi(buildingId);
      syncAP(result.apRemaining);
      void reloadIsland();
      setShowBuildingAction(false);
      showToast(`AP ${result.apSpent.toLocaleString()} 소모 — 즉시 완료`, false);
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : '즉시 완료에 실패했습니다');
    }
  };

  const handleProduceUnit = async () => {
    if (!trainUnitTypeId || trainQuantity < 1 || !island) return;
    setIsTraining(true);
    try {
      await produceUnit(trainUnitTypeId, trainQuantity, island.islandId, 'ISLAND', trainLevel);
      // 유닛 생산은 섬 저장소 GP·식량에서 차감 — 섬·유닛 현황을 다시 불러온다(금고 무관).
      void reloadIsland();
      void reloadMilitary();
      setShowTrainModal(false);
      showToast(`유닛 ${trainQuantity}개 훈련 완료`, false);
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : '훈련에 실패했습니다');
    } finally {
      setIsTraining(false);
    }
  };

  useEffect(() => { reloadInventory(); }, [reloadInventory]);

  const selectedCellData = selectedCell ? grid[selectedCell.y]?.[selectedCell.x] : null;

  const cancelModes = () => {
    setMoveMode(false);
    setMoveSourceCell(null);
    setDeployFromInventoryIdx(null);
    setBuildPending(false);
  };

  const handleCellClick = (x: number, y: number, cell: { type: BuildingType; level?: number; hp?: number; maxHp?: number; zone?: 1 | 2 | 3 | 4 }) => {
    if (buildPending) {
      if (cell.type === 'empty') {
        setSelectedCell({ x, y });
        setBuildPending(false);
        setShowBuild(true);
      }
      return;
    }
    if (moveMode && moveSourceCell) {
      if (cell.type === 'empty') {
        const sourceCell = grid[moveSourceCell.y][moveSourceCell.x];
        if (!sourceCell.buildingId) { cancelModes(); return; }
        if (sourceCell.type === 'castle') {
          showToast('성은 이동할 수 없습니다');
          cancelModes();
          return;
        }

        const origin = findOriginCell(grid, sourceCell.buildingId);
        if (!origin) { cancelModes(); return; }
        const originCell = grid[origin.y][origin.x];
        const w = originCell.width ?? 1;
        const h = originCell.height ?? 1;

        moveBuildingApi(sourceCell.buildingId, x, y).catch((err) => {
          void reloadIsland();
          showToast(err instanceof ApiError ? err.message : '이동에 실패했습니다');
        });
        setGrid(prev => {
          let next = clearBuildingCells(prev, sourceCell.buildingId!);
          for (let dy = 0; dy < h; dy++) {
            for (let dx = 0; dx < w; dx++) {
              const destX = x + dx;
              const destY = y + dy;
              if (destY < next.length && destX < next[destY].length) {
                next = next.map((row, ry) => row.map((c, rx) =>
                  ry === destY && rx === destX
                    ? { ...originCell, zone: prev[destY][destX].zone, isBody: dx > 0 || dy > 0 }
                    : c
                ));
              }
            }
          }
          return next;
        });
        cancelModes();
      }
      return;
    }
    if (deployFromInventoryIdx !== null) {
      if (cell.type === 'empty') {
        const item = inventory[deployFromInventoryIdx];
        const itemType = item.buildingType.toLowerCase() as BuildingType;
        const zone = grid[y][x].zone;
        if (itemType === 'castle' && zone !== 1) return;
        setGrid(prev => {
          const next = prev.map(row => row.map(c => ({ ...c })));
          next[y][x] = { type: itemType, level: 1, hp: 0, maxHp: 0, zone };
          return next;
        });
        setInventory(prev => prev.filter((_, i) => i !== deployFromInventoryIdx));
        setDeployFromInventoryIdx(null);
        placeFromInventoryOnIsland(item.inventoryId, x, y)
          .then(() => { reloadInventory(); void reloadIsland(); })
          .catch((err) => {
            reloadInventory();
            void reloadIsland();
            showToast(err instanceof ApiError ? err.message : '배치에 실패했습니다');
          });
      }
      return;
    }
    setSelectedCell({ x, y });
    if (cell.type === 'empty') {
      setShowBuild(true);
    } else {
      setShowBuildingAction(true);
    }
  };

  const handleStartMove = () => {
    if (!selectedCell) return;
    setMoveSourceCell(selectedCell);
    setMoveMode(true);
    setShowBuildingAction(false);
  };

  const handleStoreBuilding = () => {
    if (!selectedCell) return;
    const cell = grid[selectedCell.y][selectedCell.x];
    if (!cell.buildingId) return;
    if (cell.type === 'castle') {
      showToast('성은 보관함에 담을 수 없습니다');
      return;
    }
    const buildingId = cell.buildingId;
    storeBuildingApi(buildingId)
      .then(() => reloadInventory())
      .catch((err) => {
        void reloadIsland();
        showToast(err instanceof ApiError ? err.message : '보관에 실패했습니다');
      });
    setGrid(prev => clearBuildingCells(prev, buildingId));
    setShowBuildingAction(false);
  };

  const [isBuilding, setIsBuilding] = useState(false);
  const isBuildingRef = useRef(false);
  const [isHarvesting, setIsHarvesting] = useState(false);
  const [isBoosting, setIsBoosting] = useState(false);
  // 생산 부스터 활성 여부 — 종료 시각이 미래면 활성 (boostEndMs 는 상단에서 계산).
  const isBoostActive = boostEndMs > now;
  const boostRemainingLabel = (() => {
    const secs = Math.max(0, Math.floor((boostEndMs - now) / 1000));
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
  })();

  const handleBoost = async () => {
    if (isBoosting || isBoostActive) return;
    setIsBoosting(true);
    try {
      const result = await activateProductionBoost();
      syncAP(result.apRemaining);
      void reloadIsland();
      showToast(`생산 부스터 ×${result.multiplier} 발동 (AP ${result.apSpent.toLocaleString()} 소모)`, false);
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : '부스터 발동에 실패했습니다');
    } finally {
      setIsBoosting(false);
    }
  };

  const handleHarvest = async () => {
    if (isHarvesting) return;
    setIsHarvesting(true);
    // 수확 전 누적량 — 0 수확이 '누적 없음'인지 '저장소 가득'인지 구분에 쓴다.
    const pendingGp = island?.accumulatedGp ?? 0;
    try {
      const res = await harvestIslandGp();
      // 수확분은 섬 저장소에 적립된다 — 섬을 다시 불러오면 섬 저장 GP에 반영(금고 무관).
      void reloadIsland();
      if (res.harvestedGp > 0) {
        showToast(`GP ${res.harvestedGp.toLocaleString()} 수확 완료`, false);
      } else if (pendingGp > 0) {
        showToast('저장소가 가득 차 수확할 수 없습니다', false);
      } else {
        showToast('수확할 GP가 없습니다', false);
      }
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : 'GP 수확에 실패했습니다');
      console.warn('[PersonalIslandPage] harvest failed', err);
    } finally {
      setIsHarvesting(false);
    }
  };

  const handleBuild = async () => {
    if (isBuildingRef.current) return;
    setBuildError('');
    if (!selectedBuilding) { setBuildError('건물을 선택해주세요.'); return; }
    if (!selectedCell) { setBuildError('그리드에서 빈 셀을 선택해주세요.'); return; }
    const typeId = catalog.find(c => c.name.toLowerCase() === selectedBuilding)?.buildingTypeId;
    if (!typeId) { setBuildError('아직 건설할 수 없는 건물입니다.'); return; }
    isBuildingRef.current = true;
    setIsBuilding(true);
    try {
      await placeIslandBuilding(typeId, selectedCell.x, selectedCell.y);
      // 건설은 섬 저장소 GP에서 차감 — 금고(vault)와 무관. 섬만 다시 불러온다.
      setSelectedBuilding(null);
      setShowBuild(false);
      void reloadIsland();
    } catch (err) {
      setBuildError(err instanceof ApiError ? err.message : '건설에 실패했습니다. 다시 시도해주세요.');
    } finally {
      isBuildingRef.current = false;
      setIsBuilding(false);
    }
  };

  const containerRef = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ mx: 0, my: 0, px: 0, py: 0 });

  const ISLAND_W = gridSize * 38; // CELL_SIZE(36) + gap(2)
  const ISLAND_H = gridSize * 38;

  const getFitView = useCallback(() => {
    const el = containerRef.current;
    if (!el) return { z: 1, x: 0, y: 0 };
    const { width, height } = el.getBoundingClientRect();
    const z = Math.min(width / ISLAND_W, height / ISLAND_H) * 0.95;
    return { z, x: (width - ISLAND_W * z) / 2, y: (height - ISLAND_H * z) / 2 };
  }, [ISLAND_W, ISLAND_H]);

  useEffect(() => {
    const raf = requestAnimationFrame(() => {
      const { z, x, y } = getFitView();
      setZoom(z);
      setPan({ x, y });
    });
    return () => cancelAnimationFrame(raf);
  }, [getFitView]);

  const handleWheel = useCallback((e: WheelEvent) => {
    e.preventDefault();
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const mx = e.clientX - rect.left;
    const my = e.clientY - rect.top;
    const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15;
    setZoom(z => {
      const next = Math.max(0.3, Math.min(5, z * factor));
      const scale = next / z;
      setPan(p => ({ x: mx - (mx - p.x) * scale, y: my - (my - p.y) * scale }));
      return next;
    });
  }, []);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    el.addEventListener('wheel', handleWheel, { passive: false });
    return () => el.removeEventListener('wheel', handleWheel);
  }, [handleWheel]);

  const handleMouseDown = (e: React.MouseEvent) => {
    if ((e.target as Element).closest('[data-cell]')) return;
    setIsDragging(true);
    setDragStart({ mx: e.clientX, my: e.clientY, px: pan.x, py: pan.y });
  };
  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    setPan({ x: dragStart.px + (e.clientX - dragStart.mx), y: dragStart.py + (e.clientY - dragStart.my) });
  };
  const handleMouseUp = () => setIsDragging(false);

  const zoneOverlay: Record<number, string> = {
    1: 'rgba(255, 215, 0, 0.08)', 2: 'rgba(255, 51, 51, 0.06)', 3: 'rgba(0, 245, 255, 0.04)',
  };
  const zoneBorder: Record<number, string> = {
    1: '#ffd700', 2: '#ff3333', 3: '#00f5ff',
  };

  // 실제 건물 개수 = 앵커 칸(!isBody)만 센다. 2x2 건물이 칸 수(4)로 부풀지 않도록.
  const countBuildings = (type: BuildingType) => grid.flat().filter(c => c.type === type && !c.isBody).length;

  // 건물/장식 탭이 공유하는 목록 카드.
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

  // 총 방어력 = 파괴되지 않은 배치 건물들의 방어력 합(카탈로그 기준 실데이터).
  const totalDefense = (island?.buildings ?? [])
    .filter(b => !b.isDestroyed)
    .reduce((sum, b) => sum + (catalogByType.get(b.type.toLowerCase())?.defensePower ?? 0), 0);

  const CELL_SIZE = 36;

  return (
    <div className="page-root">
      <GNB />

      {toast && (
        <IslandToast
          message={toast.message}
          isError={toast.isError}
          onClose={() => setToast(null)}
        />
      )}

      <div className="bg-surface border-b border-[#00ff8840] px-5 py-3 flex items-center gap-4 flex-shrink-0">
        <button onClick={() => navigate('/app/map')} className="text-muted hover:text-foreground mr-1">←</button>
        <div className="w-10 h-10 bg-[#00ff8830] rounded-xl border border-gp flex items-center justify-center">
          <span className="text-xl">🏝</span>
        </div>
        <div>
          <h1 className="text-gp font-bold text-xl">나의 섬 · {username || '—'}</h1>
          <p className="text-muted text-xs">중앙 대륙 · {island?.grade ?? 'D'}급 개인 영토 · {gridSize}×{gridSize} 그리드</p>
        </div>
        <div className="flex items-center gap-2 ml-4">
          <div className="h-7 px-3 rounded-lg bg-[#ffd70020] border border-gold flex items-center">
            <span className="text-gold font-bold text-[11px]">{island?.grade ?? 'D'}급</span>
          </div>
          <div className="flex items-center gap-1 bg-elevated border border-gp rounded-lg px-2 py-1">
            <div className="w-2 h-2 bg-gp rounded-full animate-pulse" />
            <span className="text-gp text-[11px]">안전 보호 중</span>
          </div>
          <div
            className={`flex items-center gap-1 bg-elevated border rounded-lg px-2 py-1 ${isBuilderFull ? 'border-gold' : 'border-outline'}`}
            title="건축 장인 — 지금 바로 건설에 투입 가능한 장인 수 / 전체"
          >
            <span className="text-[11px]">🔨</span>
            <span className={`text-[11px] font-bold ${isBuilderFull ? 'text-gold' : 'text-muted'}`}>
              장인 {builderCount - buildersInUse}/{builderCount}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-4 ml-auto">
          <div className="text-right" title="계정 금고(vault)의 GP. 건물 건설·생산에는 이 섬 저장소의 GP가 쓰인다(금고와 별개).">
            <p className="text-muted text-[10px]">금고 GP</p>
            <p className="text-gp font-bold text-base">💎 {gp.toLocaleString()}</p>
          </div>
          {island && (
            <div
              className="text-right"
              title={`이 섬 저장소(성·저장소)의 GP. 건물 건설·유닛 생산에 차감되는 값.\n최대치는 저장소가 관리 — 성·저장소 레벨당 +${STORAGE_CAP_PER_LEVEL.toLocaleString()}.`}
            >
              <p className="text-muted text-[10px]">섬 저장 GP <span className="text-[9px]">(📦 저장소 관리)</span></p>
              <p className="font-bold text-base">
                <span className="text-gold">🏝 {(island.storedGp ?? 0).toLocaleString()}</span>
                <span className="text-muted text-[11px]"> / {(island.storageCapacity ?? 0).toLocaleString()}</span>
              </p>
            </div>
          )}
          <div className="text-right">
            <p className="text-muted text-[10px]">생산 속도</p>
            <p className="font-bold text-base">
              <span className="text-gold">+{(island?.productionRatePerHour ?? 0) * (isBoostActive ? 2 : 1)} GP/시간</span>
              {isBoostActive && <span className="text-[#00f5ff] text-[11px] ml-1">⚡×2 {boostRemainingLabel}</span>}
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
        </div>
      </div>

      {/* Mode indicator banners */}
      {moveMode && (
        <div className="flex items-center justify-between px-5 py-2 flex-shrink-0 border-b border-[#ffd70060]" style={{ background: '#1a1200' }}>
          <div className="flex items-center gap-2">
            <span className="text-sm">🔄</span>
            <span className="text-gold font-semibold text-[13px]">
              이동 모드 — 이동할 빈 셀을 클릭하세요
              {moveSourceCell && <span className="text-muted ml-2 text-[11px]">출발: ({moveSourceCell.x}, {moveSourceCell.y})</span>}
            </span>
          </div>
          <button onClick={cancelModes} className="h-7 px-3 rounded-lg border border-[#ffd70060] text-gold text-xs transition-colors">취소</button>
        </div>
      )}
      {buildPending && (
        <div className="flex items-center justify-between px-5 py-2 flex-shrink-0 border-b border-[#00f5ff60]" style={{ background: '#001020' }}>
          <div className="flex items-center gap-2">
            <span className="text-sm">🏗</span>
            <span className="font-semibold text-[13px]" style={{ color: '#00f5ff' }}>건설 위치 선택 — 빈 셀을 클릭하세요</span>
          </div>
          <button onClick={cancelModes} className="h-7 px-3 rounded-lg border text-xs transition-colors" style={{ borderColor: '#00f5ff60', color: '#00f5ff' }}>취소</button>
        </div>
      )}
      {deployFromInventoryIdx !== null && inventory[deployFromInventoryIdx] && (
        <div className="flex items-center justify-between px-5 py-2 flex-shrink-0 border-b border-[#00ff8860]" style={{ background: '#001a10' }}>
          <div className="flex items-center gap-2">
            <span className="text-sm">📦</span>
            <span className="text-gp font-semibold text-[13px]">
              배치 모드 — 배치할 빈 셀을 클릭하세요
              <span className="text-muted ml-2 text-[11px]">({inventory[deployFromInventoryIdx].buildingTypeName})</span>
            </span>
          </div>
          <button onClick={cancelModes} className="h-7 px-3 rounded-lg border border-[#00ff8860] text-gp text-xs transition-colors">취소</button>
        </div>
      )}

      <div className="flex flex-1 overflow-hidden">
        <div className="flex-1 flex flex-col overflow-hidden bg-[#070c18]">
          {/* Legend bar */}
          <div className="px-4 pt-3 pb-2 flex justify-between items-center flex-shrink-0">
            <div className="flex gap-3">
              {[3, 2, 1].map(z => (
                <div key={z} className="flex items-center gap-1">
                  <div className="w-3 h-3 rounded-sm border" style={{ background: zoneOverlay[z], borderColor: zoneBorder[z] }} />
                  <span className="text-muted text-[10px]">Zone {z}</span>
                </div>
              ))}
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted text-[10px]">
                {selectedCell ? `선택: (${selectedCell.x}, ${selectedCell.y}) - ${nameFor(selectedCellData?.type || 'empty')}` : '셀을 클릭하여 선택'}
              </span>
              <button onClick={() => setZoom(z => Math.min(5, z * 1.2))} className="w-6 h-6 bg-outline-soft border border-outline rounded text-muted hover:text-white text-xs flex items-center justify-center">+</button>
              <button onClick={() => setZoom(z => Math.max(0.3, z / 1.2))} className="w-6 h-6 bg-outline-soft border border-outline rounded text-muted hover:text-white text-xs flex items-center justify-center">−</button>
              <button onClick={() => { const { z, x, y } = getFitView(); setZoom(z); setPan({ x, y }); }} className="w-6 h-6 bg-outline-soft border border-outline rounded text-muted hover:text-white text-xs flex items-center justify-center">⊡</button>
            </div>
          </div>

          {/* Pan/zoom viewport */}
          <div
            ref={containerRef}
            className="flex-1 relative overflow-hidden"
            style={{ cursor: isDragging ? 'grabbing' : 'grab', userSelect: 'none' }}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
          >
            <div style={{ position: 'absolute', top: 0, left: 0, transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`, transformOrigin: '0 0', willChange: 'transform' }}>
          <div style={{ display: 'grid', gridTemplateColumns: `repeat(${gridSize}, ${CELL_SIZE}px)`, gridTemplateRows: `repeat(${gridSize}, ${CELL_SIZE}px)`, gap: 2, width: gridSize * (CELL_SIZE + 2) }}>
            {grid.map((row, y) =>
              row.map((cell, x) => {
                const isSelected = selectedCell?.x === x && selectedCell?.y === y;
                const isMoveSource = moveSourceCell?.x === x && moveSourceCell?.y === y;
                const isActionTarget = (moveMode || deployFromInventoryIdx !== null || buildPending) && cell.type === 'empty';
                const zone = cell.zone || 4;
                const bg = isMoveSource ? colorFor(cell.type) + '80' : cell.type !== 'empty' ? colorFor(cell.type) + '50' : showZones ? zoneOverlay[zone] : 'var(--color-surface)';
                const hpPct = cell.hp && cell.maxHp ? cell.hp / cell.maxHp : 0;
                const hpColor = hpPct > 0.7 ? '#00ff88' : hpPct > 0.4 ? '#ffd700' : '#ff3333';
                const building = isUnderConstruction(cell.buildCompleteAt, now);
                return (
                  <div
                    key={`${x}-${y}`}
                    data-cell="true"
                    onClick={() => handleCellClick(x, y, cell)}
                    className="relative cursor-pointer flex flex-col items-center justify-center transition-all hover:brightness-125"
                    style={{
                      width: CELL_SIZE, height: CELL_SIZE, background: bg, borderRadius: 4,
                      border: isMoveSource
                        ? '2px solid #ffd700'
                        : isSelected
                          ? '2px solid #00f5ff'
                          : isActionTarget
                            ? '1px dashed #00ff8880'
                            : showZones ? `1px solid ${zoneBorder[zone]}30` : `1px solid ${cell.type !== 'empty' ? colorFor(cell.type) + '60' : 'var(--color-outline-soft)'}`,
                      boxShadow: isMoveSource ? '0 0 6px #ffd700' : isSelected ? '0 0 8px #00f5ff80' : cell.type === 'castle' ? '0 0 6px #ffd70040' : undefined,
                    }}
                  >
                    {cell.type !== 'empty' ? (
                      <>
                        {!cell.isBody && (
                          <span className="text-sm leading-none">{building ? '🔨' : iconFor(cell.type)}</span>
                        )}
                        {!cell.isBody && building && (
                          <span className="absolute bottom-0 left-0 right-0 text-[7px] text-center text-gold font-bold leading-tight">
                            {remainingLabel(cell.buildCompleteAt, now)}
                          </span>
                        )}
                        {!cell.isBody && cell.level && !building && (
                          <div className="absolute bottom-0.5 left-0.5 right-0.5 h-1 rounded-full overflow-hidden" style={{ background: '#0a0e1a' }}>
                            <div className="h-full rounded-full" style={{ width: `${hpPct * 100}%`, background: hpColor }} />
                          </div>
                        )}
                        {!cell.isBody && cell.level && !building && (
                          <div className="absolute top-0 right-0 w-3 h-3 rounded-full flex items-center justify-center text-[6px]" style={{ background: colorFor(cell.type) }}>
                            {cell.level}
                          </div>
                        )}
                      </>
                    ) : (
                      <span className={`text-[10px] ${isActionTarget ? 'text-[#00ff8870]' : 'text-outline opacity-40'}`}>
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
        </div>

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
                {functionalCatalog.map(buildingCard)}
              </div>
            )}
            {activeTab === 'decoration' && (
              <div className="p-3 space-y-2">
                {decorativeCatalog.length > 0
                  ? decorativeCatalog.map(buildingCard)
                  : <p className="text-muted text-[11px] text-center py-6">장식 건물이 없습니다</p>}
                <button
                  onClick={() => setShowShop(true)}
                  className="w-full h-9 mt-1 border border-gold text-gold rounded-xl text-xs transition-colors hover:bg-gold/10"
                >🛒 장식 상점에서 구매</button>
              </div>
            )}
            {activeTab === 'resources' && (
              <div className="p-3 space-y-3">
                <div className="bg-panel-deep rounded-xl p-3">
                  <div className="flex justify-between"><span className="text-gold font-semibold text-xs">⚡ AP</span><span className="text-gold font-bold text-sm">{ap.toLocaleString()}</span></div>
                </div>
                <div className="bg-panel-deep rounded-xl p-3">
                  <div className="flex justify-between mb-1"><span className="text-gp font-semibold text-xs">💎 GP 생산</span><span className="text-gp font-bold text-sm">+{island?.productionRatePerHour ?? 0}/시간</span></div>
                  <div className="space-y-1 mt-2">
                    {countBuildings('workshop') > 0
                      ? <div className="flex justify-between"><span className="text-muted text-[10px]">생산소 ×{countBuildings('workshop')}개</span><span className="text-[10px]" style={{ color: '#00ff88' }}>+{island?.productionRatePerHour ?? 0}/시간</span></div>
                      : <p className="text-muted text-[10px]">생산 건물 없음</p>
                    }
                  </div>
                </div>
                <IslandResearchPanel
                  research={research}
                  isBusy={isResearching}
                  error={researchError}
                  onResearch={handleResearch}
                />
              </div>
            )}
            {activeTab === 'units' && (
              <div className="p-3 space-y-2">
                <div className="flex items-center justify-between mb-1">
                  <p className="text-muted font-semibold text-xs">주둔 유닛</p>
                  {islandMilitary && (
                    <span className="text-[10px] text-gold">🌾 식량 {islandFood.toLocaleString()}</span>
                  )}
                </div>
                {isMilitaryLoading && <LoadingState className="py-4" />}
                {!isMilitaryLoading && islandUnits.map(u => {
                  // 관리자 지정 값 우선, 없으면 기본 매핑
                  const fallback = UNIT_LABELS[u.name] ?? { label: u.name, icon: '⚔', color: '#e0e8ff' };
                  const meta = {
                    label: u.displayName ?? fallback.label,
                    icon: u.icon ?? fallback.icon,
                    color: u.colorHex ?? fallback.color,
                  };
                  return (
                    <div key={u.unitTypeId} className="bg-panel-deep rounded-xl p-3">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-base">{meta.icon}</span>
                        <div className="flex-1">
                          <div className="flex justify-between">
                            <span className="text-xs" style={{ color: meta.color }}>{meta.label}</span>
                            <span className="text-muted text-[11px]">{u.quantity}개</span>
                          </div>
                          <p className="text-muted text-[9px]">대기 {u.idleCount} · 배치 {u.deployedCount} · 공격력 {u.attackPower}</p>
                        </div>
                      </div>
                      {u.quantity > 0 && (
                        <div className="bg-panel h-1.5 rounded-full overflow-hidden">
                          <div className="h-full rounded-full" style={{ width: `${Math.round((u.deployedCount / u.quantity) * 100)}%`, background: meta.color }} />
                        </div>
                      )}
                    </div>
                  );
                })}
                {!isMilitaryLoading && islandUnits.length === 0 && (
                  <p className="text-muted text-xs text-center py-4">보유한 유닛이 없습니다</p>
                )}
                <button
                  onClick={() => {
                    setTrainUnitTypeId((unitCatalog.find(u => u.requiredBarracksLevel <= islandBarracksLevel) ?? unitCatalog[0])?.unitTypeId ?? null);
                    setTrainLevel(1);
                    setTrainQuantity(1);
                    setShowTrainModal(true);
                  }}
                  className="w-full h-9 border border-secondary rounded-xl text-secondary text-xs hover:bg-[#8b50ff20] transition-colors"
                >유닛 훈련하기</button>
              </div>
            )}
            {activeTab === 'expand' && (
              <div className="p-3 space-y-3">
                <div className="bg-panel-deep rounded-xl p-3">
                  <div className="flex justify-between items-center mb-2">
                    <span className="text-primary font-semibold text-xs">🏝 현재 섬</span>
                    <span className="text-foreground font-bold text-sm">{island?.grade}급 · {gridSize}×{gridSize}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-y-1 text-[10px]">
                    <span className="text-muted">성 레벨</span><span className="text-right text-foreground">Lv.{castleLevel} / {CASTLE_MAX_LEVEL}</span>
                    <span className="text-muted">Zone 반경</span><span className="text-right text-foreground">{island?.zone1Radius ?? '-'} / {island?.zone2Radius ?? '-'}</span>
                    <span className="text-muted">건축 장인</span><span className="text-right text-foreground">{builderCount}명</span>
                  </div>
                </div>
                <p className="text-muted text-[11px] leading-relaxed">
                  <span className="text-gold">성(🏰)</span>을 레벨업하면 섬 등급이 <span className="text-foreground font-semibold">자동 승격</span>되고 그리드가 확장·재배치됩니다. 별도 확장 버튼은 없습니다.
                </p>
                <div className="bg-panel-deep rounded-xl overflow-hidden">
                  {ISLAND_GRADES.map(g => {
                    const isCurrent = g.name === island?.grade;
                    const locked = g.castleLevelRequired > CASTLE_MAX_LEVEL;
                    return (
                      <div key={g.name} className={`flex items-center justify-between px-3 py-2 text-[11px] border-b border-outline last:border-b-0 ${isCurrent ? 'bg-[#00f5ff10]' : ''}`}>
                        <span className={isCurrent ? 'text-primary font-bold' : 'text-foreground'}>{g.name}급{isCurrent ? ' (현재)' : ''}</span>
                        <span className="text-muted">{g.gridSize}×{g.gridSize} · 성 Lv.{g.castleLevelRequired} 필요{locked ? ' · 미출시' : ''}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          <div className="p-3 border-t border-outline space-y-2">
            <button
              onClick={() => {
                const cell = selectedCell ? grid[selectedCell.y]?.[selectedCell.x] : null;
                if (cell?.type === 'empty') {
                  setShowBuild(true);
                } else {
                  cancelModes();
                  setBuildPending(true);
                }
              }}
              className="w-full h-9 border border-primary rounded-xl text-primary text-xs hover:bg-primary/10 transition-colors"
            >🏗 건물 건설</button>
            <button
              onClick={() => setShowInventory(true)}
              className="relative w-full h-9 border border-secondary text-secondary rounded-xl text-xs transition-colors hover:bg-[#8b50ff20]"
            >
              📦 보관함
              {inventory.length > 0 && (
                <span className="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-secondary text-white text-[10px] flex items-center justify-center">
                  {inventory.length}
                </span>
              )}
            </button>
            <button
              onClick={() => setShowShop(true)}
              className="w-full h-9 border border-gold text-gold rounded-xl text-xs transition-colors hover:bg-gold/10"
            >🛒 장식 상점</button>
            <button
              onClick={() => void handleHarvest()}
              disabled={isHarvesting}
              className="w-full h-9 rounded-xl font-bold text-xs transition-all"
              style={{
                background: isHarvesting ? '#2a3050' : '#00ff88',
                color: isHarvesting ? '#7788a5' : '#0a0e1a',
                border: isHarvesting ? '1px solid #354064' : 'none',
              }}
            >
              {isHarvesting
                ? '수확 중...'
                : `🌾 GP 수확하기${island && island.accumulatedGp > 0 ? ` (+${island.accumulatedGp.toLocaleString()})` : ''}`}
            </button>
            <button
              onClick={() => void handleBoost()}
              disabled={isBoosting || isBoostActive}
              title={isBoostActive ? '이미 부스터가 적용 중입니다' : 'AP 500 소모 · GP·식량 생산 6시간 ×2'}
              className="w-full h-9 rounded-xl font-bold text-xs transition-all border disabled:cursor-not-allowed"
              style={{
                borderColor: '#00f5ff',
                color: isBoostActive ? '#7788a5' : '#00f5ff',
                background: isBoostActive ? '#00f5ff10' : 'transparent',
              }}
            >
              {isBoosting
                ? '발동 중...'
                : isBoostActive
                  ? `⚡ 부스터 ×2 활성 (${boostRemainingLabel} 남음)`
                  : '⚡ 생산 부스터 (500 AP · 6시간 ×2)'}
            </button>
            <button onClick={() => navigate('/app/map')} className="w-full h-9 bg-elevated border border-outline rounded-xl text-muted text-xs">🗺 월드맵으로</button>
          </div>
        </div>
      </div>

      {showBuild && (
        <IslandBuildModal
          selectedCell={selectedCell}
          selectedZone={selectedCellData?.zone}
          gp={gp}
          catalog={catalog}
          selectedBuilding={selectedBuilding}
          buildError={buildError}
          isBuilding={isBuilding}
          onSelectBuilding={setSelectedBuilding}
          onClose={() => { setShowBuild(false); setSelectedBuilding(null); setBuildError(''); }}
          onBuild={() => void handleBuild()}
        />
      )}

      {showBuildingAction && selectedCell && selectedCellData && selectedCellData.type !== 'empty' && (
        <IslandBuildingActionPanel
          selectedCell={selectedCell}
          cellData={selectedCellData}
          info={catalog.find(c => c.name.toLowerCase() === selectedCellData.type)}
          onStartMove={handleStartMove}
          onStoreBuilding={handleStoreBuilding}
          onUpgrade={handleUpgradeBuilding}
          onTrain={() => {
            setTrainUnitTypeId(unitCatalog[0]?.unitTypeId ?? null);
            setTrainLevel(1);
            setTrainQuantity(1);
            setShowBuildingAction(false);
            setShowTrainModal(true);
          }}
          onHarvest={() => {
            setShowBuildingAction(false);
            void handleHarvest();
          }}
          onRush={() => void handleRush()}
          onClose={() => setShowBuildingAction(false)}
        />
      )}

      {showTrainModal && (
        <IslandTrainUnitModal
          units={unitCatalog}
          islandGp={island?.storedGp ?? 0}
          storedFood={islandFood}
          maxBarracksLevel={islandBarracksLevel}
          trainUnitTypeId={trainUnitTypeId}
          trainQuantity={trainQuantity}
          trainLevel={trainLevel}
          researchedLevels={researchedLevels}
          isTraining={isTraining}
          onSelectUnit={id => { setTrainUnitTypeId(id); setTrainLevel(1); }}
          onChangeQuantity={setTrainQuantity}
          onChangeLevel={setTrainLevel}
          onTrain={handleProduceUnit}
          onClose={() => setShowTrainModal(false)}
        />
      )}

      {showInventory && (
        <IslandInventoryModal
          inventory={inventory}
          onDeploy={(idx) => { setDeployFromInventoryIdx(idx); setShowInventory(false); }}
          onClose={() => setShowInventory(false)}
        />
      )}

      {showShop && (
        <IslandDecorationShopModal
          ap={ap}
          onPurchased={(apRemaining) => { syncAP(apRemaining); reloadInventory(); }}
          onClose={() => setShowShop(false)}
        />
      )}
    </div>
  );
}
