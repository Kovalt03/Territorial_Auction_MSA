import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';

import { useApp } from '../context/AppContext';
import { useMyBids } from '../hooks/useMyBids';
import { useVault } from '../hooks/useVault';
import { logoutApi } from '../api/auth';
import { fetchSettings, updateSettings, changePassword, deleteAccount } from '../api/user';
import { GNB } from '../components/GNB';
import { Button } from '../components/Button';
import type { NotificationSettings } from '../types/user';

type Section = 'notifications' | 'security' | 'account';

export function SettingsPage() {
  const navigate = useNavigate();
  const { logout, username } = useApp();
  const { bids: myBids } = useMyBids();
  const { territories } = useVault();
  const activeBids = myBids.filter(b => b.status === 'BIDDING');
  const [activeSection, setActiveSection] = useState<Section>('notifications');
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const handleLogout = async () => {
    setIsLoggingOut(true);
    try {
      await logoutApi();
    } catch {
      // 서버 오류여도 로컬 상태는 초기화
    }
    logout();
    navigate('/login');
  };

  const [notifications, setNotifications] = useState<NotificationSettings>({
    isOutbidEnabled: true,
    isAuctionStartEnabled: true,
    isMarketingEnabled: false,
  });
  const [isNotifSaving, setIsNotifSaving] = useState(false);
  const [isNotifSaved, setIsNotifSaved] = useState(false);

  useEffect(() => {
    fetchSettings()
      .then(data => setNotifications(data))
      .catch((e) => console.warn('[SettingsPage] settings load failed', e));
  }, []);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isPwLoading, setIsPwLoading] = useState(false);
  const [pwError, setPwError] = useState('');
  const [isPwSuccess, setIsPwSuccess] = useState(false);

  const [deletePassword, setDeletePassword] = useState('');
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [isDeleteLoading, setIsDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  const handleToggle = (key: keyof NotificationSettings) => {
    setNotifications(prev => ({ ...prev, [key]: !prev[key] }));
    setIsNotifSaved(false);
  };

  const handleSaveNotifications = async () => {
    setIsNotifSaving(true);
    try {
      await updateSettings(notifications);
      setIsNotifSaved(true);
      setTimeout(() => setIsNotifSaved(false), 2500);
    } catch {
      // keep current state on error
    } finally {
      setIsNotifSaving(false);
    }
  };

  const handleChangePassword = async () => {
    setPwError('');
    setIsPwSuccess(false);
    if (!currentPassword || !newPassword || !confirmPassword) {
      setPwError('모든 항목을 입력해주세요.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPwError('새 비밀번호가 일치하지 않습니다.');
      return;
    }
    if (newPassword.length < 8) {
      setPwError('새 비밀번호는 8자 이상이어야 합니다.');
      return;
    }
    setIsPwLoading(true);
    try {
      await changePassword(currentPassword, newPassword);
      setIsPwSuccess(true);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setTimeout(() => setIsPwSuccess(false), 3000);
    } catch {
      setPwError('비밀번호 변경에 실패했습니다. 현재 비밀번호를 확인해주세요.');
    } finally {
      setIsPwLoading(false);
    }
  };

  const handleDeleteAccount = async () => {
    setDeleteError('');
    if (!deletePassword) {
      setDeleteError('비밀번호를 입력해주세요.');
      return;
    }
    if (deleteConfirmText !== '탈퇴합니다') {
      setDeleteError('"탈퇴합니다"를 정확히 입력해주세요.');
      return;
    }
    setIsDeleteLoading(true);
    try {
      await deleteAccount(deletePassword);
      logout();
      navigate('/login');
    } catch {
      setDeleteError('계정 삭제에 실패했습니다. 비밀번호를 확인해주세요.');
    } finally {
      setIsDeleteLoading(false);
    }
  };

  const sidebarItems: { id: Section; icon: string; label: string }[] = [
    { id: 'notifications', icon: '🔔', label: '알림 설정' },
    { id: 'security', icon: '🔒', label: '보안' },
    { id: 'account', icon: '👤', label: '계정 관리' },
  ];

  return (
    <div className="page-root">
      <GNB />

      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar */}
        <aside className="w-[220px] flex-shrink-0 flex flex-col py-6 px-3 gap-1 bg-surface border-r border-outline">
          <p className="text-muted font-semibold px-3 mb-3 text-[11px] tracking-[0.08em]">
            설정
          </p>
          {sidebarItems.map(item => (
            <button
              key={item.id}
              onClick={() => setActiveSection(item.id)}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors text-left w-full text-[13px] border ${
                activeSection === item.id
                  ? 'bg-primary/10 text-primary border-primary/20 font-semibold'
                  : 'text-muted border-transparent'
              }`}
            >
              <span className="text-base">{item.icon}</span>
              {item.label}
            </button>
          ))}

          <div className="flex-1" />

          <button
            onClick={handleLogout}
            disabled={isLoggingOut}
            className="flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors text-left w-full text-danger border border-transparent text-[13px] hover:bg-danger/10 disabled:opacity-50"
          >
            <span className="text-base">🚪</span>
            {isLoggingOut ? '로그아웃 중...' : '로그아웃'}
          </button>
        </aside>

        {/* Content */}
        <main className="flex-1 overflow-y-auto p-8">
          <div className="max-w-[560px]">

            {/* 프로필 카드 */}
            <div className="card p-5 mb-6">
              <div className="flex items-center gap-4 mb-4">
                <div className="w-14 h-14 rounded-2xl flex items-center justify-center font-bold text-2xl flex-shrink-0 bg-[#00f5ff20] border-2 border-primary text-primary">
                  {(username || '게스트').charAt(0).toUpperCase()}
                </div>
                <div>
                  <p className="text-foreground font-bold text-lg">{username || '게스트'}</p>
                  <p className="text-muted text-xs">플레이어</p>
                </div>
              </div>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { label: '영토', val: territories.length, color: '#00f5ff' },
                  { label: '입찰', val: myBids.length, color: '#ffd700' },
                  { label: '경매중', val: activeBids.length, color: '#ff8c00' },
                ].map(s => (
                  <div key={s.label} className="bg-elevated rounded-xl p-2 text-center">
                    <p className="font-bold text-sm" style={{ color: s.color }}>{s.val}</p>
                    <p className="text-muted text-[10px]">{s.label}</p>
                  </div>
                ))}
              </div>
            </div>

            {/* 알림 설정 */}
            {activeSection === 'notifications' && (
              <div>
                <h2 className="text-foreground font-bold mb-1 text-xl">알림 설정</h2>
                <p className="text-muted mb-6 text-[13px]">
                  수신할 알림 항목을 개별로 ON/OFF 할 수 있습니다.
                </p>

                <div className="rounded-xl overflow-hidden border border-outline">
                  {[
                    {
                      key: 'isOutbidEnabled' as keyof NotificationSettings,
                      icon: '🔺',
                      label: '상회 입찰 알림',
                      desc: '내 입찰이 다른 유저에게 넘겨졌을 때 알림을 받습니다.',
                    },
                    {
                      key: 'isAuctionStartEnabled' as keyof NotificationSettings,
                      icon: '🏁',
                      label: '경매 시작 알림',
                      desc: '관심 대륙에서 새 경매가 시작될 때 알림을 받습니다.',
                    },
                    {
                      key: 'isMarketingEnabled' as keyof NotificationSettings,
                      icon: '📢',
                      label: '마케팅·이벤트 알림',
                      desc: '이벤트, 업데이트 등 마케팅 소식을 받습니다.',
                    },
                  ].map((item, idx, arr) => (
                    <div
                      key={item.key}
                      className={`flex items-center justify-between px-5 py-4 bg-panel${idx < arr.length - 1 ? ' border-b border-outline' : ''}`}
                    >
                      <div className="flex items-start gap-3">
                        <span className="text-xl mt-px">{item.icon}</span>
                        <div>
                          <p className="text-foreground font-semibold text-sm">{item.label}</p>
                          <p className="text-muted text-xs">{item.desc}</p>
                        </div>
                      </div>
                      {/* Toggle */}
                      <button
                        onClick={() => handleToggle(item.key)}
                        className={`relative flex-shrink-0 w-11 h-6 rounded-full transition-colors duration-200 ${notifications[item.key] ? 'bg-primary' : 'bg-outline'}`}
                      >
                        <span
                          className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-all duration-200 ${notifications[item.key] ? 'left-6' : 'left-1'}`}
                        />
                      </button>
                    </div>
                  ))}
                </div>

                <Button
                  variant={isNotifSaved ? 'ghost' : 'ghost'}
                  onClick={handleSaveNotifications}
                  disabled={isNotifSaving}
                  className={`mt-5 ${isNotifSaved ? 'text-gp border-gp bg-[#00ff8820]' : ''}`}
                >
                  {isNotifSaving ? '저장 중...' : isNotifSaved ? '✓ 저장됨' : '변경사항 저장'}
                </Button>
              </div>
            )}

            {/* 보안 */}
            {activeSection === 'security' && (
              <div>
                <h2 className="text-foreground font-bold mb-1 text-xl">보안</h2>
                <p className="text-muted mb-6 text-[13px]">
                  계정 비밀번호를 변경합니다.
                </p>

                <div className="rounded-xl p-6 bg-panel border border-outline">
                  <p className="text-foreground font-semibold mb-4 text-[15px]">비밀번호 변경</p>

                  <div className="space-y-3">
                    {[
                      { label: '현재 비밀번호', value: currentPassword, setter: setCurrentPassword, placeholder: '현재 비밀번호 입력' },
                      { label: '새 비밀번호', value: newPassword, setter: setNewPassword, placeholder: '8자 이상, 영문+숫자+특수문자' },
                      { label: '새 비밀번호 확인', value: confirmPassword, setter: setConfirmPassword, placeholder: '새 비밀번호를 다시 입력' },
                    ].map(field => (
                      <div key={field.label}>
                        <label className="block text-muted mb-1.5 text-xs">{field.label}</label>
                        <input
                          type="password"
                          value={field.value}
                          onChange={e => field.setter(e.target.value)}
                          placeholder={field.placeholder}
                          className="w-full h-10 bg-surface border border-outline rounded-lg px-3 text-foreground outline-none focus:border-primary transition-colors text-[13px]"
                        />
                      </div>
                    ))}
                  </div>

                  {pwError && (
                    <p className="mt-3 text-danger text-xs">⚠ {pwError}</p>
                  )}
                  {isPwSuccess && (
                    <p className="mt-3 text-gp text-xs">✓ 비밀번호가 성공적으로 변경되었습니다.</p>
                  )}

                  <Button
                    variant="ghost"
                    onClick={handleChangePassword}
                    disabled={isPwLoading}
                    className="mt-5"
                  >
                    {isPwLoading ? '변경 중...' : '비밀번호 변경'}
                  </Button>
                </div>
              </div>
            )}

            {/* 계정 관리 */}
            {activeSection === 'account' && (
              <div>
                <h2 className="text-foreground font-bold mb-1 text-xl">계정 관리</h2>
                <p className="text-muted mb-6 text-[13px]">
                  계정을 영구적으로 삭제합니다. 이 작업은 되돌릴 수 없습니다.
                </p>

                <div className="rounded-xl p-6 bg-[#1a0a10] border border-danger/25">
                  <div className="flex items-center gap-2 mb-4">
                    <span className="text-lg">⚠</span>
                    <p className="text-danger font-bold text-[15px]">회원 탈퇴 (위험 영역)</p>
                  </div>

                  <div className="rounded-lg p-4 mb-5 bg-[#ff333315] border border-[#ff333330]">
                    <p className="text-[#ff8899] text-xs leading-relaxed">
                      탈퇴 시 다음 항목이 <strong>즉시 삭제·소멸</strong>됩니다.
                    </p>
                    <ul className="mt-2 space-y-1">
                      {[
                        '보유 중인 모든 영토 자동 반납',
                        '보유 AP·GP 전액 소멸',
                        '길드 자동 탈퇴 처리',
                        '모든 입찰 취소',
                      ].map(item => (
                        <li key={item} className="text-[#ff8899] flex items-start gap-2 text-xs">
                          <span className="flex-shrink-0 mt-0.5">·</span>
                          {item}
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div className="space-y-3">
                    <div>
                      <label className="block text-[#ff8899] mb-1.5 text-xs">
                        현재 비밀번호
                      </label>
                      <input
                        type="password"
                        value={deletePassword}
                        onChange={e => setDeletePassword(e.target.value)}
                        placeholder="본인 확인을 위해 비밀번호를 입력하세요"
                        className="w-full h-10 bg-surface border border-danger/25 rounded-lg px-3 text-foreground outline-none text-[13px]"
                      />
                    </div>
                    <div>
                      <label className="block text-[#ff8899] mb-1.5 text-xs">
                        확인 문구 입력 — <span className="text-danger font-bold">탈퇴합니다</span> 를 그대로 입력하세요
                      </label>
                      <input
                        type="text"
                        value={deleteConfirmText}
                        onChange={e => setDeleteConfirmText(e.target.value)}
                        placeholder="탈퇴합니다"
                        className="w-full h-10 bg-surface border border-danger/25 rounded-lg px-3 text-foreground outline-none text-[13px]"
                      />
                    </div>
                  </div>

                  {deleteError && (
                    <p className="mt-3 text-danger text-xs">⚠ {deleteError}</p>
                  )}

                  <Button
                    variant="danger"
                    onClick={handleDeleteAccount}
                    disabled={isDeleteLoading || deleteConfirmText !== '탈퇴합니다' || !deletePassword}
                    className="mt-5"
                  >
                    {isDeleteLoading ? '처리 중...' : '계정 영구 삭제'}
                  </Button>
                </div>
              </div>
            )}

          </div>
        </main>
      </div>
    </div>
  );
}
