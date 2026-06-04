import { request } from './client';

export interface LoginResponse {
  token: string;
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
