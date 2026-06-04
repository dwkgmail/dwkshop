import { request } from './client';

export interface CartItem {
  id: number;
  productId: number;
  skuId: number;
  productName: string;
  productImageUrl: string;
  skuName: string;
  specJson: string;
  salePrice: number;
  salePriceText: string;
  quantity: number;
  stock: number;
  checked: boolean;
  allowCart: boolean;
  allowSingleBuy: boolean;
  pointDeductEnabled: boolean;
  status: string;
  statusMessage?: string;
  canCheck: boolean;
  estimatedAmount: number;
  estimatedAmountText: string;
}

export interface CartResponse {
  userId: number;
  badgeCount: number;
  estimatedAmount: number;
  estimatedAmountText: string;
  items: CartItem[];
}

export function getCart() {
  return request<CartResponse>('/api/cart/items');
}

export function addCartItem(skuId: number, quantity: number) {
  return request<CartResponse>('/api/cart/items', {
    method: 'POST',
    body: JSON.stringify({ skuId, quantity })
  });
}

export function updateCartItem(id: number, quantity: number) {
  return request<CartResponse>(`/api/cart/items/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ quantity })
  });
}

export function deleteCartItem(id: number) {
  return request<CartResponse>(`/api/cart/items/${id}`, {
    method: 'DELETE'
  });
}

export function updateCartChecked(id: number, checked: boolean) {
  return request<CartResponse>(`/api/cart/items/${id}/checked`, {
    method: 'PUT',
    body: JSON.stringify({ checked })
  });
}

export function checkAllCartItems(checked: boolean) {
  return request<CartResponse>('/api/cart/items/check-all', {
    method: 'PUT',
    body: JSON.stringify({ checked })
  });
}
