import { apiClient } from './client';

export function loginApi(email: string, password: string) {
  return apiClient.post<{ accessToken: string }>('/auth/login', { email, password });
}

export function signupApi(username: string, email: string, password: string, nickname: string) {
  return apiClient.post<{ userId: number; username: string; nickname: string }>(
    '/auth/signup', { username, email, password, nickname }
  );
}

export function checkUsernameApi(username: string) {
  return apiClient.get<void>(`/auth/check-username?username=${encodeURIComponent(username)}`);
}

export function checkEmailApi(email: string) {
  return apiClient.get<void>(`/auth/check-email?email=${encodeURIComponent(email)}`);
}

export function logoutApi() {
  return apiClient.post<void>('/auth/logout', {});
}
