import { useState } from 'react';
import { useNavigate } from 'react-router';

import { useApp } from '../context/AppContext';
import { loginApi } from '../api/auth';
import { fetchMyProfile, fetchMyWallet } from '../api/user';

import { GridBackground } from '../components/GridBackground';
import { Button } from '../components/Button';

export function LoginPage() {
  const navigate = useNavigate();
  const { login } = useApp();
  const [email, setEmail] = useState('');
  const [pw, setPw] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleLogin = async () => {
    if (!email || !pw) { setError('이메일과 비밀번호를 입력해주세요.'); return; }
    setIsSubmitting(true);
    setError('');
    try {
      const { accessToken } = await loginApi(email, pw);
      localStorage.setItem('accessToken', accessToken);
      const [profile, wallet] = await Promise.all([
        fetchMyProfile().catch(() => null),
        fetchMyWallet().catch(() => null),
      ]);
      login(profile?.nickname ?? '', {
        token: accessToken,
        userId: profile?.userId,
        ap: wallet?.availableAP ?? 0,
        gp: wallet?.availableGP ?? 0,
      });
      navigate('/app/map');
    } catch {
      localStorage.removeItem('accessToken');
      setError('이메일 또는 비밀번호가 올바르지 않습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleGuest = () => {
    navigate('/app/map');
  };

  return (
    <div className="min-h-screen bg-surface relative flex items-center justify-center overflow-hidden">
      <GridBackground />

      <div className="absolute w-[500px] h-[600px] bg-primary opacity-[0.04] rounded-full blur-3xl" />

      <div className="relative bg-panel border-[1.5px] border-primary rounded-2xl w-full max-w-[380px] mx-4 overflow-hidden shadow-[0_0_40px_#00f5ff15]">
        <div className="pt-8 pb-4 flex flex-col items-center">
          <div className="w-16 h-16 bg-[#00f5ff15] border-2 border-primary rounded-2xl flex items-center justify-center mb-3">
            <span className="text-[32px]">⬡</span>
          </div>
          <h1 className="text-primary font-bold text-[22px]">픽셀 경매</h1>
          <p className="text-muted text-xs">PIXEL AUCTION · 사이버 영토 전쟁</p>
        </div>

        <div className="px-8 pb-8">
          <div className="bg-[#2a1500] border border-[#ffd70060] rounded-xl px-4 py-2.5 mb-5 flex items-center gap-2">
            <span className="text-base">🎁</span>
            <span className="text-gold text-xs">신규 가입 시 1,000 AP 즉시 지급!</span>
          </div>

          {error && (
            <div className="bg-danger/10 border border-danger rounded-lg px-3 py-2 mb-3">
              <span className="text-danger text-xs">{error}</span>
            </div>
          )}

          <label className="form-label">이메일</label>
          <input
            type="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleLogin()}
            placeholder="이메일을 입력하세요"
            className="w-full bg-elevated border border-outline rounded-lg px-4 h-11 text-foreground outline-none focus:border-primary transition-colors mb-4 text-sm"
          />

          <label className="form-label">비밀번호</label>
          <input
            type="password"
            value={pw}
            onChange={e => setPw(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleLogin()}
            placeholder="비밀번호를 입력하세요"
            className="w-full bg-elevated border border-outline rounded-lg px-4 h-11 text-foreground outline-none focus:border-primary transition-colors mb-5 text-sm"
          />

          <Button
            onClick={handleLogin}
            disabled={isSubmitting}
            size="lg"
            fullWidth
            className="mb-3"
          >
            {isSubmitting ? '로그인 중...' : '로그인'}
          </Button>

          <Button
            variant="secondary"
            onClick={() => navigate('/register')}
            size="md"
            fullWidth
            className="mb-3"
          >
            회원가입
          </Button>

          <button
            onClick={handleGuest}
            className="w-full text-center text-muted hover:text-foreground transition-colors text-xs"
          >
            게스트로 둘러보기 →
          </button>
        </div>
      </div>
    </div>
  );
}
