import { createContext, useContext, useState, useEffect, ReactNode } from 'react';

import { ApiError } from '../api/client';

import { fetchMyProfile, fetchMyWallet } from '../api/user';
import { fetchMySeasonPass } from '../api/season';
import { fetchNotificationList } from '../api/notification';
import { disconnectStomp } from '../hooks/useStompClient';

export interface Territory {
  id: string;
  x: number;
  y: number;
  name: string;
  status: 'mine' | 'occupied' | 'auction' | 'idle';
  owner: string | null;
  color: string;
  grade: 'S' | 'A' | 'B' | 'C';
  currentBid: number;
  myBid?: number;
  gpPerMin: number;
  defense: number;
  isWishlisted: boolean;
  bidHistory: { user: string; amount: number; time: string }[];
  protection?: boolean;
}

export interface ChatMessage {
  id: string;
  user: string;
  message: string;
  time: string;
}

interface LoginOptions {
  token?: string;
  userId?: number;
  ap?: number;
  gp?: number;
}

interface AppState {
  ap: number;
  gp: number;
  hasPass: boolean;
  passEndDate: Date | null;
  notifications: number;
  territories: Territory[];
  messages: ChatMessage[];
  isLoggedIn: boolean;
  isAuthLoading: boolean;
  username: string;
  userId: number | null;
}

interface AppContextType extends AppState {
  login: (name: string, opts?: LoginOptions) => void;
  logout: () => void;
  addAP: (amount: number) => void;
  syncAP: (amount: number) => void;
  syncGP: (amount: number) => void;
  syncPass: (hasPass: boolean, expiresAt: string | null) => void;
  spendAP: (amount: number) => boolean;
  spendGP: (amount: number) => boolean;
  toggleWishlist: (id: string) => void;
  placeBid: (id: string, amount: number) => void;
  sendMessage: (text: string) => void;
  activatePass: () => void;
  decrementNotification: () => void;
  incrementNotification: () => void;
  resetNotifications: () => void;
}

const AppContext = createContext<AppContextType | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AppState>({
    ap: 0,
    gp: 0,
    hasPass: false,
    passEndDate: null,
    notifications: 0,
    territories: [],
    messages: [],
    isLoggedIn: false,
    isAuthLoading: !!localStorage.getItem('accessToken'),
    username: '',
    userId: null,
  });

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;
    Promise.all([fetchMyProfile(), fetchMyWallet(), fetchMySeasonPass()])
      .then(([profile, wallet, pass]) => {
        setState(prev => ({
          ...prev,
          isLoggedIn: true,
          isAuthLoading: false,
          username: profile.nickname,
          userId: profile.userId,
          ap: wallet.availableAP,
          gp: wallet.availableGP,
          hasPass: pass.hasSeasonPass,
          passEndDate: pass.seasonPass?.expiresAt ? new Date(pass.seasonPass.expiresAt) : null,
        }));
        // 알림 카운트는 별도 fetch — 실패해도 로그인 상태에 영향 없음
        fetchNotificationList(0, 1)
          .then(notifs => setState(prev => ({ ...prev, notifications: notifs.unreadCount })))
          .catch((e) => console.warn('[AppContext] notification count fetch failed', e));
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) {
          // 인증 실패 — 토큰 무효화, 로그아웃
          localStorage.removeItem('accessToken');
          setState(prev => ({ ...prev, isAuthLoading: false }));
        } else {
          // 서버 오류(5xx 등) — 토큰 유지, 로그인 상태 유지하되 프로필은 빈 값
          setState(prev => ({ ...prev, isLoggedIn: true, isAuthLoading: false }));
        }
      });
  }, []);

  const login = (name: string, opts?: LoginOptions) => {
    if (opts?.token) localStorage.setItem('accessToken', opts.token);
    setState(prev => ({
      ...prev,
      isLoggedIn: true,
      username: name,
      userId: opts?.userId ?? null,
      ap: opts?.ap ?? 0,
      gp: opts?.gp ?? prev.gp,
    }));
  };

  const logout = () => {
    localStorage.removeItem('accessToken');
    disconnectStomp();
    setState(prev => ({ ...prev, isLoggedIn: false, username: '', userId: null, notifications: 0 }));
  };

  const decrementNotification = () => {
    setState(prev => ({ ...prev, notifications: Math.max(0, prev.notifications - 1) }));
  };

  const incrementNotification = () => {
    setState(prev => ({ ...prev, notifications: prev.notifications + 1 }));
  };

  const resetNotifications = () => {
    setState(prev => ({ ...prev, notifications: 0 }));
  };

  const addAP = (amount: number) => {
    setState(prev => ({ ...prev, ap: prev.ap + amount }));
  };

  const syncAP = (amount: number) => {
    setState(prev => ({ ...prev, ap: amount }));
  };

  const syncGP = (amount: number) => {
    setState(prev => ({ ...prev, gp: amount }));
  };

  const syncPass = (hasPass: boolean, expiresAt: string | null) => {
    setState(prev => ({
      ...prev,
      hasPass,
      passEndDate: expiresAt ? new Date(expiresAt) : null,
    }));
  };

  const spendAP = (amount: number): boolean => {
    if (state.ap < amount) return false;
    setState(prev => ({ ...prev, ap: prev.ap - amount }));
    return true;
  };

  const spendGP = (amount: number): boolean => {
    if (state.gp < amount) return false;
    setState(prev => ({ ...prev, gp: prev.gp - amount }));
    return true;
  };

  const toggleWishlist = (id: string) => {
    setState(prev => ({
      ...prev,
      territories: prev.territories.map(t =>
        t.id === id ? { ...t, isWishlisted: !t.isWishlisted } : t
      ),
    }));
  };

  const placeBid = (id: string, amount: number) => {
    const time = new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
    setState(prev => ({
      ...prev,
      territories: prev.territories.map(t =>
        t.id === id
          ? {
              ...t,
              currentBid: amount,
              myBid: amount,
              bidHistory: [
                { user: prev.username || '나', amount, time },
                ...t.bidHistory,
              ],
            }
          : t
      ),
    }));
  };

  const sendMessage = (text: string) => {
    const id = Date.now().toString();
    const time = new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
    setState(prev => ({
      ...prev,
      messages: [...prev.messages, { id, user: prev.username || '나', message: text, time }],
    }));
  };

  const activatePass = () => {
    setState(prev => {
      const newEnd = prev.passEndDate
        ? new Date(prev.passEndDate.getTime() + 30 * 24 * 60 * 60 * 1000)
        : new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
      return { ...prev, ap: prev.ap - 1000, hasPass: true, passEndDate: newEnd };
    });
  };

  return (
    <AppContext.Provider value={{ ...state, login, logout, addAP, syncAP, syncGP, syncPass, spendAP, spendGP, toggleWishlist, placeBid, sendMessage, activatePass, decrementNotification, incrementNotification, resetNotifications }}>
      {children}
    </AppContext.Provider>
  );
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used within AppProvider');
  return ctx;
}
