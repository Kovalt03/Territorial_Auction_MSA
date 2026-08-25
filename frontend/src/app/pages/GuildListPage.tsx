import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router';

import { useApp } from '../context/AppContext';
import { useMyGuild } from '../hooks/useMyGuild';
import {
  fetchGuildList, createGuild,
  joinGuild, cancelJoinGuild,
  type GuildSummary,
} from '../api/guild';
import { ApiError } from '../api/client';

import { GNB } from '../components/GNB';
import { Button } from '../components/Button';
import { Badge } from '../components/Badge';
import { LoadingState } from '../components/LoadingState';

const PAGE_SIZE = 20;

export function GuildListPage() {
  const navigate = useNavigate();
  const { isLoggedIn } = useApp();

  const [guilds, setGuilds] = useState<GuildSummary[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { myGuild } = useMyGuild(isLoggedIn);

  const [showCreate, setShowCreate] = useState(false);
  const [createName, setCreateName] = useState('');
  const [createDesc, setCreateDesc] = useState('');
  const [createError, setCreateError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  const [joiningId, setJoiningId] = useState<number | null>(null);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const loadList = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await fetchGuildList({ page, size: PAGE_SIZE, search: search || undefined });
      setGuilds(res.guilds);
      setTotalCount(res.totalCount);
    } finally {
      setIsLoading(false);
    }
  }, [page, search]);

  useEffect(() => { loadList(); }, [loadList]);

  const handleSearch = () => { setSearch(searchInput); setPage(0); };

  const handleCreate = async () => {
    if (!createName.trim()) { setCreateError('길드명을 입력해주세요.'); return; }
    setIsCreating(true);
    setCreateError(null);
    try {
      const res = await createGuild({ name: createName.trim(), description: createDesc.trim() || undefined });
      setShowCreate(false);
      navigate(`/app/guild/${res.guildId}`);
    } catch (e: unknown) {
      const isConflict = e instanceof ApiError && e.status === 409;
      setCreateError(isConflict ? '이미 존재하는 길드명이거나 이미 길드에 소속되어 있습니다.' : '길드 생성에 실패했습니다.');
    } finally {
      setIsCreating(false);
    }
  };

  const handleJoin = async (guildId: number) => {
    setJoiningId(guildId);
    setActionError(null);
    try {
      await joinGuild(guildId);
      setPendingId(guildId);
    } catch (e) {
      setActionError(
        e instanceof ApiError && e.status >= 400 && e.status < 500
          ? e.message
          : '가입 신청에 실패했습니다.',
      );
    } finally {
      setJoiningId(null);
    }
  };

  const handleCancelJoin = async (guildId: number) => {
    setJoiningId(guildId);
    try {
      await cancelJoinGuild(guildId);
      setPendingId(null);
    } catch {
      setActionError('취소에 실패했습니다.');
    } finally {
      setJoiningId(null);
    }
  };

  const totalPages = Math.ceil(totalCount / PAGE_SIZE);

  return (
    <div className="flex flex-col h-screen bg-surface">
      <GNB />
      <div className="flex-1 overflow-y-auto p-6">
        <div className="max-w-4xl mx-auto">

          {/* Header */}
          <div className="flex items-center justify-between mb-6">
            <div>
              <h1 className="text-foreground font-bold text-2xl">길드</h1>
              <p className="text-muted text-[13px]">총 {totalCount}개 길드</p>
            </div>
            {isLoggedIn && !myGuild && (
              <Button onClick={() => setShowCreate(true)}>
                + 길드 생성
              </Button>
            )}
            {isLoggedIn && myGuild && (
              <Button
                variant="ghost"
                onClick={() => navigate(`/app/guild/${myGuild.guildId}`)}
              >
                내 길드 보기
              </Button>
            )}
          </div>

          {/* Search */}
          <div className="flex gap-2 mb-5">
            <input
              value={searchInput}
              onChange={e => setSearchInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
              placeholder="길드명 검색..."
              className="flex-1 h-10 bg-panel border border-outline rounded-lg px-4 text-foreground outline-none focus:border-primary placeholder-muted text-sm"
            />
            <Button variant="secondary" onClick={handleSearch}>
              검색
            </Button>
          </div>

          {actionError && (
            <p className="text-danger mb-3 text-[13px]">{actionError}</p>
          )}

          {/* List */}
          {isLoading ? (
            <LoadingState className="py-20" />
          ) : guilds.length === 0 ? (
            <div className="text-center text-muted py-20 text-sm">검색 결과가 없습니다.</div>
          ) : (
            <div className="flex flex-col gap-3">
              {guilds.map(g => {
                const isMine = myGuild?.guildId === g.guildId;
                const isPending = pendingId === g.guildId;
                return (
                  <div
                    key={g.guildId}
                    className="card p-4 flex items-center gap-4 hover:border-muted transition-colors cursor-pointer"
                    onClick={() => navigate(`/app/guild/${g.guildId}`)}
                  >
                    <div
                      className="w-12 h-12 rounded-xl flex items-center justify-center font-bold flex-shrink-0 text-xl bg-[#00f5ff20] border-2 border-primary text-primary"
                    >
                      {g.guildName.charAt(0)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-foreground font-semibold truncate text-[15px]">{g.guildName}</span>
                        <Badge variant={g.recruitingStatus === 'OPEN' ? 'success' : 'danger'} className="flex-shrink-0">
                          {g.recruitingStatus === 'OPEN' ? '모집 중' : '모집 마감'}
                        </Badge>
                      </div>
                      <div className="flex gap-4 text-muted text-xs">
                        <span>길드장: {g.masterNickname}</span>
                        <span>멤버 {g.memberCount}/{g.maxMembers}</span>
                        <span>영토 {g.totalTerritories}개</span>
                        <span>🏆 {g.totalTrophyPoints.toLocaleString()}</span>
                      </div>
                    </div>
                    {isLoggedIn && !myGuild && g.recruitingStatus === 'OPEN' && (
                      <button
                        onClick={e => { e.stopPropagation(); isPending ? handleCancelJoin(g.guildId) : handleJoin(g.guildId); }}
                        disabled={joiningId === g.guildId}
                        className="px-3 py-1.5 rounded-lg text-xs font-semibold flex-shrink-0 transition-colors"
                        style={isPending
                          ? { background: '#2a3050', color: '#8892b0', border: '1px solid #354064' }
                          : { background: '#00f5ff20', color: '#00f5ff', border: '1px solid #00f5ff' }
                        }
                      >
                        {joiningId === g.guildId ? '처리 중...' : isPending ? '신청 취소' : '가입 신청'}
                      </button>
                    )}
                    {isMine && (
                      <span className="text-xs text-primary flex-shrink-0">내 길드</span>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-6">
              {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => i + Math.max(0, page - 2)).map(p => (
                <button
                  key={p}
                  onClick={() => setPage(p)}
                  className={`w-8 h-8 rounded text-xs transition-colors ${p === page ? 'bg-primary text-surface font-bold' : 'bg-panel border border-outline text-dim'}`}
                >
                  {p + 1}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Create modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setShowCreate(false)}>
          <div className="bg-panel border border-outline rounded-2xl p-6 w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <h2 className="text-foreground font-bold mb-4 text-lg">길드 생성</h2>
            <div className="flex flex-col gap-3">
              <div>
                <label className="text-muted mb-1 block text-xs">길드명 *</label>
                <input
                  value={createName}
                  onChange={e => setCreateName(e.target.value)}
                  maxLength={20}
                  placeholder="2~20자"
                  className="w-full bg-elevated border border-outline rounded-lg px-3 py-2 text-foreground outline-none focus:border-primary text-sm"
                />
              </div>
              <div>
                <label className="text-muted mb-1 block text-xs">소개글</label>
                <textarea
                  value={createDesc}
                  onChange={e => setCreateDesc(e.target.value)}
                  maxLength={200}
                  rows={3}
                  placeholder="길드를 소개해주세요 (선택)"
                  className="w-full bg-elevated border border-outline rounded-lg px-3 py-2 text-foreground outline-none focus:border-primary resize-none text-sm"
                />
              </div>
              {createError && <p className="text-danger text-xs">{createError}</p>}
              <div className="flex gap-2 mt-2">
                <Button variant="secondary" onClick={() => setShowCreate(false)} className="flex-1">취소</Button>
                <Button onClick={handleCreate} disabled={isCreating} className="flex-1">
                  {isCreating ? '생성 중...' : '생성'}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
