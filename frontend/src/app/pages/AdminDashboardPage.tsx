import { useEffect, useState } from 'react';

import { fetchDashboard } from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminDashboard } from '../types/admin';

export function AdminDashboardPage() {
  const [data, setData] = useState<AdminDashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchDashboard()
      .then(setData)
      .catch(e => {
        setError(e instanceof ApiError ? e.message : '지표를 불러올 수 없습니다.');
        console.warn('[AdminDashboard] fetch failed', e);
      });
  }, []);

  if (error) return <div className="p-6"><p className="text-danger text-xs">⚠ {error}</p></div>;
  if (!data) return <div className="p-6"><p className="text-muted text-sm">불러오는 중...</p></div>;

  return (
    <div className="h-full overflow-auto p-6">
      <h2 className="font-bold text-base mb-4">운영 지표</h2>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
        <Card label="전체 유저" value={data.totalUsers} accent="var(--color-primary)" />
        <Card label="활성 유저" value={data.activeUsers} accent="var(--color-gp)" />
        <Card label="정지 유저" value={data.suspendedUsers} accent="var(--color-danger)" />
        <Card label="진행 중 경매" value={data.activeAuctions} accent="var(--color-flare)" />
        <Card label="경매중 영토" value={data.biddingTerritories} accent="var(--color-flare)" />
        <Card label="점유 영토" value={data.occupiedTerritories} accent="var(--color-secondary)" />
        <Card label="유휴 영토" value={data.idleTerritories} accent="var(--color-muted)" />
        <Card label="시즌" value={data.currentSeasonNumber != null ? `S${data.currentSeasonNumber}` : '없음'} accent="var(--color-gold)" />
      </div>

      <h3 className="font-bold text-sm mb-2">유통 재화</h3>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-5">
        <Card label="총 가용 AP" value={data.totalAvailableAp.toLocaleString()} accent="var(--color-ap)" />
        <Card label="총 가용 GP" value={data.totalAvailableGp.toLocaleString()} accent="var(--color-gp)" />
      </div>

      {data.currentSeasonNumber != null && (
        <p className="text-[11px] text-muted">
          현재 시즌 S{data.currentSeasonNumber} · 시작 {data.currentSeasonStartedAt?.slice(0, 16).replace('T', ' ')}
          {data.currentSeasonEndedAt ? ` · 종료예정 ${data.currentSeasonEndedAt.slice(0, 16).replace('T', ' ')}` : ' · 무기한'}
        </p>
      )}
    </div>
  );
}

function Card({ label, value, accent }: { label: string; value: number | string; accent: string }) {
  return (
    <div className="bg-panel border border-outline rounded-xl p-4">
      <p className="text-[11px] text-muted mb-1">{label}</p>
      <p className="text-2xl font-black" style={{ color: accent }}>{value}</p>
    </div>
  );
}
