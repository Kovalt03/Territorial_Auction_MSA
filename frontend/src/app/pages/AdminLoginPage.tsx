import { useState } from 'react';
import { useNavigate } from 'react-router';

import { adminLogin, setupAdminTotp } from '../api/admin';
import { ApiError } from '../api/client';

import type { TotpSetupResponse } from '../types/admin';

export function AdminLoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [totpCode, setTotpCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [setup, setSetup] = useState<TotpSetupResponse | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;
    setError(null);
    setIsSubmitting(true);
    localStorage.removeItem('accessToken');
    try {
      const result = await adminLogin(email, password, totpCode);
      localStorage.setItem('accessToken', result.accessToken);
      if (result.totpEnrolled) {
        navigate('/admin/continents');
        return;
      }
      setSetup(await setupAdminTotp());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (setup) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-surface px-4">
        <div className="w-full max-w-sm bg-panel border border-outline rounded-2xl p-6 text-center">
          <h1 className="text-foreground font-bold text-lg mb-1">🔐 2차 인증 등록</h1>
          <p className="text-muted text-xs mb-4">인증 앱에 아래 키를 등록한 뒤 계속하세요.</p>
          <div className="bg-elevated border border-outline rounded-lg p-3 mb-3 break-all">
            <p className="text-muted text-[10px] mb-1">시크릿 키 (수동 입력)</p>
            <p className="text-primary font-mono text-sm">{setup.secret}</p>
          </div>
          <p className="text-muted text-[10px] break-all mb-4">{setup.otpAuthUri}</p>
          <button
            onClick={() => navigate('/admin/continents')}
            className="w-full h-10 rounded-lg bg-primary text-surface font-bold text-sm hover:brightness-110">
            등록 완료, 계속
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface px-4">
      <form onSubmit={handleSubmit} className="w-full max-w-sm bg-panel border border-outline rounded-2xl p-6">
        <h1 className="text-foreground font-bold text-xl mb-1">🛡️ 관리자 로그인</h1>
        <p className="text-muted text-xs mb-5">운영자 전용 · 2차 인증 필요</p>

        {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}

        <label className="block text-dim mb-1.5 text-[11px] font-medium">이메일</label>
        <input type="email" value={email} onChange={e => setEmail(e.target.value)} required
          className="w-full bg-elevated border border-outline rounded-md px-3 h-[38px] text-foreground outline-none focus:border-primary text-sm mb-3" />

        <label className="block text-dim mb-1.5 text-[11px] font-medium">비밀번호</label>
        <input type="password" value={password} onChange={e => setPassword(e.target.value)} required
          className="w-full bg-elevated border border-outline rounded-md px-3 h-[38px] text-foreground outline-none focus:border-primary text-sm mb-3" />

        <label className="block text-dim mb-1.5 text-[11px] font-medium">인증 코드 (6자리 · 최초 로그인 시 생략)</label>
        <input inputMode="numeric" value={totpCode} onChange={e => setTotpCode(e.target.value)}
          placeholder="000000" maxLength={6}
          className="w-full bg-elevated border border-outline rounded-md px-3 h-[38px] text-foreground outline-none focus:border-primary text-sm mb-5 tracking-widest" />

        <button type="submit" disabled={isSubmitting}
          className="w-full h-11 rounded-lg bg-primary text-surface font-bold text-sm hover:brightness-110 disabled:opacity-50">
          {isSubmitting ? '확인 중...' : '로그인'}
        </button>
      </form>
    </div>
  );
}
