import { apiClient } from './client';
import type { MyWalletResponse, MyProfileResponse, ChargeApResponse, NotificationSettings } from '../types/user';

export function fetchMyWallet() {
  return apiClient.get<MyWalletResponse>('/users/me/wallet');
}

export function fetchMyProfile() {
  return apiClient.get<MyProfileResponse>('/users/me');
}

export function chargeAp(amount: number, paymentKey: string, orderId: string) {
  return apiClient.post<ChargeApResponse>('/users/me/ap/charge', { amount, paymentKey, orderId });
}

export function fetchSettings() {
  return apiClient.get<NotificationSettings>('/users/me/settings');
}

export function updateSettings(settings: Partial<NotificationSettings>) {
  return apiClient.patch<null>('/users/me/settings', settings);
}

export function changePassword(currentPassword: string, newPassword: string) {
  return apiClient.patch<null>('/users/me/password', { currentPassword, newPassword });
}

export function deleteAccount(password: string) {
  return apiClient.delete<null>('/users/me', { password });
}

export function fetchWishlist() {
  return apiClient.get<{ territoryIds: number[] }>('/users/me/wishlist');
}

export function addToWishlist(territoryId: number) {
  return apiClient.post<null>(`/users/me/wishlist/${territoryId}`, {});
}

export function removeFromWishlist(territoryId: number) {
  return apiClient.delete<null>(`/users/me/wishlist/${territoryId}`);
}
