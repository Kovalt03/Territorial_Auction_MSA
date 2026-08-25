export function getTokenRole(): 'USER' | 'ADMIN' | null {
  const token = localStorage.getItem('accessToken');
  if (!token) return null;
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload)).role ?? null;
  } catch {
    return null;
  }
}
