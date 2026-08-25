import { useState, useEffect } from 'react';

import { useApp } from '../context/AppContext';
import {
  fetchNotificationList, markNotificationRead, markAllNotificationsRead,
  type NotificationItem,
} from '../api/notification';

import { GNB } from '../components/GNB';
import { Button } from '../components/Button';
import { EmptyState } from '../components/EmptyState';
import { LoadingState } from '../components/LoadingState';

const TYPE_ICON: Record<string, string> = {
  OUTBID: '⚡',
  AUCTION_WIN: '🏆',
  AUCTION_LOSE: '❌',
  SIEGE_ALERT: '⚔️',
  SIEGE_RESULT: '🛡️',
  TAX_CHARGED: '💰',
  INCOME: '💎',
  SEASON_PASS_EXPIRING: '⏳',
  TAX_FAIL_WARNING: '⚠️',
  TAX_EVICTION: '🚨',
  ISLAND_EXPANDED: '🏝️',
  ADMIN_NOTICE: '📢',
};

const TYPE_COLOR: Record<string, string> = {
  OUTBID: '#ff8c00',
  AUCTION_WIN: '#ffd700',
  AUCTION_LOSE: '#ff3333',
  SIEGE_ALERT: '#ff3333',
  SIEGE_RESULT: '#8b50ff',
  TAX_CHARGED: '#ff8c00',
  INCOME: '#00ff88',
  SEASON_PASS_EXPIRING: '#ffd700',
  TAX_FAIL_WARNING: '#ff8c00',
  TAX_EVICTION: '#ff3333',
  ISLAND_EXPANDED: '#00f5ff',
  ADMIN_NOTICE: '#00f5ff',
};

const PAGE_SIZE = 20;

export function NotificationPage() {
  const { decrementNotification, resetNotifications } = useApp();

  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [isMarkingAll, setIsMarkingAll] = useState(false);

  useEffect(() => {
    setIsLoading(true);
    fetchNotificationList(0, PAGE_SIZE)
      .then(res => {
        setNotifications(res.notifications);
        setHasMore(res.notifications.length === PAGE_SIZE);
      })
      .catch((e) => console.warn('[NotificationPage] list load failed', e))
      .finally(() => setIsLoading(false));
  }, []);

  const loadMore = async () => {
    const nextPage = page + 1;
    setIsLoadingMore(true);
    try {
      const res = await fetchNotificationList(nextPage, PAGE_SIZE);
      setNotifications(prev => [...prev, ...res.notifications]);
      setHasMore(res.notifications.length === PAGE_SIZE);
      setPage(nextPage);
    } finally {
      setIsLoadingMore(false);
    }
  };

  const handleMarkRead = async (notif: NotificationItem) => {
    if (notif.isRead) return;
    try {
      await markNotificationRead(notif.notificationId);
      setNotifications(prev => prev.map(n => n.notificationId === notif.notificationId ? { ...n, isRead: true } : n));
      decrementNotification();
    } catch {
      // silently ignore — badge count stays consistent
    }
  };

  const handleMarkAll = async () => {
    setIsMarkingAll(true);
    try {
      await markAllNotificationsRead();
      setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
      resetNotifications();
    } finally {
      setIsMarkingAll(false);
    }
  };

  const unreadCount = notifications.filter(n => !n.isRead).length;

  return (
    <div className="flex flex-col h-screen bg-surface">
      <GNB />
      <div className="flex-1 overflow-y-auto p-6">
        <div className="max-w-2xl mx-auto">

          <div className="flex items-center justify-between mb-5">
            <div>
              <h1 className="text-foreground font-bold text-[22px]">알림</h1>
              {unreadCount > 0 && (
                <p className="text-muted text-[13px]">읽지 않은 알림 {unreadCount}개</p>
              )}
            </div>
            {unreadCount > 0 && (
              <Button
                variant="secondary"
                size="sm"
                onClick={handleMarkAll}
                disabled={isMarkingAll}
              >
                {isMarkingAll ? '처리 중...' : '전체 읽음'}
              </Button>
            )}
          </div>

          {isLoading ? (
            <LoadingState className="py-20" />
          ) : notifications.length === 0 ? (
            <EmptyState message="알림이 없습니다." className="py-20" />
          ) : (
            <div className="flex flex-col gap-2">
              {notifications.map(n => (
                <div
                  key={n.notificationId}
                  onClick={() => handleMarkRead(n)}
                  className={`bg-panel border rounded-xl px-4 py-3 flex items-start gap-3 cursor-pointer transition-all ${n.isRead ? 'opacity-70' : ''}`}
                  style={{ borderColor: n.isRead ? '#354064' : TYPE_COLOR[n.type] ?? '#354064' }}
                >
                  <div
                    className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0 text-lg"
                    style={{ background: `${TYPE_COLOR[n.type] ?? '#354064'}20` }}
                  >
                    {TYPE_ICON[n.type] ?? '🔔'}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-foreground text-sm">{n.message}</p>
                    <p className="text-muted mt-0.5 text-[11px]">
                      {new Date(n.createdAt).toLocaleString('ko-KR')}
                    </p>
                  </div>
                  {!n.isRead && (
                    <div className="w-2 h-2 rounded-full flex-shrink-0 mt-1" style={{ background: TYPE_COLOR[n.type] ?? '#00f5ff' }} />
                  )}
                </div>
              ))}

              {hasMore && (
                <button
                  onClick={loadMore}
                  disabled={isLoadingMore}
                  className="w-full py-3 mt-2 rounded-xl border border-outline text-muted hover:border-muted hover:text-foreground disabled:opacity-50 transition-colors text-[13px]"
                >
                  {isLoadingMore ? '불러오는 중...' : '더 보기'}
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
