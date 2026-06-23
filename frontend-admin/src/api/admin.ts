import { request } from './client';

export interface AdminMember {
  id: number;
  mobile: string;
  nickname: string;
  status: string;
  availablePoints: number;
  lockedPoints: number;
  orderCount: number;
  couponCount: number;
  createdAt: string;
}

export interface AdminCoupon {
  id: number;
  couponCode: string;
  name: string;
  couponType: string;
  thresholdAmount: number;
  thresholdAmountText: string;
  discountAmount: number;
  discountAmountText: string;
  discountRate?: number;
  totalQuantity: number;
  receivedQuantity: number;
  usedQuantity: number;
  receiveStartTime: string;
  receiveEndTime: string;
  useStartTime: string;
  useEndTime: string;
  couponStatus: string;
}

export interface CouponPayload {
  name: string;
  couponCode?: string;
  couponType: string;
  thresholdAmount: number;
  discountAmount: number;
  discountRate?: number;
  totalQuantity: number;
  receiveStartTime: string;
  receiveEndTime: string;
  useStartTime: string;
  useEndTime: string;
  couponStatus: string;
}

export interface AdminRole {
  id: number;
  roleCode: string;
  roleName: string;
  permissions: string;
  status: string;
}

export interface AdminAccount {
  id: number;
  username: string;
  displayName: string;
  status: string;
  roleId?: number;
  roleName?: string;
  createdAt: string;
}

export interface OperationLog {
  id: number;
  adminUserId?: number;
  adminUsername: string;
  module: string;
  action: string;
  targetType: string;
  targetId?: number;
  detail: string;
  createdAt: string;
}

export function getAdminMembers() {
  return request<AdminMember[]>('/admin/users');
}

export function updateMemberStatus(id: number, status: string) {
  return request<AdminMember>(`/admin/users/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export function getCoupons() {
  return request<AdminCoupon[]>('/admin/coupons');
}

export function createCoupon(payload: CouponPayload) {
  return request<AdminCoupon>('/admin/coupons', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function updateCouponStatus(id: number, status: string) {
  return request<AdminCoupon>(`/admin/coupons/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export function getRoles() {
  return request<AdminRole[]>('/admin/roles');
}

export function getAdminAccounts() {
  return request<AdminAccount[]>('/admin/admin-users');
}

export function assignAdminRole(id: number, roleId: number) {
  return request<AdminAccount>(`/admin/admin-users/${id}/role`, {
    method: 'PATCH',
    body: JSON.stringify({ roleId })
  });
}

export function updateAdminAccountStatus(id: number, status: string) {
  return request<AdminAccount>(`/admin/admin-users/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status })
  });
}

export function getOperationLogs() {
  return request<OperationLog[]>('/admin/operation-logs');
}
