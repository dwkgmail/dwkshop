import { request } from './client';

export interface LoginResponse {
  token: string;
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
