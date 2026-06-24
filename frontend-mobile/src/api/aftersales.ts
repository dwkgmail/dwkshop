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

export function createAftersale(orderId: number, reason: string) {
  return request<Aftersale>('/api/aftersales', {
    method: 'POST',
    body: JSON.stringify({ orderId, reason })
  });
}

export function getAftersales() {
  return request<Aftersale[]>('/api/aftersales');
}
