import { request } from './client';

export interface Aftersale {
  id: number;
  aftersaleNo: string;
  orderId: number;
  orderNo: string;
  userId: number;
  receiverMobile: string;
  aftersaleType: string;
  refundScope: string;
  aftersaleStatus: string;
  refundItems: Array<{
    skuId: number;
    productId: number;
    quantity: number;
    refundAmount: number;
    refundAmountText: string;
  }>;
  refundAmount: number;
  refundAmountText: string;
  includeFreight: boolean;
  reason: string;
  refundReasonType?: string;
  evidenceImages: string[];
  returnLogisticsCompany?: string;
  returnLogisticsNo?: string;
  rejectReason?: string;
  applyTime: string;
  auditTime?: string;
  refundTime?: string;
}

export function getAftersales() {
  return request<Aftersale[]>('/admin/aftersales');
}

function confirmationHeaders(reason: string) {
  return {
    'X-Admin-Confirm': 'true',
    'X-Admin-Reason': reason
  };
}

export function approveAftersale(id: number, reason: string) {
  return request<Aftersale>(`/admin/aftersales/${id}/approve`, {
    method: 'POST',
    headers: confirmationHeaders(reason)
  });
}

export function confirmAftersaleReturned(id: number) {
  return request<Aftersale>(`/admin/aftersales/${id}/return`, {
    method: 'POST'
  });
}

export function completeAftersaleRefund(id: number, reason: string) {
  return request<Aftersale>(`/admin/aftersales/${id}/refund/complete`, {
    method: 'POST',
    headers: confirmationHeaders(reason)
  });
}

export function failAftersaleRefund(id: number, failureReason: string) {
  return request<Aftersale>(`/admin/aftersales/${id}/refund/fail`, {
    method: 'POST',
    headers: confirmationHeaders(failureReason),
    body: JSON.stringify({ failureReason })
  });
}

export function retryAftersaleRefund(id: number, reason: string) {
  return request<Aftersale>(`/admin/aftersales/${id}/refund/retry`, {
    method: 'POST',
    headers: confirmationHeaders(reason)
  });
}

export function closeAftersale(id: number) {
  return request<Aftersale>(`/admin/aftersales/${id}/close`, {
    method: 'POST'
  });
}

export function rejectAftersale(id: number, rejectReason: string) {
  return request<Aftersale>(`/admin/aftersales/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify({ rejectReason })
  });
}
