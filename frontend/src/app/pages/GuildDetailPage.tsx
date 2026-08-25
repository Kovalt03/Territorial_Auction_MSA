import { useState, useEffect, useCallback } from 'react';
import { ApiError } from '../api/client';
import { useParams, useNavigate } from 'react-router';

import { GNB } from '../components/GNB';
import { Button } from '../components/Button';
import { ChatPanel } from '../components/ChatPanel';
import { LoadingState } from '../components/LoadingState';
import { useApp } from '../context/AppContext';
import { useMyGuild } from '../hooks/useMyGuild';
import {
  fetchGuildDetail, fetchGuildApplications,
  joinGuild, leaveGuild,
  approveApplication, rejectApplication, kickMember, transferMaster, updateGuild,
  type GuildDetail, type GuildApplication,
} from '../api/guild';

type Tab = 'members' | 'applications' | 'settings' | 'chat';

export function GuildDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { isLoggedIn, userId } = useApp();

  const guildId = Number(id ?? '0');

  const { myGuild, refresh: refreshMyGuild } = useMyGuild(isLoggedIn);
  const [guild, setGuild] = useState<GuildDetail | null>(null);
  const [applications, setApplications] = useState<GuildApplication[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [tab, setTab] = useState<Tab>('members');
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionDone, setActionDone] = useState<string | null>(null);
  const [isActing, setIsActing] = useState(false);
  const [confirmAction, setConfirmAction] = useState<{ message: string; onConfirm: () => void } | null>(null);

  const [editDesc, setEditDesc] = useState('');
  const [editStatus, setEditStatus] = useState<'OPEN' | 'CLOSED'>('OPEN');
  const [isSaving, setIsSaving] = useState(false);

  const isMaster = myGuild?.guildId === guildId && myGuild.myRole === 'MASTER';
  const isMember = myGuild?.guildId === guildId;

  const load = useCallback(async () => {
    setIsLoading(true);
    try {
      const detail = await fetchGuildDetail(guildId);
      setGuild(detail);
      setEditDesc(detail.description ?? '');
    } catch {
      navigate('/app/guild');
    } finally {
      setIsLoading(false);
    }
  }, [guildId, navigate]);

  useEffect(() => {
    if (!id) navigate('/app/guild');
  }, [id, navigate]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    if (!isMaster) return;
    fetchGuildApplications(guildId)
      .then(res => setApplications(res.applications))
      .catch((e) => console.warn('[GuildDetailPage] applications load failed', e));
  }, [isMaster, guildId]);

  const executeAction = async (fn: () => Promise<unknown>, successMsg?: string) => {
    setIsActing(true);
    setActionError(null);
    try {
      await fn();
      if (successMsg) {
        setActionDone(successMsg);
        setTimeout(() => setActionDone(null), 3000);
      }
      await load();
      refreshMyGuild();
    } catch (e) {
      setActionError(
        e instanceof ApiError && e.status >= 400 && e.status < 500
          ? e.message
          : '작업에 실패했습니다. 다시 시도해주세요.',
      );
    } finally {
      setIsActing(false);
    }
  };

  const handleSaveSettings = async () => {
    setIsSaving(true);
    try {
      await updateGuild(guildId, { description: editDesc || undefined, recruitingStatus: editStatus });
      await load();
    } catch {
      setActionError('설정 저장에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  if (!id) return null;

  if (isLoading) {
    return (
      <div className="flex flex-col h-screen bg-surface">
        <GNB />
        <LoadingState className="flex-1" />
      </div>
    );
  }

  if (!guild) return null;

  const tabs: [Tab, string][] = [
    ['members', '멤버'],
    ...(isMember ? [['chat', '채팅'] as [Tab, string]] : []),
    ...(isMaster
      ? [
          ['applications', `신청 (${applications.length})`] as [Tab, string],
          ['settings', '설정'] as [Tab, string],
        ]
      : []),
  ];

  return (
    <div className="flex flex-col h-screen bg-surface">
      <GNB />
      <div className="flex-1 overflow-y-auto p-6">
        <div className="max-w-3xl mx-auto">

          {/* Back */}
          <button onClick={() => navigate('/app/guild')} className="text-muted hover:text-foreground mb-4 flex items-center gap-1 text-[13px]">
            ← 길드 목록
          </button>

          {/* Guild header */}
          <div className="bg-panel border border-outline rounded-2xl p-6 mb-4">
            <div className="flex items-start gap-5">
              <div
                className="w-16 h-16 rounded-2xl flex items-center justify-center font-bold text-3xl flex-shrink-0 bg-[#00f5ff20] border-2 border-primary text-primary"
              >
                {guild.name.charAt(0)}
              </div>
              <div className="flex-1">
                <h1 className="text-foreground font-bold mb-1 text-[22px]">{guild.name}</h1>
                {guild.description && (
                  <p className="text-dim mb-3 text-[13px]">{guild.description}</p>
                )}
                <div className="flex flex-wrap gap-4 text-muted text-[13px]">
                  <span>길드장: <span className="text-primary">{guild.master.nickname}</span></span>
                  <span>멤버 {guild.memberCount}명</span>
                  <span>영토 {guild.totalTerritoryCount}개</span>
                  <span>생성일: {new Date(guild.createdAt).toLocaleDateString('ko-KR')}</span>
                </div>
              </div>
              <div className="flex flex-col gap-2 flex-shrink-0">
                {isLoggedIn && !myGuild && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => executeAction(() => joinGuild(guildId))}
                    disabled={isActing}
                  >
                    가입 신청
                  </Button>
                )}
                {isMember && !isMaster && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => setConfirmAction({
                      message: '길드에서 탈퇴하시겠습니까?',
                      onConfirm: () => void executeAction(() => leaveGuild(guildId), '탈퇴했습니다.'),
                    })}
                    disabled={isActing}
                  >
                    탈퇴
                  </Button>
                )}
              </div>
            </div>
            {actionError && <p className="text-danger mt-3 text-xs">{actionError}</p>}
            {actionDone && <p className="text-gp mt-3 text-xs">✓ {actionDone}</p>}
          </div>

          {/* Tabs */}
          <div className="flex gap-1 mb-4">
            {tabs.map(([t, label]) => (
              <button
                key={t}
                onClick={() => setTab(t)}
                className={`px-4 py-2 rounded-lg text-[13px] transition-colors ${tab === t ? 'bg-[#00f5ff20] border border-primary text-primary' : 'bg-panel border border-outline text-muted'}`}
              >
                {label}
              </button>
            ))}
          </div>

          {/* Chat tab */}
          {tab === 'chat' && isMember && (
            <div
              className="bg-panel border border-outline rounded-2xl overflow-hidden flex flex-col h-[60vh]"
            >
              <ChatPanel roomId={`room_guild_${guildId}`} />
            </div>
          )}

          {/* Members tab */}
          {tab === 'members' && (
            <div className="flex flex-col gap-2">
              {guild.members.map(m => (
                <div key={m.userId} className="card px-4 py-3 flex items-center gap-3">
                  <div
                    className={`w-9 h-9 rounded-xl flex items-center justify-center font-bold flex-shrink-0 text-sm ${m.role === 'MASTER' ? 'bg-[#ffd70020] text-gold' : 'bg-elevated text-dim'}`}
                  >
                    {m.nickname.charAt(0).toUpperCase()}
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-foreground text-sm">{m.nickname}</span>
                      {m.role === 'MASTER' && <span className="text-gold text-xs">👑 길드장</span>}
                    </div>
                    <span className="text-muted text-xs">영토 {m.territoryCount}개 · 가입일 {new Date(m.joinedAt).toLocaleDateString('ko-KR')}</span>
                  </div>
                  {isMaster && m.userId !== userId && (
                    <div className="flex gap-1">
                      <button
                        onClick={() => setConfirmAction({
                          message: `${m.nickname}에게 길드장을 이전하시겠습니까?`,
                          onConfirm: () => void executeAction(() => transferMaster(guildId, m.userId)),
                        })}
                        className="px-2 py-1 rounded text-xs border border-outline text-dim hover:border-gold hover:text-gold transition-colors"
                      >
                        이전
                      </button>
                      <button
                        onClick={() => setConfirmAction({
                          message: `${m.nickname}을 추방하시겠습니까?`,
                          onConfirm: () => void executeAction(() => kickMember(guildId, m.userId)),
                        })}
                        className="px-2 py-1 rounded text-xs border border-outline text-dim hover:border-danger hover:text-danger transition-colors"
                      >
                        추방
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}

          {/* Applications tab */}
          {tab === 'applications' && isMaster && (
            <div className="flex flex-col gap-2">
              {applications.length === 0 && (
                <div className="text-center text-muted py-12 text-sm">신청 내역이 없습니다.</div>
              )}
              {applications.map(a => (
                <div key={a.applicationId} className="card px-4 py-3 flex items-center gap-3">
                  <div className="w-9 h-9 rounded-xl bg-elevated flex items-center justify-center font-bold text-dim flex-shrink-0 text-sm">
                    {a.nickname.charAt(0).toUpperCase()}
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <p className="text-foreground text-sm">{a.nickname}</p>
                      <span className="text-muted text-xs">🏆 {a.trophyPoints.toLocaleString()}</span>
                    </div>
                    <p className="text-muted text-xs">{new Date(a.appliedAt).toLocaleString('ko-KR')}</p>
                  </div>
                  <div className="flex gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => executeAction(async () => {
                        await approveApplication(guildId, a.userId);
                        const res = await fetchGuildApplications(guildId);
                        setApplications(res.applications);
                      })}
                      disabled={isActing}
                    >
                      승인
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => executeAction(async () => {
                        await rejectApplication(guildId, a.userId);
                        const res = await fetchGuildApplications(guildId);
                        setApplications(res.applications);
                      })}
                      disabled={isActing}
                    >
                      거절
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Settings tab */}
          {tab === 'settings' && isMaster && (
            <div className="bg-panel border border-outline rounded-2xl p-5 flex flex-col gap-4">
              <div>
                <label className="text-muted block mb-1 text-xs">소개글</label>
                <textarea
                  value={editDesc}
                  onChange={e => setEditDesc(e.target.value)}
                  maxLength={200}
                  rows={4}
                  className="w-full bg-elevated border border-outline rounded-lg px-3 py-2 text-foreground outline-none focus:border-primary resize-none text-sm"
                />
              </div>
              <div>
                <label className="text-muted block mb-2 text-xs">모집 상태</label>
                <div className="flex gap-2">
                  {(['OPEN', 'CLOSED'] as const).map(s => (
                    <button
                      key={s}
                      onClick={() => setEditStatus(s)}
                      className={`px-4 py-2 rounded-lg text-sm transition-colors ${editStatus === s ? 'bg-[#00f5ff20] border border-primary text-primary' : 'bg-elevated border border-outline text-muted'}`}
                    >
                      {s === 'OPEN' ? '모집 중' : '모집 마감'}
                    </button>
                  ))}
                </div>
              </div>
              <Button
                onClick={handleSaveSettings}
                disabled={isSaving}
                fullWidth
              >
                {isSaving ? '저장 중...' : '저장'}
              </Button>
            </div>
          )}

        </div>
      </div>

      {confirmAction && (
        <div className="modal-overlay">
          <div className="bg-panel border border-outline rounded-2xl p-6 max-w-xs mx-4 text-center">
            <p className="text-foreground text-sm mb-5">{confirmAction.message}</p>
            <div className="flex gap-3">
              <button onClick={() => setConfirmAction(null)} className="btn-cancel">취소</button>
              <button
                onClick={() => { confirmAction.onConfirm(); setConfirmAction(null); }}
                className="flex-1 h-11 bg-danger rounded-xl text-white font-bold text-sm hover:brightness-110 transition-all"
              >
                확인
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
