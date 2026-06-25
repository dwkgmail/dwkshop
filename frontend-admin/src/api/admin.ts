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

export interface InventoryReconciliationOrder {
  orderId: number;
  orderNo?: string;
  quantity: number;
  state: string;
  updatedAt: string;
}

export interface InventoryReconciliationEvent {
  eventId: string;
  orderId: number;
  eventType: string;
  consumedAt: string;
}

export interface InventoryRepairRecord {
  id: number;
  skuId: number;
  beforeLockedStock: number;
  projectedLockedStock: number;
  difference: number;
  repairType: string;
  repairStatus: string;
  operator: string;
  reason?: string;
  createdAt: string;
}

export interface InventoryReconciliationItem {
  skuId: number;
  skuCode: string;
  skuName: string;
  productId: number;
  productName: string;
  currentStock: number;
  projectedLockedStock: number;
  actualLockedStock: number;
  difference: number;
  autoRepairAllowed: boolean;
  relatedOrders: InventoryReconciliationOrder[];
  recentEvents: InventoryReconciliationEvent[];
  repairRecords: InventoryRepairRecord[];
}

export interface InventoryHealthCheck {
  checkType: string;
  status: string;
  count: number;
  message: string;
}

export interface InventoryReconciliationReport {
  checkedAt: string;
  items: InventoryReconciliationItem[];
  checks: InventoryHealthCheck[];
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

export function getInventoryReconciliation(onlyDiff = false) {
  return request<InventoryReconciliationReport>(`/admin/inventory-reconciliation?onlyDiff=${onlyDiff}`);
}

export function repairInventoryLockedStock(skuId: number, reason: string) {
  return request<InventoryRepairRecord>(`/admin/inventory-reconciliation/skus/${skuId}/repair`, {
    method: 'POST',
    body: JSON.stringify({ operator: 'admin', reason })
  });
}
