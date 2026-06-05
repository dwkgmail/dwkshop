import { request } from './client';

export interface OrderAmount {
  productAmount: number;
  productAmountText: string;
  productDiscountAmount: number;
  productDiscountAmountText: string;
  couponDiscountAmount: number;
  couponDiscountAmountText: string;
  pointDiscountAmount: number;
  pointDiscountAmountText: string;
  freightAmount: number;
  freightAmountText: string;
  freightDiscountAmount: number;
  freightDiscountAmountText: string;
  payAmount: number;
  payAmountText: string;
}

export interface OrderSummary {
  id: number;
  orderNo: string;
  userId: number;
  orderStatus: string;
  payStatus: string;
  deliveryStatus: string;
  aftersaleStatus: string;
  payAmount: number;
  payAmountText: string;
  createdAt: string;
}

export interface OrderDetail extends OrderSummary {
  userId: number;
  receiverName: string;
  receiverMobile: string;
  receiverAddress: string;
  remark?: string;
  logisticsCompany?: string;
  logisticsNo?: string;
  deliveryRemark?: string;
  payExpireTime: string;
  payTime?: string;
  deliveryTime?: string;
  finishTime?: string;
  amount: OrderAmount;
  items: Array<{
    id: number;
    productId: number;
    skuId: number;
    productName: string;
    skuName: string;
    productImageUrl: string;
    salePrice: number;
    salePriceText: string;
    quantity: number;
    payAmount: number;
    payAmountText: string;
  }>;
}

export function getOrders() {
  return request<OrderSummary[]>('/admin/orders');
}

export function getOrder(id: number) {
  return request<OrderDetail>(`/admin/orders/${id}`);
}

export function shipOrder(id: number, payload: { logisticsCompany: string; logisticsNo: string; deliveryRemark?: string }) {
  return request<OrderDetail>(`/admin/orders/${id}/ship`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function updateDeliveryStatus(id: number, payload: { deliveryStatus: string; deliveryRemark?: string }) {
  return request<OrderDetail>(`/admin/orders/${id}/delivery-status`, {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}
