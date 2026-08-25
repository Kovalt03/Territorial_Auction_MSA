import { Fragment, useEffect, useState } from 'react';

import {
  fetchAdminBuildingTypes, createBuildingType, updateBuildingType, deleteBuildingType,
  fetchLevelSpecs, updateLevelSpecs, fetchCastleLimits, updateCastleLimits,
  type BuildingTypeForm, type LevelSpecValues,
} from '../api/admin';
import { ApiError } from '../api/client';

import type { BuildingTypeInfo } from '../types/island';

const input = 'w-full bg-elevated border border-outline rounded px-1.5 h-7 text-foreground text-[11px] outline-none focus:border-primary';

// 최대 레벨 3 → 상세 그리드는 Lv1(기본)·Lv2·Lv3
const UPGRADE_LEVELS = [2, 3];
// 성 레벨별 건물 개수 상한 — 성은 하나뿐이라 대상에서 제외
const CASTLE_LEVELS = [1, 2, 3];

// 정적 속성(레벨과 무관, 메인 행에서 편집)
const ATTR_FIELDS: { key: keyof BuildingTypeForm; label: string; w: string; nullable?: boolean; text?: boolean; decorativeOnly?: boolean }[] = [
  { key: 'icon', label: '아이콘', w: 'w-12', nullable: true, text: true },
  { key: 'colorHex', label: '색', w: 'w-20', nullable: true, text: true },
  { key: 'width', label: '너비', w: 'w-12' },
  { key: 'height', label: '높이', w: 'w-12' },
  { key: 'zoneRestriction', label: 'Zone', w: 'w-14', nullable: true },
  { key: 'apCost', label: 'AP상점가', w: 'w-16', nullable: true, decorativeOnly: true },
];

// 레벨별 값. baseKey=Lv1(건물 기본값), specKey=Lv2·Lv3(레벨 지정값)
type StatRow = { label: string; baseKey: keyof BuildingTypeForm; specKey: keyof LevelSpecValues; production?: boolean; nullable?: boolean };
const STAT_ROWS: StatRow[] = [
  { label: '비용', baseKey: 'baseCostGp', specKey: 'upgradeCostGp' },
  { label: 'HP', baseKey: 'maxHp', specKey: 'maxHp' },
  { label: '시간(초)', baseKey: 'buildTimeSeconds', specKey: 'upgradeTimeSeconds', nullable: true },
  { label: '방어력', baseKey: 'defensePower', specKey: 'defensePower', nullable: true },
  { label: '식량/시간', baseKey: 'foodProductionRate', specKey: 'foodProductionRate', production: true, nullable: true },
  { label: '유닛/레벨', baseKey: 'unitCapacityPerLevel', specKey: 'unitCapacityPerLevel', production: true, nullable: true },
  { label: 'GP/시간', baseKey: 'gpProductionRate', specKey: 'gpProductionRate', production: true, nullable: true },
];

function toForm(b: BuildingTypeInfo): BuildingTypeForm {
  return {
    displayName: b.displayName,
    width: b.width, height: b.height, maxHp: b.maxHp, baseCostGp: b.baseCostGp,
    upgradeCostGp: b.upgradeCostGp, apCost: b.apCost, zoneRestriction: b.zoneRestriction, defensePower: b.defensePower,
    foodProductionRate: b.foodProductionRate, unitCapacityPerLevel: b.unitCapacityPerLevel,
    gpProductionRate: b.gpProductionRate,
    buildTimeSeconds: b.buildTimeSeconds, upgradeTimeSeconds: b.upgradeTimeSeconds,
    icon: b.icon, colorHex: b.colorHex,
  };
}

