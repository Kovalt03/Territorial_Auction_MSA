import { useState } from 'react';
import { useNavigate } from 'react-router';

import { useApp } from '../context/AppContext';
import { signupApi, loginApi, checkUsernameApi, checkEmailApi } from '../api/auth';
import { fetchMyProfile, fetchMyWallet } from '../api/user';
import { ApiError } from '../api/client';

import { GridBackground } from '../components/GridBackground';
import { Button } from '../components/Button';

export function RegisterPage() {
  const navigate = useNavigate();
  const { login } = useApp();

  const [form, setForm] = useState({ username: '', email: '', password: '', pwConfirm: '', nickname: '' });
  const [usernameChecked, setUsernameChecked] = useState(false);
  const [usernameAvailable, setUsernameAvailable] = useState<boolean | null>(null);
  const [emailChecked, setEmailChecked] = useState(false);
  const [emailAvailable, setEmailAvailable] = useState<boolean | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [showWelcome, setShowWelcome] = useState(false);

  const handleChange = (field: string, value: string) => {
    setForm(prev => ({ ...prev, [field]: value }));
    if (field === 'username') { setUsernameChecked(false); setUsernameAvailable(null); }
    if (field === 'email') { setEmailChecked(false); setEmailAvailable(null); }
    setError('');
  };

  const handleCheckUsername = async () => {
    if (!form.username) return;
    try {
      await checkUsernameApi(form.username);
      setUsernameAvailable(true);
    } catch {
      setUsernameAvailable(false);
    }
    setUsernameChecked(true);
  };

  const handleCheckEmail = async () => {
    if (!form.email) return;
    try {
      await checkEmailApi(form.email);
      setEmailAvailable(true);
    } catch {
      setEmailAvailable(false);
    }
    setEmailChecked(true);
  };

  const handleSubmit = async () => {
    setError('');
    if (!usernameChecked || !usernameAvailable) { setError('아이디 중복확인을 해주세요.'); return; }
    if (!emailChecked || !emailAvailable) { setError('이메일 중복확인을 해주세요.'); return; }
    if (form.password.length < 8) { setError('비밀번호는 8자 이상이어야 합니다.'); return; }
    if (form.password !== form.pwConfirm) { setError('비밀번호가 일치하지 않습니다.'); return; }
    if (!form.nickname) { setError('닉네임을 입력해주세요.'); return; }

    setIsLoading(true);
    try {
      await signupApi(form.username, form.email, form.password, form.nickname);
      const tokenData = await loginApi(form.email, form.password);
      localStorage.setItem('accessToken', tokenData.accessToken);
      const [profile, wallet] = await Promise.all([
        fetchMyProfile().catch(() => null),
        fetchMyWallet().catch(() => null),
      ]);
      login(profile?.nickname ?? '', { token: tokenData.accessToken, userId: profile?.userId, ap: wallet?.availableAP ?? 0, gp: wallet?.availableGP ?? 0 });
      setShowWelcome(true);
    } catch (e: unknown) {
      if (e instanceof ApiError && e.status === 409) setError('이미 사용 중인 아이디 또는 이메일입니다.');
      else setError('회원가입 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const pwMatch = form.password && form.pwConfirm ? form.password === form.pwConfirm : null;

  return (
    <div className="min-h-screen bg-surface relative flex items-center justify-center overflow-hidden">
      <GridBackground />

      <div className="absolute w-[428px] h-[720px] bg-primary opacity-[0.06] rounded-3xl" />

      <div className="relative bg-panel border-[1.5px] border-primary rounded-2xl w-full max-w-[400px] mx-4 overflow-hidden shadow-2xl shadow-[#00f5ff]/10">
        <div className="pt-5 pb-3 flex flex-col items-center">
          <span className="text-primary font-bold text-sm">⬡ PIXEL WAR</span>
        </div>

        <div className="px-7 pb-7">
          <h2 className="text-foreground font-bold mb-3 text-[22px]">회원가입</h2>

          <div className="bg-elevated border border-gold rounded-lg px-4 py-2.5 mb-4">
            <span className="text-gold text-xs font-medium">
              🎁  가입 완료 시 1,000 AP 즉시 지급
            </span>
          </div>

          {/* 아이디 */}
          <label className="form-label">아이디</label>
          <div className="flex gap-2 mb-1">
            <input
              value={form.username}
              onChange={e => handleChange('username', e.target.value)}
              placeholder="영문, 숫자 4~20자"
              className="flex-1 bg-elevated border border-outline rounded-md px-3 h-[38px] text-foreground outline-none focus:border-primary transition-colors text-xs"
            />
            <Button
              variant="ghost"
              size="sm"
              onClick={handleCheckUsername}
            >
              중복확인
            </Button>
          </div>
          {usernameChecked && (
            <p className={`mb-3 text-[11px] ${usernameAvailable ? 'text-gp' : 'text-danger'}`}>
              {usernameAvailable ? '✓ 사용 가능한 아이디입니다' : '✗ 이미 사용 중인 아이디입니다'}
            </p>
          )}
          {!usernameChecked && <div className="mb-3" />}

          {/* 이메일 */}
          <label className="form-label">이메일</label>
          <div className="flex gap-2 mb-1">
            <input
              type="email"
              value={form.email}
              onChange={e => handleChange('email', e.target.value)}
              placeholder="example@email.com"
              className="flex-1 bg-elevated border border-outline rounded-md px-3 h-[38px] text-foreground outline-none focus:border-primary transition-colors text-xs"
            />
            <Button
              variant="ghost"
              size="sm"
              onClick={handleCheckEmail}
            >
              중복확인
            </Button>
          </div>
          {emailChecked && (
            <p className={`mb-3 text-[11px] ${emailAvailable ? 'text-gp' : 'text-danger'}`}>
              {emailAvailable ? '✓ 사용 가능한 이메일입니다' : '✗ 이미 사용 중인 이메일입니다'}
            </p>
          )}
          {!emailChecked && <div className="mb-3" />}

          {/* 비밀번호 */}
          <label className="form-label">비밀번호</label>
          <input
            type="password"
            value={form.password}
            onChange={e => handleChange('password', e.target.value)}
            placeholder="8자 이상, 영문+숫자 조합"
            className="form-input mb-4"
          />

          {/* 비밀번호 확인 */}
          <label className="form-label">비밀번호 확인</label>
          <input
            type="password"
            value={form.pwConfirm}
            onChange={e => handleChange('pwConfirm', e.target.value)}
            placeholder="비밀번호를 다시 입력"
            className={`w-full bg-elevated border rounded-md px-3 h-[38px] text-foreground outline-none transition-colors mb-4 text-xs ${
              pwMatch === null ? 'border-outline' : pwMatch ? 'border-gp' : 'border-danger'
            }`}
          />

          {/* 닉네임 */}
          <label className="form-label">닉네임</label>
          <input
            value={form.nickname}
            onChange={e => handleChange('nickname', e.target.value)}
            placeholder="다른 유저에게 보이는 이름"
            className="form-input mb-4"
          />

          {error && (
            <p className="text-danger mb-3 text-xs">⚠ {error}</p>
          )}

          <Button
            onClick={handleSubmit}
            disabled={isLoading}
            size="lg"
            fullWidth
            className="mb-3"
          >
            {isLoading ? '가입 중...' : '가입하기'}
          </Button>

          <Button
            variant="secondary"
            onClick={() => navigate('/login')}
            size="md"
            fullWidth
          >
            ← 로그인으로 돌아가기
          </Button>
        </div>
      </div>

      {showWelcome && (
        <div className="fixed inset-0 flex items-center justify-center z-50 bg-black/60">
          <div className="bg-panel border-2 border-gold rounded-2xl p-8 text-center max-w-sm mx-4 shadow-2xl shadow-[#ffd700]/20">
            <div className="text-5xl mb-4">🎁</div>
            <h3 className="text-gold font-bold text-xl mb-2">가입을 축하합니다!</h3>
            <p className="text-dim mb-3 text-sm">웰컴 보너스가 지급되었습니다</p>
            <div className="bg-elevated rounded-xl py-4 px-6 mb-5">
              <p className="text-primary font-bold text-[28px]">+1,000 AP</p>
              <p className="text-dim text-sm mt-1">즉시 사용 가능</p>
            </div>
            <Button
              onClick={() => { setShowWelcome(false); navigate('/app/map'); }}
              size="lg"
              fullWidth
            >
              게임 시작! 🚀
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
