import { request } from './client';

export interface Aftersale {
  id: number;
  aftersaleNo: string;
  orderId: number;
  orderNo: string;
  userId: number;
  receiverMobile: string;
  aftersaleType: string;
  aftersaleStatus: string;
  refundAmount: number;
  refundAmountText: string;
  reason: string;
  rejectReason?: string;
  applyTime: string;
  auditTime?: string;
  refundTime?: string;
}

export function createAftersale(orderId: number, reason: string) {
  return request<Aftersale>('/api/aftersales', {
    method: 'POST',
    body: JSON.stringify({ orderId, reason })
  });
}

export function getAftersales() {
  return request<Aftersale[]>('/api/aftersales');
}
