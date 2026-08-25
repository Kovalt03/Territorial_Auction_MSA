import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { useApp } from '../context/AppContext';
import {
  fetchNotificationList,
  markNotificationRead,
  markAllNotificationsRead,
  type NotificationItem,
} from '../api/notification';
import { LoadingState } from './LoadingState';

const TYPE_ICON: Record<string, string> = {
  OUTBID: '⚡', AUCTION_WIN: '🏆', AUCTION_LOSE: '❌', SIEGE_ALERT: '⚔️',
  SIEGE_RESULT: '🛡️', TAX_CHARGED: '💰', INCOME: '💎',
  SEASON_PASS_EXPIRING: '⏳', TAX_FAIL_WARNING: '⚠️', TAX_EVICTION: '🚨', ISLAND_EXPANDED: '🏝️',
  ADMIN_NOTICE: '📢',
};
const TYPE_COLOR: Record<string, string> = {
  OUTBID: '#ff8c00', AUCTION_WIN: '#ffd700', AUCTION_LOSE: '#ff3333', SIEGE_ALERT: '#ff3333',
  SIEGE_RESULT: '#8b50ff', TAX_CHARGED: '#ff8c00', INCOME: '#00ff88',
  SEASON_PASS_EXPIRING: '#ffd700', TAX_FAIL_WARNING: '#ff8c00', TAX_EVICTION: '#ff3333', ISLAND_EXPANDED: '#00f5ff',
  ADMIN_NOTICE: '#00f5ff',
};
const PREVIEW_SIZE = 8;

export function NotificationBell() {
  const navigate = useNavigate();
  const { notifications: unreadCount, decrementNotification, resetNotifications } = useApp();
  const [isOpen, setIsOpen] = useState(false);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    setIsLoading(true);
    fetchNotificationList(0, PREVIEW_SIZE)
      .then(res => setItems(res.notifications))
      .catch(e => console.warn('[NotificationBell] load failed', e))
      .finally(() => setIsLoading(false));
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setIsOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [isOpen]);

  const handleItemClick = async (n: NotificationItem) => {
    if (n.isRead) return;
    try {
      await markNotificationRead(n.notificationId);
      setItems(prev => prev.map(x => (x.notificationId === n.notificationId ? { ...x, isRead: true } : x)));
      decrementNotification();
    } catch (e) {
      console.warn('[NotificationBell] mark read failed', e);
    }
  };

  const handleMarkAll = async () => {
    try {
      await markAllNotificationsRead();
      setItems(prev => prev.map(x => ({ ...x, isRead: true })));
      resetNotifications();
    } catch (e) {
      console.warn('[NotificationBell] mark all failed', e);
    }
  };

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setIsOpen(o => !o)}
        className={`relative flex flex-col items-center justify-center gap-0.5 px-2 py-1 rounded-lg hover:bg-elevated transition-colors min-w-[52px] h-14 ${isOpen ? 'bg-elevated' : ''}`}
        title="알림"
      >
        <span className="text-lg">🔔</span>
        <span className={`text-[10px] leading-none ${isOpen ? 'font-semibold text-primary' : 'font-normal text-muted'}`}>
          알림
        </span>
        {unreadCount > 0 && (
          <div className="absolute top-1 right-1 min-w-4 h-4 px-1 bg-ap rounded-full flex items-center justify-center">
            <span className="text-white font-bold text-[9px]">{unreadCount > 9 ? '9+' : unreadCount}</span>
          </div>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full mt-1 w-80 bg-panel border border-outline rounded-xl shadow-xl overflow-hidden z-50">
          <div className="flex items-center justify-between px-4 py-2.5 border-b border-outline">
            <span className="text-foreground font-semibold text-[13px]">알림</span>
            {unreadCount > 0 && (
              <button onClick={handleMarkAll} className="text-primary text-[11px] hover:underline">
                전체 읽음
              </button>
            )}
          </div>

          <div className="max-h-[360px] overflow-y-auto">
            {isLoading ? (
              <LoadingState className="py-8" />
            ) : items.length === 0 ? (
              <p className="text-muted text-center text-xs py-8">알림이 없습니다.</p>
            ) : (
              items.map(n => (
                <button
                  key={n.notificationId}
                  onClick={() => handleItemClick(n)}
                  className={`w-full text-left flex items-start gap-2.5 px-4 py-2.5 border-b border-outline-soft hover:bg-elevated transition-colors ${n.isRead ? 'opacity-60' : ''}`}
                >
                  <div
                    className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 text-sm"
                    style={{ background: `${TYPE_COLOR[n.type] ?? '#354064'}20` }}
                  >
                    {TYPE_ICON[n.type] ?? '🔔'}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-foreground text-[12px] leading-snug">{n.message}</p>
                    <p className="text-muted text-[10px] mt-0.5">
                      {new Date(n.createdAt).toLocaleString('ko-KR')}
                    </p>
                  </div>
                  {!n.isRead && (
                    <span
                      className="w-1.5 h-1.5 rounded-full flex-shrink-0 mt-1.5"
                      style={{ background: TYPE_COLOR[n.type] ?? '#00f5ff' }}
                    />
                  )}
                </button>
              ))
            )}
          </div>

          <button
            onClick={() => {
              setIsOpen(false);
              navigate('/app/notifications');
            }}
            className="w-full py-2.5 text-center text-muted text-[12px] hover:text-foreground hover:bg-elevated transition-colors border-t border-outline"
          >
            전체 보기
          </button>
        </div>
      )}
    </div>
  );
}
