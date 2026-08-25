import { Fragment, useEffect, useState } from 'react';

import { fetchAuditLogs } from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminAuditLog } from '../types/admin';

const PAGE_SIZE = 30;
const TARGET_TYPES = ['', 'USER', 'TERRITORY', 'CONTINENT', 'SETTING', 'CHAT_MESSAGE'];

export function AdminAuditLogPage() {
  const [logs, setLogs] = useState<AdminAuditLog[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [action, setAction] = useState('');
  const [actionInput, setActionInput] = useState('');
  const [targetType, setTargetType] = useState('');
  const [expanded, setExpanded] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchAuditLogs({ action: action || undefined, targetType: targetType || undefined, page, size: PAGE_SIZE })
      .then(r => { setLogs(r.logs); setTotalCount(r.totalCount); setError(null); })
      .catch(e => {
        setError(e instanceof ApiError ? e.message : '감사 로그를 불러올 수 없습니다.');
        console.warn('[AdminAuditLog] fetch failed', e);
      });
  }, [page, action, targetType]);

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  const applyAction = () => { setAction(actionInput.trim()); setPage(0); };

  return (
    <div className="h-full overflow-auto p-6">
      <div className="flex items-center gap-2 mb-4">
        <input value={actionInput} onChange={e => setActionInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') applyAction(); }}
          placeholder="action 검색 (예: WALLET_ADJUST)"
          className="bg-elevated border border-outline rounded-md px-3 h-9 text-foreground text-xs outline-none focus:border-primary w-64" />
        <button onClick={applyAction} className="h-9 px-4 rounded-md bg-primary text-surface text-xs font-bold hover:brightness-110">검색</button>
        <select value={targetType} onChange={e => { setTargetType(e.target.value); setPage(0); }}
          className="h-9 px-2 rounded-md bg-elevated border border-outline text-foreground text-xs outline-none focus:border-primary">
          {TARGET_TYPES.map(t => <option key={t} value={t}>{t === '' ? '대상 전체' : t}</option>)}
        </select>
        <span className="ml-auto text-[11px] text-muted">총 {totalCount}건</span>
      </div>

      {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}

      <table className="w-full text-xs">
        <thead className="text-dim text-[11px] border-b border-outline">
          <tr>
            <th className="text-left font-medium py-2 px-2 w-40">시각</th>
            <th className="text-left font-medium py-2 px-2">관리자</th>
            <th className="text-left font-medium py-2 px-2">action</th>
            <th className="text-left font-medium py-2 px-2">대상</th>
            <th className="text-left font-medium py-2 px-2 w-16">상세</th>
          </tr>
        </thead>
        <tbody>
          {logs.map(l => (
            <Fragment key={l.id}>
              <tr className="border-b border-outline-soft hover:bg-panel">
                <td className="py-2 px-2 text-muted">{l.createdAt.slice(0, 19).replace('T', ' ')}</td>
                <td className="py-2 px-2">{l.adminNickname ?? `#${l.adminUserId}`}</td>
                <td className="py-2 px-2 font-semibold text-primary">{l.action}</td>
                <td className="py-2 px-2 text-muted">{l.targetType ?? '-'}{l.targetId != null ? ` #${l.targetId}` : ''}</td>
                <td className="py-2 px-2">
                  {l.detailJson && l.detailJson !== '{}' && (
                    <button onClick={() => setExpanded(expanded === l.id ? null : l.id)}
                      className="text-dim hover:text-foreground-soft underline">{expanded === l.id ? '접기' : '보기'}</button>
                  )}
                </td>
              </tr>
              {expanded === l.id && (
                <tr className="border-b border-outline-soft">
                  <td colSpan={5} className="px-2 py-2 bg-panel-deep">
                    <pre className="text-[11px] text-foreground-soft whitespace-pre-wrap break-all">{formatJson(l.detailJson)}</pre>
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
          {logs.length === 0 && (
            <tr><td colSpan={5} className="py-8 text-center text-muted">로그가 없습니다.</td></tr>
          )}
        </tbody>
      </table>

      <div className="flex items-center justify-center gap-3 mt-4 text-xs">
        <button disabled={page <= 0} onClick={() => setPage(p => p - 1)}
          className="px-3 h-8 rounded-md border border-outline text-muted disabled:opacity-30 hover:text-foreground-soft">이전</button>
        <span className="text-dim">{page + 1} / {totalPages}</span>
        <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}
          className="px-3 h-8 rounded-md border border-outline text-muted disabled:opacity-30 hover:text-foreground-soft">다음</button>
      </div>
    </div>
  );
}

function formatJson(raw: string | null): string {
  if (!raw) return '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}
