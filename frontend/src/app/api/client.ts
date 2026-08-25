export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = 'ApiError';
  }
}

const BASE = (import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/$/, '');

let isRefreshing = false;
let pendingQueue: Array<(token: string | null) => void> = [];

function drainQueue(token: string | null) {
  pendingQueue.forEach(cb => cb(token));
  pendingQueue = [];
}

async function tryRefresh(): Promise<string> {
  const res = await fetch(BASE + '/auth/refresh', { method: 'POST', credentials: 'include' });
  if (!res.ok) throw new Error('refresh failed');
  const body = await res.json();
  const token: string = body.data.accessToken;
  localStorage.setItem('accessToken', token);
  return token;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('accessToken');
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers as Record<string, string>),
  };

  const res = await fetch(BASE + path, { ...options, headers, credentials: 'include' });

  if (res.status !== 401) {
    if (!res.ok) {
      let message = res.statusText;
      try { const b = await res.json(); if (b?.message) message = b.message; } catch { /* ignore */ }
      throw new ApiError(message, res.status);
    }
    const body = await res.json();
    return body.data as T;
  }

  // 토큰 없이 받은 401은 엔드포인트 자체의 인증 실패 (잘못된 비밀번호 등) — 리프레시 불필요
  if (!token) {
    let message = 'Unauthorized';
    try { const b = await res.json(); if (b?.message) message = b.message; } catch { /* ignore */ }
    throw new ApiError(message, 401);
  }

  // 401 — attempt token refresh
  if (isRefreshing) {
    return new Promise<T>((resolve, reject) => {
      pendingQueue.push((newToken) => {
        if (!newToken) { reject(new Error('Session expired')); return; }
        const retryHeaders = { ...headers, Authorization: `Bearer ${newToken}` };
        fetch(BASE + path, { ...options, headers: retryHeaders, credentials: 'include' })
          .then(r => {
            if (!r.ok) {
              throw new ApiError(r.statusText, r.status);
            }
            return r.json();
          })
          .then(b => resolve(b.data as T))
          .catch(reject);
      });
    });
  }

  isRefreshing = true;
  let newToken: string;
  try {
    newToken = await tryRefresh();
  } catch {
    isRefreshing = false;
    drainQueue(null);
    localStorage.removeItem('accessToken');
    window.location.href = '/login';
    throw new ApiError('Session expired', 401);
  }
  isRefreshing = false;
  drainQueue(newToken);
  const retryHeaders = { ...headers, Authorization: `Bearer ${newToken}` };
  const retryRes = await fetch(BASE + path, { ...options, headers: retryHeaders, credentials: 'include' });
  if (!retryRes.ok) {
    let retryMessage = retryRes.statusText;
    try { const b = await retryRes.json(); if (b?.message) retryMessage = b.message; } catch { /* ignore */ }
    throw new ApiError(retryMessage, retryRes.status);
  }
  const body = await retryRes.json();
  return body.data as T;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, data: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(data) }),
  patch: <T>(path: string, data: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(data) }),
  delete: <T>(path: string, data?: unknown) =>
    request<T>(path, { method: 'DELETE', ...(data ? { body: JSON.stringify(data) } : {}) }),
};
