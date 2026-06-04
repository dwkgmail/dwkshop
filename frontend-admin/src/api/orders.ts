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
  orderStatus: string;
  payStatus: string;
  deliveryStatus: string;
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
  payExpireTime: string;
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
  return request<OrderSummary[]>('/api/orders');
}

export function getOrder(id: number) {
  return request<OrderDetail>(`/api/orders/${id}`);
}
