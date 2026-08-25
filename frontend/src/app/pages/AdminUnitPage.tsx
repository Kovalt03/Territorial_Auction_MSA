import { Fragment, useEffect, useState } from 'react';

import {
  fetchAdminUnitTypes, updateUnitType, fetchUnitLevelSpecs, updateUnitLevelSpecs,
  type AdminUnitType, type UnitLevelValues,
} from '../api/admin';
import { ApiError } from '../api/client';

const input = 'w-full bg-elevated border border-outline rounded px-1.5 h-7 text-foreground text-[11px] outline-none focus:border-primary';

// 훈련으로 도달할 수 있는 레벨 (Lv1 은 생산 시점의 기본값)
const TRAIN_LEVELS = [2, 3];

type UnitForm = Omit<AdminUnitType, 'unitTypeId' | 'name'>;

const ATTR_FIELDS: { key: keyof UnitForm; label: string; w: string; text?: boolean }[] = [
  { key: 'icon', label: '아이콘', w: 'w-12', text: true },
  { key: 'colorHex', label: '색', w: 'w-20', text: true },
  { key: 'attackPower', label: '공격력', w: 'w-16' },
  { key: 'defensePower', label: '방어력', w: 'w-16' },
  { key: 'costGp', label: '생산비용(GP)', w: 'w-20' },
  { key: 'foodCost', label: '생산 식량', w: 'w-16' },
  { key: 'level', label: '요구 병영Lv', w: 'w-16' },
];

const SPEC_FIELDS: { key: keyof UnitLevelValues; label: string }[] = [
  { key: 'attackPower', label: '공격력' },
  { key: 'defensePower', label: '방어력' },
  { key: 'trainCostFood', label: '훈련 식량' },
  { key: 'requiredBarracksLevel', label: '요구 병영Lv' },
];

function toForm(u: AdminUnitType): UnitForm {
  const { unitTypeId: _id, name: _name, ...rest } = u;
  return rest;
}

