const API_BASE = '';
const TOKEN_KEY = 'dwkshop-user-token';
const REFRESH_TOKEN_KEY = 'dwkshop-user-refresh-token';
export const AUTH_EXPIRED_EVENT = 'dwkshop:user-auth-expired';

export function getAuthToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setAuthToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function setAuthTokens(token: string, refreshToken: string) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearAuthToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

async function refreshAuthToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;

  const response = await fetch(`${API_BASE}/api/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  });

  if (!response.ok) return false;
  const body = (await response.json()) as { token: string; refreshToken: string; name?: string };
  setAuthTokens(body.token, body.refreshToken);
  if (body.name) localStorage.setItem('dwkshop-user-name', body.name);
  return true;
}

function notifyAuthExpired() {
  clearAuthToken();
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
}

async function parseError(response: Response) {
  let message = `请求失败 (${response.status})`;
  if (response.status >= 500) return message;
  try {
    const body = await response.json();
    message = body.message || body.error || message;
  } catch {
    // Keep the generic message when the backend does not return JSON.
  }
  return message;
}

function shouldRefresh(url: string, response: Response, retried: boolean) {
  return response.status === 401 && !retried && !url.startsWith('/api/auth/');
}

export async function request<T>(url: string, options: RequestInit = {}, retried = false): Promise<T> {
  const token = getAuthToken();
  const response = await fetch(`${API_BASE}${url}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {})
    }
  });

  if (shouldRefresh(url, response, retried) && (await refreshAuthToken())) {
    return request<T>(url, options, true);
  }

  if (!response.ok) {
    if (response.status === 401) notifyAuthExpired();
    throw new Error(await parseError(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}