export function AdminBuildingPage() {
  const [items, setItems] = useState<BuildingTypeInfo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchAdminBuildingTypes()
      .then(r => { setItems(r); setError(null); })
      .catch(e => { setError(e instanceof ApiError ? e.message : '건물을 불러올 수 없습니다.'); console.warn('[AdminBuilding] fetch', e); });
  };
  useEffect(load, []);

  const onDone = (msg: string) => { setMessage(msg); load(); };

  return (
    <div className="h-full overflow-auto p-6">
      <h2 className="font-bold text-base mb-1">건물 관리</h2>
      <p className="text-muted text-[11px] mb-4">
        메인 행은 정적 속성(아이콘·색·크기·Zone·표시명), <b>상세</b>에서 레벨별 값(비용·HP·방어력·생산)을 편집합니다.
        <span className="text-primary font-semibold"> 기능</span> 건물은 생산 스탯도, <span className="text-gp font-semibold">장식</span> 건물은 HP·방어력만.
      </p>

      {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}
      {message && <p className="text-gp text-xs mb-3">✓ {message}</p>}

      <table className="w-full text-xs mb-6">
        <thead className="text-dim text-[11px] border-b border-outline">
          <tr>
            <th className="text-left font-medium py-2 px-2">코드 / 분류</th>
            <th className="text-left font-medium py-2 px-1 w-[110px]">표시명</th>
            {ATTR_FIELDS.map(f => <th key={f.key} className="text-left font-medium py-2 px-1">{f.label}</th>)}
            <th className="text-right font-medium py-2 px-2 w-44"></th>
          </tr>
        </thead>
        <tbody>
          {(['FUNCTIONAL', 'DECORATIVE'] as const).map(cat => {
            const group = items.filter(b => (cat === 'DECORATIVE' ? b.category === 'DECORATIVE' : b.category !== 'DECORATIVE'));
            if (group.length === 0) return null;
            return (
              <Fragment key={cat}>
                <tr className="bg-panel-deep">
                  <td colSpan={ATTR_FIELDS.length + 3} className={`py-1.5 px-2 text-[11px] font-bold ${cat === 'DECORATIVE' ? 'text-gp' : 'text-primary'}`}>
                    {cat === 'DECORATIVE' ? '🎨 장식 건물' : '⚙ 기능 건물'} <span className="text-dim font-normal">({group.length})</span>
                  </td>
                </tr>
                {group.map(b => <Row key={b.buildingTypeId} item={b} onDone={onDone} onError={setError} />)}
              </Fragment>
            );
          })}
          {items.length === 0 && <tr><td colSpan={ATTR_FIELDS.length + 3} className="py-8 text-center text-muted">건물이 없습니다.</td></tr>}
        </tbody>
      </table>

      <section className="bg-panel border border-outline rounded-xl p-4 max-w-3xl">
        <h3 className="font-bold text-sm mb-3">새 건물 추가</h3>
        <CreateForm onDone={onDone} onError={setError} />
      </section>
    </div>
  );
}

