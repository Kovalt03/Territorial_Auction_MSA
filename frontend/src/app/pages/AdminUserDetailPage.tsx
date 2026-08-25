import { useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router';

import { fetchAdminUser, changeUserStatus } from '../api/admin';
import { ApiError } from '../api/client';

import { WalletAdjustForm } from './WalletAdjustForm';
import { UserActivityPanel } from './UserActivityPanel';
import { SendNotificationForm } from './SendNotificationForm';

import type { AdminUserDetail, UserStatus } from '../types/admin';

const STATUS_STYLE: Record<UserStatus, string> = {
  ACTIVE: 'text-gp',
  SUSPENDED: 'text-danger',
  WITHDRAWN: 'text-muted',
};

export function AdminUserDetailPage() {
  const { id } = useParams<{ id: string }>();
  const numericId = Number(id);

  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (!numericId) return;
    fetchAdminUser(numericId)
      .then(d => { setDetail(d); setError(null); })
      .catch(e => {
        setError(e instanceof ApiError ? e.message : '유저를 불러올 수 없습니다.');
        console.warn('[AdminUserDetail] fetch failed', e);
      });
  }, [numericId]);

  if (!numericId) return <Navigate to="/admin/users" replace />;

  const handleStatus = async (status: UserStatus) => {
    if (!detail || isSaving || !reason.trim()) return;
    setIsSaving(true); setError(null);
    try {
      const d = await changeUserStatus(numericId, status, reason.trim());
      setDetail(d); setReason('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '상태 변경에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  if (error && !detail) return <div className="p-6"><p className="text-danger text-xs">⚠ {error}</p></div>;
  if (!detail) return <div className="p-6"><p className="text-muted text-sm">불러오는 중...</p></div>;

  const isActive = detail.status === 'ACTIVE';
  const isAdmin = detail.role === 'ADMIN';

  return (
    <div className="h-full overflow-auto p-6 max-w-2xl">
      <Link to="/admin/users" className="text-muted text-xs hover:text-foreground-soft">← 목록으로</Link>
      <div className="flex items-center gap-3 mt-3 mb-5">
        <h2 className="font-bold text-lg">{detail.nickname}</h2>
        <span className={`text-xs font-bold ${STATUS_STYLE[detail.status]}`}>{detail.status}</span>
        {isAdmin && <span className="text-gold text-xs font-bold">ADMIN</span>}
      </div>

      <div className="grid grid-cols-2 gap-x-6 gap-y-3 text-xs mb-6 bg-panel border border-outline rounded-xl p-4">
        <Info label="아이디" value={detail.username} />
        <Info label="이메일" value={detail.email} />
        <Info label="가입일" value={detail.createdAt.slice(0, 10)} />
        <Info label="보유 영토" value={`${detail.territoryCount}개`} />
        <Info label="AP (가용/잠금)" value={`${detail.availableAp} / ${detail.lockedAp}`} />
        <Info label="GP" value={`${detail.availableGp}`} />
        <Info label="식량" value={`${detail.availableFood}`} />
      </div>

      {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}

      <section className="bg-panel border border-outline rounded-xl p-4 mb-4">
        <h3 className="font-bold text-sm mb-2">계정 상태</h3>
        <input value={reason} onChange={e => setReason(e.target.value)} placeholder="사유 (필수)"
          className="w-full bg-elevated border border-outline rounded-md px-2 h-9 text-foreground text-xs outline-none focus:border-primary mb-2" />
        {isActive ? (
          <button onClick={() => void handleStatus('SUSPENDED')} disabled={isSaving || isAdmin || !reason.trim()}
            title={isAdmin ? '관리자 계정은 정지할 수 없습니다.' : ''}
            className="w-full h-9 rounded-lg border border-danger text-danger text-xs font-bold hover:bg-elevated disabled:opacity-40">
            계정 정지
          </button>
        ) : (
          <button onClick={() => void handleStatus('ACTIVE')} disabled={isSaving || !reason.trim()}
            className="w-full h-9 rounded-lg border border-gp text-gp text-xs font-bold hover:bg-elevated disabled:opacity-40">
            계정 활성화
          </button>
        )}
      </section>

      <section className="bg-panel border border-outline rounded-xl p-4 mb-4">
        <h3 className="font-bold text-sm mb-2">재화 조정</h3>
        <WalletAdjustForm userId={numericId} currentAp={detail.availableAp} currentGp={detail.availableGp} onAdjusted={setDetail} />
      </section>

      <section className="bg-panel border border-outline rounded-xl p-4 mb-4">
        <h3 className="font-bold text-sm mb-3">활동 내역</h3>
        <UserActivityPanel userId={numericId} />
      </section>

      <section className="bg-panel border border-outline rounded-xl p-4">
        <h3 className="font-bold text-sm mb-2">알림 발송</h3>
        <SendNotificationForm userId={numericId} />
      </section>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span className="text-dim">{label}</span>
      <p className="text-foreground mt-0.5">{value}</p>
    </div>
  );
}
