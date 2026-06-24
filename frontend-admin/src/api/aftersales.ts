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

export function approveAftersale(id: number) {
  return request<Aftersale>(`/admin/aftersales/${id}/approve`, {
    method: 'POST'
  });
}

export function rejectAftersale(id: number, rejectReason: string) {
  return request<Aftersale>(`/admin/aftersales/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify({ rejectReason })
  });
}
