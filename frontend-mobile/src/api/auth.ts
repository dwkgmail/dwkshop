import { request } from './client';

export interface LoginResponse {
  token: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  id: number;
  name: string;
  role: string;
}

export function loginUser(mobile: string, password: string) {
  return request<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ mobile, password })
  });
}

export function registerUser(mobile: string, password: string, nickname: string) {
  return request<LoginResponse>('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ mobile, password, nickname })
  });
}

export function changeUserPassword(oldPassword: string, newPassword: string) {
  return request<LoginResponse>('/api/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword })
  });
}

export function logoutUser() {
  return request<void>('/api/auth/logout', { method: 'POST' });
}
