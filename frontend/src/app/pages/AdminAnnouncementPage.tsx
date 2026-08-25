import { useEffect, useState } from 'react';

import { fetchAdminAnnouncement, updateAnnouncement } from '../api/admin';
import { ApiError } from '../api/client';

export function AdminAnnouncementPage() {
  const [active, setActive] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchAdminAnnouncement()
      .then(r => { setActive(r.active); setMessage(r.message); })
      .catch(e => { setError(e instanceof ApiError ? e.message : '공지를 불러올 수 없습니다.'); console.warn('[AdminAnnouncement] fetch', e); });
  }, []);

  const save = async (nextActive: boolean) => {
    if (busy) return;
    if (nextActive && !message.trim()) { setError('노출하려면 메시지를 입력하세요.'); return; }
    setBusy(true); setError(null); setResult(null);
    try {
      const r = await updateAnnouncement(nextActive, message.trim());
      setActive(r.active);
      setResult(nextActive ? '공지를 노출했습니다.' : '공지를 내렸습니다.');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '저장에 실패했습니다.');
    } finally { setBusy(false); }
  };

  return (
    <div className="h-full overflow-auto p-6">
      <div className="max-w-lg">
        <h2 className="font-bold text-base mb-1">공지 배너</h2>
        <p className="text-muted text-xs mb-4">모든 사용자 화면 상단에 노출되는 공지/이벤트 배너입니다.</p>

        {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}
        {result && <p className="text-gp text-xs mb-3">✓ {result}</p>}

        <div className="bg-panel border border-outline rounded-xl p-4">
          <div className="flex items-center justify-between mb-3">
            <span className="font-semibold text-sm">현재 상태</span>
            <span className="text-xs font-bold" style={{ color: active ? 'var(--color-gp)' : 'var(--color-muted)' }}>
              {active ? '● 노출 중' : '○ 숨김'}
            </span>
          </div>

          <label className="block text-dim mb-1.5 text-[11px] font-medium">공지 메시지 (최대 200자)</label>
          <textarea value={message} onChange={e => setMessage(e.target.value)} maxLength={200} rows={3}
            placeholder="예: 12/25 02:00~04:00 서버 점검 예정입니다."
            className="w-full bg-elevated border border-outline rounded-md px-2 py-2 text-foreground text-xs outline-none focus:border-primary mb-1 resize-none" />
          <p className="text-dim text-[10px] text-right mb-3">{message.length}/200</p>

          {message.trim() && (
            <div className="mb-3">
              <p className="text-dim text-[10px] mb-1">미리보기</p>
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-md text-[11px] font-medium"
                style={{ background: 'color-mix(in srgb, var(--color-primary) 15%, transparent)', color: 'var(--color-primary)' }}>
                <span>📢</span><span className="truncate">{message}</span>
              </div>
            </div>
          )}

          <div className="flex gap-2">
            {active ? (
              <>
                <button onClick={() => void save(true)} disabled={busy || !message.trim()}
                  className="flex-1 h-9 rounded-lg bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">
                  변경 저장
                </button>
                <button onClick={() => void save(false)} disabled={busy}
                  className="flex-1 h-9 rounded-lg border border-outline text-muted text-xs font-bold hover:bg-elevated disabled:opacity-40">
                  공지 내리기
                </button>
              </>
            ) : (
              <button onClick={() => void save(true)} disabled={busy || !message.trim()}
                className="flex-1 h-9 rounded-lg bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">
                공지 노출
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