export function AdminUnitPage() {
  const [units, setUnits] = useState<AdminUnitType[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchAdminUnitTypes()
      .then(r => { setUnits(r); setError(null); })
      .catch(e => { setError(e instanceof ApiError ? e.message : '유닛을 불러올 수 없습니다.'); console.warn('[AdminUnit] fetch', e); });
  };
  useEffect(load, []);

  const onDone = (msg: string) => { setMessage(msg); load(); };

  return (
    <div className="h-full overflow-auto p-6">
      <h2 className="font-bold text-base mb-1">유닛 관리</h2>
      <p className="text-muted text-[11px] mb-4">
        메인 행은 생산 기준값(Lv1), <b>상세</b>에서 훈련으로 도달하는 레벨별 값을 편집합니다.
        훈련 스펙은 네 값을 모두 비우면 그 레벨의 훈련이 사라집니다.
      </p>

      {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}
      {message && <p className="text-gp text-xs mb-3">✓ {message}</p>}

      <table className="w-full text-xs">
        <thead className="text-dim border-b border-outline">
          <tr>
            <th className="text-left font-medium py-2 px-2">유닛</th>
            <th className="text-left font-medium py-2 px-1">표시명</th>
            {ATTR_FIELDS.map(f => <th key={f.key} className="text-left font-medium py-2 px-1">{f.label}</th>)}
            <th className="text-right font-medium py-2 px-2">작업</th>
          </tr>
        </thead>
        <tbody>
          {units.map(u => <Row key={u.unitTypeId} unit={u} onDone={onDone} onError={setError} />)}
          {units.length === 0 && (
            <tr><td colSpan={ATTR_FIELDS.length + 3} className="py-8 text-center text-muted">유닛이 없습니다.</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function Row({ unit, onDone, onError }: { unit: AdminUnitType; onDone: (m: string) => void; onError: (m: string) => void }) {
  const [form, setForm] = useState<UnitForm>(toForm(unit));
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  // specs[level][field] = 문자열 입력값
  const [specs, setSpecs] = useState<Record<number, Partial<Record<keyof UnitLevelValues, string>>>>({});
  const [specsLoaded, setSpecsLoaded] = useState(false);
  useEffect(() => {
    if (!open || specsLoaded) return;
    fetchUnitLevelSpecs(unit.unitTypeId)
      .then(r => {
        const next: Record<number, Partial<Record<keyof UnitLevelValues, string>>> = {};
        TRAIN_LEVELS.forEach(lv => {
          const v = r[String(lv)];
          const row: Partial<Record<keyof UnitLevelValues, string>> = {};
          SPEC_FIELDS.forEach(f => { row[f.key] = v && v[f.key] != null ? String(v[f.key]) : ''; });
          next[lv] = row;
        });
        setSpecs(next); setSpecsLoaded(true);
      })
      .catch(e => { onError(e instanceof ApiError ? e.message : '훈련 스펙을 불러올 수 없습니다.'); console.warn('[AdminUnit] specs', e); });
  }, [open, specsLoaded, unit.unitTypeId, onError]);

  const setAttr = (k: keyof UnitForm, v: string, text?: boolean) =>
    setForm(f => ({ ...f, [k]: text ? (v || null) : v.trim() === '' ? 0 : Number(v) }));

  const save = async () => {
    if (busy) return;
    setBusy(true);
    try {
      await updateUnitType(unit.unitTypeId, form);
      if (specsLoaded) {
        const payload: Record<number, UnitLevelValues> = {};
        TRAIN_LEVELS.forEach(lv => {
          const row = specs[lv] ?? {};
          const num = (k: keyof UnitLevelValues) => (row[k]?.trim() ? Number(row[k]) : null);
          payload[lv] = {
            attackPower: num('attackPower'), defensePower: num('defensePower'),
            trainCostFood: num('trainCostFood'), requiredBarracksLevel: num('requiredBarracksLevel'),
          };
        });
        await updateUnitLevelSpecs(unit.unitTypeId, payload);
      }
      onDone(`${unit.name} 저장됨`);
    } catch (e) {
      onError(e instanceof ApiError ? e.message : '저장 실패');
    } finally { setBusy(false); }
  };

  return (
    <Fragment>
      <tr className="border-b border-outline-soft">
        <td className="py-1.5 px-2 font-semibold whitespace-nowrap">
          <span className="mr-1">{form.icon || '🎖'}</span>{unit.name}
        </td>
        <td className="py-1.5 px-1">
          <input value={form.displayName ?? ''} placeholder="한글명"
            onChange={e => setForm(f => ({ ...f, displayName: e.target.value || null }))} className={`${input} w-20`} />
        </td>
        {ATTR_FIELDS.map(f => (
          <td key={f.key} className="py-1.5 px-1">
            <input type={f.text ? 'text' : 'number'} value={form[f.key] ?? ''}
              placeholder={f.text ? (f.key === 'colorHex' ? '#rgb' : '🎖') : '0'}
              onChange={e => setAttr(f.key, e.target.value, f.text)} className={`${input} ${f.w}`} />
          </td>
        ))}
        <td className="py-1.5 px-2 text-right whitespace-nowrap">
          <button onClick={() => setOpen(o => !o)} className="text-dim hover:text-foreground-soft mr-2">{open ? '상세 ▾' : '상세 ▸'}</button>
          <button onClick={() => void save()} disabled={busy} className="text-primary font-bold hover:brightness-125 disabled:opacity-30">저장</button>
        </td>
      </tr>
      {open && (
        <tr className="bg-surface border-b border-outline-soft">
          <td colSpan={ATTR_FIELDS.length + 3} className="py-2 px-3">
            <p className="text-[11px] text-dim mb-2">
              훈련 레벨별 값 <span className="text-muted">— 네 값을 모두 비우면 그 레벨로 훈련할 수 없습니다</span>
            </p>
            <table className="text-[11px]">
              <thead className="text-dim">
                <tr>
                  <th className="text-left font-medium pr-3 pb-1">항목</th>
                  {TRAIN_LEVELS.map(lv => <th key={lv} className="text-left font-medium px-1 pb-1">Lv{lv}</th>)}
                </tr>
              </thead>
              <tbody>
                {SPEC_FIELDS.map(f => (
                  <tr key={f.key}>
                    <td className="pr-3 py-0.5 text-foreground-soft whitespace-nowrap">{f.label}</td>
                    {TRAIN_LEVELS.map(lv => (
                      <td key={lv} className="px-1 py-0.5">
                        <input type="number" value={specs[lv]?.[f.key] ?? ''} placeholder="없음"
                          onChange={e => setSpecs(s => ({ ...s, [lv]: { ...s[lv], [f.key]: e.target.value } }))}
                          className={`${input} w-[84px]`} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </td>
        </tr>
      )}
    </Fragment>
  );
}
