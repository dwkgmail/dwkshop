import { request } from './client';

export interface ConfirmOrderItem {
  cartItemId?: number;
  productId: number;
  skuId: number;
  productName: string;
  skuName: string;
  productImageUrl: string;
  salePrice: number;
  salePriceText: string;
  quantity: number;
  totalAmount: number;
  totalAmountText: string;
  allowSingleBuy: boolean;
  pointDeductEnabled: boolean;
  noticeTitle?: string;
  noticeContent?: string;
}

export interface OrderAddress {
  id: number;
  receiverName: string;
  receiverMobile: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  defaultFlag: boolean;
}

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

export interface ConfirmCoupon {
  couponUserId: number;
  couponId: number;
  name: string;
  couponType: string;
  thresholdAmount: number;
  thresholdAmountText: string;
  discountAmount: number;
  discountAmountText: string;
  selected: boolean;
}

export interface PointDeduction {
  visible: boolean;
  availablePoints: number;
  deductionAmount: number;
  deductionAmountText: string;
  selected: boolean;
}

export interface ConfirmOrderResponse {
  settlementToken: string;
  sourceType: string;
  address: OrderAddress;
  items: ConfirmOrderItem[];
  freightAmount: number;
  freightAmountText: string;
  selectedCoupon?: ConfirmCoupon;
  availableCoupons: ConfirmCoupon[];
  pointDeduction: PointDeduction;
  amount: OrderAmount;
  remark?: string;
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

export interface ConfirmOrderPayload {
  sourceType: 'CART' | 'BUY_NOW';
  cartItemIds?: number[];
  skuId?: number;
  quantity?: number;
  addressId?: number;
  couponUserId?: number;
  usePoints?: boolean;
  remark?: string;
}

export function confirmOrder(payload: ConfirmOrderPayload) {
  return request<ConfirmOrderResponse>('/api/orders/confirm', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function createOrder(settlementToken: string, expectedPayAmount: number, remark?: string) {
  return request<OrderDetail>('/api/orders/create', {
    method: 'POST',
    body: JSON.stringify({ settlementToken, expectedPayAmount, remark })
  });
}

export function getOrders() {
  return request<OrderSummary[]>('/api/orders');
}

export function getOrder(id: number) {
  return request<OrderDetail>(`/api/orders/${id}`);
}

export function cancelOrder(id: number) {
  return request<OrderDetail>(`/api/orders/${id}/cancel`, {
    method: 'POST'
  });
}

export function payOrder(id: number) {
  return request<OrderDetail>(`/api/orders/${id}/pay`, {
    method: 'POST'
  });
}
