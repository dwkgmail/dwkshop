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

export function loginAdmin(username: string, password: string) {
  return request<LoginResponse>('/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  });
}

export function changeAdminPassword(oldPassword: string, newPassword: string) {
  return request<LoginResponse>('/admin/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword })
  });
}

export function logoutAdmin() {
  return request<void>('/admin/auth/logout', { method: 'POST' });
}
