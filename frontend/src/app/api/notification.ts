import { apiClient } from './client';

export interface NotificationItem {
  notificationId: number;
  type:
    | 'OUTBID'
    | 'AUCTION_WIN'
    | 'AUCTION_LOSE'
    | 'SIEGE_ALERT'
    | 'SIEGE_RESULT'
    | 'TAX_CHARGED'
    | 'INCOME'
    | 'SEASON_PASS_EXPIRING'
    | 'TAX_FAIL_WARNING'
    | 'TAX_EVICTION'
    | 'ISLAND_EXPANDED'
    | 'ADMIN_NOTICE';
  message: string;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationListResponse {
  unreadCount: number;
  notifications: NotificationItem[];
}

export function fetchNotificationList(page = 0, size = 20) {
  return apiClient.get<NotificationListResponse>(`/notifications?page=${page}&size=${size}`);
}

export function markNotificationRead(notificationId: number) {
  return apiClient.patch<null>(`/notifications/${notificationId}/read`, {});
}

export function markAllNotificationsRead() {
  return apiClient.patch<null>('/notifications/read-all', {});
}
