import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import { fetchAdminUsers } from '../api/admin';
import { ApiError } from '../api/client';

import { BulkUserActionBar } from './BulkUserActionBar';

import type { AdminUser, UserStatus, UserStatusFilter } from '../types/admin';

const PAGE_SIZE = 20;
const STATUS_FILTERS: { value: UserStatusFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'ACTIVE', label: '활성' },
  { value: 'SUSPENDED', label: '정지' },
  { value: 'WITHDRAWN', label: '탈퇴' },
];
const STATUS_STYLE: Record<UserStatus, string> = {
  ACTIVE: 'text-gp',
  SUSPENDED: 'text-danger',
  WITHDRAWN: 'text-muted',
};

export function AdminUserListPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? '0');
  const status = (params.get('status') ?? 'ALL') as UserStatusFilter;
  const keyword = params.get('keyword') ?? '';

  const [input, setInput] = useState(keyword);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [message, setMessage] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchAdminUsers({
      keyword: keyword || undefined,
      status: status === 'ALL' ? undefined : status,
      page,
      size: PAGE_SIZE,
    })
      .then(r => { setUsers(r.users); setTotalCount(r.totalCount); setError(null); })
      .catch(e => {
        setError(e instanceof ApiError ? e.message : '유저를 불러올 수 없습니다.');
        console.warn('[AdminUserList] fetch failed', e);
      });
  }, [page, status, keyword, refreshKey]);

  const patch = (next: Record<string, string>) => {
    setSelected(new Set());
    setMessage(null);
    const merged: Record<string, string> = { keyword, status, page: String(page), ...next };
    const clean: Record<string, string> = {};
    Object.entries(merged).forEach(([k, v]) => {
      if (v && v !== 'ALL' && !(k === 'page' && v === '0')) clean[k] = v;
    });
    setParams(clean);
  };

  const toggle = (id: number) =>
    setSelected(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  const allSelected = users.length > 0 && users.every(u => selected.has(u.userId));
  const toggleAll = () =>
    setSelected(s => {
      const n = new Set(s);
      users.forEach(u => (allSelected ? n.delete(u.userId) : n.add(u.userId)));
      return n;
    });

  const handleBulkDone = (msg: string) => {
    setSelected(new Set());
    setMessage(msg);
    setRefreshKey(k => k + 1);
  };

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  return (
    <div className="h-full flex flex-col">
      <div className="flex-1 overflow-auto p-6">
        <div className="flex items-center gap-2 mb-4">
          <input value={input} onChange={e => setInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') patch({ keyword: input, page: '0' }); }}
            placeholder="닉네임 / 아이디 검색"
            className="bg-elevated border border-outline rounded-md px-3 h-9 text-foreground text-xs outline-none focus:border-primary w-64" />
          <button onClick={() => patch({ keyword: input, page: '0' })}
            className="h-9 px-4 rounded-md bg-primary text-surface text-xs font-bold hover:brightness-110">검색</button>
          <div className="flex gap-1 ml-2">
            {STATUS_FILTERS.map(s => (
              <button key={s.value} onClick={() => patch({ status: s.value, page: '0' })}
                className={`px-2.5 h-9 rounded-md text-[11px] font-semibold border ${status === s.value ? 'border-primary text-primary' : 'border-outline text-muted hover:text-foreground-soft'}`}>
                {s.label}
              </button>
            ))}
          </div>
          <span className="ml-auto text-[11px] text-muted">총 {totalCount}명</span>
        </div>

        {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}
        {message && <p className="text-gp text-xs mb-3">✓ {message}</p>}

        <table className="w-full text-xs">
          <thead className="text-dim text-[11px] border-b border-outline">
            <tr>
              <th className="w-8 px-2"><input type="checkbox" checked={allSelected} onChange={toggleAll} /></th>
              <th className="text-left font-medium py-2 px-2">닉네임</th>
              <th className="text-left font-medium py-2 px-2">아이디</th>
              <th className="text-left font-medium py-2 px-2">이메일</th>
              <th className="text-left font-medium py-2 px-2">상태</th>
              <th className="text-left font-medium py-2 px-2">권한</th>
              <th className="text-left font-medium py-2 px-2">가입일</th>
            </tr>
          </thead>
          <tbody>
            {users.map(u => (
              <tr key={u.userId} onClick={() => navigate(`/admin/users/${u.userId}`)}
                className="border-b border-outline-soft cursor-pointer hover:bg-panel">
                <td className="px-2" onClick={e => e.stopPropagation()}>
                  <input type="checkbox" checked={selected.has(u.userId)} onChange={() => toggle(u.userId)} />
                </td>
                <td className="py-2 px-2 font-semibold">{u.nickname}</td>
                <td className="py-2 px-2 text-muted">{u.username}</td>
                <td className="py-2 px-2 text-muted">{u.email}</td>
                <td className={`py-2 px-2 font-bold ${STATUS_STYLE[u.status]}`}>{u.status}</td>
                <td className="py-2 px-2">{u.role === 'ADMIN' ? <span className="text-gold font-bold">ADMIN</span> : 'USER'}</td>
                <td className="py-2 px-2 text-muted">{u.createdAt.slice(0, 10)}</td>
              </tr>
            ))}
            {users.length === 0 && (
              <tr><td colSpan={7} className="py-8 text-center text-muted">결과가 없습니다.</td></tr>
            )}
          </tbody>
        </table>

        <div className="flex items-center justify-center gap-3 mt-4 text-xs">
          <button disabled={page <= 0} onClick={() => patch({ page: String(page - 1) })}
            className="px-3 h-8 rounded-md border border-outline text-muted disabled:opacity-30 hover:text-foreground-soft">이전</button>
          <span className="text-dim">{page + 1} / {totalPages}</span>
          <button disabled={page + 1 >= totalPages} onClick={() => patch({ page: String(page + 1) })}
            className="px-3 h-8 rounded-md border border-outline text-muted disabled:opacity-30 hover:text-foreground-soft">다음</button>
        </div>
      </div>

      {selected.size > 0 && (
        <BulkUserActionBar userIds={[...selected]} onDone={handleBulkDone} onClear={() => setSelected(new Set())} />
      )}
    </div>
  );
}