function Row({ item, onDone, onError }: { item: BuildingTypeInfo; onDone: (m: string) => void; onError: (m: string) => void }) {
  const [form, setForm] = useState<BuildingTypeForm>(toForm(item));
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);
  const isDecorative = item.category === 'DECORATIVE';
  const isCastle = item.name === 'CASTLE';
  const statRows = STAT_ROWS.filter(s => !s.production || !isDecorative);

  // 성 레벨별 최대 개수 (상세 토글 시 로드). castleLimits[성레벨] = 문자열 입력값
  const [castleLimits, setCastleLimits] = useState<Record<number, string>>({});
  useEffect(() => {
    if (!open || isCastle) return;
    fetchCastleLimits(item.buildingTypeId)
      .then(r => {
        const next: Record<number, string> = {};
        CASTLE_LEVELS.forEach(lv => { next[lv] = r[String(lv)] != null ? String(r[String(lv)]) : ''; });
        setCastleLimits(next);
      })
      .catch(e => { onError(e instanceof ApiError ? e.message : '개수 제한을 불러올 수 없습니다.'); console.warn('[AdminBuilding] castleLimits', e); });
  }, [open, isCastle, item.buildingTypeId, onError]);

  // Lv2·Lv3 지정값 (상세 토글 시 로드). levelSpecs[level][specKey] = 문자열 입력값
  const [levelSpecs, setLevelSpecs] = useState<Record<number, Partial<Record<keyof LevelSpecValues, string>>>>({});
  const [levelLoaded, setLevelLoaded] = useState(false);
  useEffect(() => {
    if (!open || levelLoaded) return;
    fetchLevelSpecs(item.buildingTypeId)
      .then(r => {
        const next: Record<number, Partial<Record<keyof LevelSpecValues, string>>> = {};
        UPGRADE_LEVELS.forEach(lv => {
          const v = r[String(lv)];
          const row: Partial<Record<keyof LevelSpecValues, string>> = {};
          statRows.forEach(s => { row[s.specKey] = v && v[s.specKey] != null ? String(v[s.specKey]) : ''; });
          next[lv] = row;
        });
        setLevelSpecs(next); setLevelLoaded(true);
      })
      .catch(e => { onError(e instanceof ApiError ? e.message : '레벨 설정을 불러올 수 없습니다.'); console.warn('[AdminBuilding] levelSpecs', e); });
  }, [open, levelLoaded, item.buildingTypeId, onError, statRows]);

  const setAttr = (k: keyof BuildingTypeForm, v: string, text?: boolean, nullable?: boolean) =>
    setForm(f => ({ ...f, [k]: text ? (v || null) : v.trim() === '' ? (nullable ? null : 0) : Number(v) }));
  const setSpec = (lv: number, key: keyof LevelSpecValues, val: string) =>
    setLevelSpecs(s => ({ ...s, [lv]: { ...s[lv], [key]: val } }));

  const save = async () => {
    if (busy) return;
    setBusy(true);
    try {
      await updateBuildingType(item.buildingTypeId, form);
      if (levelLoaded) {
        const payload: Record<number, LevelSpecValues> = {};
        UPGRADE_LEVELS.forEach(lv => {
          const row = levelSpecs[lv] ?? {};
          const num = (k: keyof LevelSpecValues) => (row[k]?.trim() ? Number(row[k]) : null);
          payload[lv] = {
            upgradeCostGp: num('upgradeCostGp'), maxHp: num('maxHp'), defensePower: num('defensePower'),
            foodProductionRate: num('foodProductionRate'), unitCapacityPerLevel: num('unitCapacityPerLevel'),
            gpProductionRate: num('gpProductionRate'),
            upgradeTimeSeconds: num('upgradeTimeSeconds'),
          };
        });
        await updateLevelSpecs(item.buildingTypeId, payload);
      }
      if (!isCastle && Object.keys(castleLimits).length > 0) {
        const limits: Record<number, number | null> = {};
        CASTLE_LEVELS.forEach(lv => { limits[lv] = castleLimits[lv]?.trim() ? Number(castleLimits[lv]) : null; });
        await updateCastleLimits(item.buildingTypeId, limits);
      }
      onDone(`${item.name} 저장됨`);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : '저장 실패');
    } finally { setBusy(false); }
  };
  const remove = async () => {
    if (busy) return;
    if (!window.confirm(`${item.name} 건물을 삭제할까요?`)) return;
    setBusy(true);
    try { await deleteBuildingType(item.buildingTypeId); onDone(`${item.name} 삭제됨`); }
    catch (e) { onError(e instanceof ApiError ? e.message : '삭제 실패'); }
    finally { setBusy(false); }
  };

  return (
    <>
      <tr className="border-b border-outline-soft">
        <td className="py-1.5 px-2 font-semibold whitespace-nowrap">
          <span className="mr-1">{form.icon || '🏗'}</span>{item.name}
          <span className={`ml-1.5 text-[9px] font-bold ${isDecorative ? 'text-gp' : 'text-primary'}`}>{isDecorative ? '장식' : '기능'}</span>
        </td>
        <td className="py-1.5 px-1">
          <input value={form.displayName ?? ''} placeholder="한글명" onChange={e => setForm(f => ({ ...f, displayName: e.target.value || null }))} className={input} />
        </td>
        {ATTR_FIELDS.map(f => (
          <td key={f.key} className="py-1.5 px-1">
            {f.decorativeOnly && !isDecorative
              ? <span className="text-dim text-[11px] pl-1">—</span>
              : <input type={f.text ? 'text' : 'number'} value={form[f.key] ?? ''} placeholder={f.text ? (f.key === 'colorHex' ? '#rgb' : '🏗') : f.nullable ? '-' : '0'}
                  onChange={e => setAttr(f.key, e.target.value, f.text, f.nullable)} className={`${input} ${f.w}`} />}
          </td>
        ))}
        <td className="py-1.5 px-2 text-right whitespace-nowrap">
          <button onClick={() => setOpen(o => !o)} className="text-dim hover:text-foreground-soft mr-2">{open ? '상세 ▾' : '상세 ▸'}</button>
          <button onClick={() => void save()} disabled={busy} className="text-primary font-bold hover:brightness-125 disabled:opacity-30 mr-2">저장</button>
          {isDecorative
            ? <button onClick={() => void remove()} disabled={busy} className="text-danger font-bold hover:brightness-125 disabled:opacity-40">삭제</button>
            : <span className="text-dim text-[10px]">기능</span>}
        </td>
      </tr>
      {open && (
        <tr className="bg-surface border-b border-outline-soft">
          <td colSpan={ATTR_FIELDS.length + 3} className="py-2 px-3">
            <p className="text-[11px] text-dim mb-2">
              레벨별 값 <span className="text-muted">— Lv1은 기본값(시간은 건설 시간), Lv2·Lv3은 비우면 공식 자동</span>
            </p>
            <table className="text-[11px]">
              <thead className="text-dim">
                <tr>
                  <th className="text-left font-medium pr-3 pb-1">항목</th>
                  <th className="text-left font-medium px-1 pb-1">Lv1 (기본)</th>
                  <th className="text-left font-medium px-1 pb-1">Lv2</th>
                  <th className="text-left font-medium px-1 pb-1">Lv3</th>
                </tr>
              </thead>
              <tbody>
                {statRows.map(s => (
                  <tr key={s.label}>
                    <td className="pr-3 py-0.5 text-foreground-soft whitespace-nowrap">{s.label}</td>
                    <td className="px-1 py-0.5">
                      <input type="number" value={form[s.baseKey] ?? ''} placeholder={s.nullable ? '-' : '0'}
                        onChange={e => setAttr(s.baseKey, e.target.value, false, s.nullable)} className={`${input} w-[84px]`} />
                    </td>
                    {UPGRADE_LEVELS.map(lv => (
                      <td key={lv} className="px-1 py-0.5">
                        <input type="number" value={levelSpecs[lv]?.[s.specKey] ?? ''} placeholder="자동"
                          onChange={e => setSpec(lv, s.specKey, e.target.value)} className={`${input} w-[84px]`} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>

            {!isCastle && (
              <div className="mt-3 pt-3 border-t border-outline-soft">
                <p className="text-[11px] text-dim mb-2">
                  성 레벨별 최대 개수 <span className="text-muted">— 섬·영토에 몇 개까지 지을 수 있는지. 비우면 무제한</span>
                </p>
                <div className="flex items-end gap-3">
                  {CASTLE_LEVELS.map(lv => (
                    <label key={lv} className="text-[11px] text-dim">성 Lv{lv}
                      <input type="number" min={0} value={castleLimits[lv] ?? ''} placeholder="무제한"
                        onChange={e => setCastleLimits(c => ({ ...c, [lv]: e.target.value }))}
                        className={`${input} w-[84px] mt-0.5`} />
                    </label>
                  ))}
                </div>
              </div>
            )}
          </td>
        </tr>
      )}
    </>
  );
}

function CreateForm({ onDone, onError }: { onDone: (m: string) => void; onError: (m: string) => void }) {
  const empty: BuildingTypeForm = { name: '', displayName: null, width: 1, height: 1, maxHp: 100, baseCostGp: 1000, upgradeCostGp: null, apCost: null, zoneRestriction: null, defensePower: null, foodProductionRate: null, unitCapacityPerLevel: null, gpProductionRate: null, buildTimeSeconds: null, upgradeTimeSeconds: null, icon: null, colorHex: null };
  const [form, setForm] = useState<BuildingTypeForm>(empty);
  const [busy, setBusy] = useState(false);
  const setNum = (k: keyof BuildingTypeForm, v: string, nullable?: boolean) =>
    setForm(f => ({ ...f, [k]: v.trim() === '' ? (nullable ? null : 0) : Number(v) }));

  const NUM_FIELDS: { key: keyof BuildingTypeForm; label: string; nullable?: boolean }[] = [
    { key: 'width', label: '너비' }, { key: 'height', label: '높이' },
    { key: 'maxHp', label: 'HP' }, { key: 'baseCostGp', label: '건설비용' },
    { key: 'zoneRestriction', label: 'Zone제한', nullable: true }, { key: 'defensePower', label: '방어력', nullable: true },
    { key: 'apCost', label: 'AP상점가', nullable: true },
    { key: 'buildTimeSeconds', label: '건설시간(초)', nullable: true },
  ];

  const create = async () => {
    if (busy) return;
    if (!form.name?.trim()) { onError('건물 코드를 입력하세요.'); return; }
    setBusy(true);
    try { await createBuildingType(form); onDone(`${form.name.toUpperCase()} 생성됨`); setForm(empty); }
    catch (e) { onError(e instanceof ApiError ? e.message : '생성 실패'); }
    finally { setBusy(false); }
  };

  return (
    <div>
      <p className="text-muted text-[11px] mb-2">장식 건물만 추가 가능(기능은 코드 매칭이라 신규 생성 불가) · HP·방어력만 유효</p>
      <div className="flex flex-wrap items-end gap-2">
        <label className="text-[11px] text-dim">코드(영문)
          <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="예: STATUE" className={`${input} w-28 mt-0.5`} />
        </label>
        <label className="text-[11px] text-dim">표시명(한글)
          <input value={form.displayName ?? ''} onChange={e => setForm(f => ({ ...f, displayName: e.target.value || null }))} placeholder="예: 동상" className={`${input} w-24 mt-0.5`} />
        </label>
        <label className="text-[11px] text-dim">아이콘
          <input value={form.icon ?? ''} onChange={e => setForm(f => ({ ...f, icon: e.target.value || null }))} placeholder="🗽" className={`${input} w-12 mt-0.5`} />
        </label>
        <label className="text-[11px] text-dim">색
          <input value={form.colorHex ?? ''} onChange={e => setForm(f => ({ ...f, colorHex: e.target.value || null }))} placeholder="#rrggbb" className={`${input} w-20 mt-0.5`} />
        </label>
        {NUM_FIELDS.map(f => (
          <label key={f.key} className="text-[11px] text-dim">{f.label}
            <input type="number" value={form[f.key] ?? ''} placeholder={f.nullable ? '-' : '0'}
              onChange={e => setNum(f.key, e.target.value, f.nullable)} className={`${input} w-[68px] mt-0.5`} />
          </label>
        ))}
        <button onClick={() => void create()} disabled={busy} className="h-8 px-4 rounded-md bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">추가</button>
      </div>
    </div>
  );
}
