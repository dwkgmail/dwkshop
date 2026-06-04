import { request } from './client';

export interface Category {
  id: number;
  parentId?: number;
  name: string;
  level: number;
  sortOrder: number;
  status: string;
}

export interface AdminProduct {
  id: number;
  categoryId: number;
  productCode: string;
  name: string;
  subtitle?: string;
  mainImageUrl: string;
  productType: string;
  saleStatus: string;
  deliveryType: string;
  allowCart: boolean;
  allowSingleBuy: boolean;
  pointDeductEnabled: boolean;
  pointRewardEnabled: boolean;
  pointReward: number;
  virtualSales: number;
  actualSales: number;
  minSalePrice: number;
  minSalePriceText: string;
  stock: number;
}

export interface ProductDetail extends AdminProduct {
  offSale: boolean;
  offSaleMessage?: string;
  noticeTitle?: string;
  noticeContent?: string;
  skus: ProductSku[];
}

export interface ProductSku {
  id?: number;
  skuCode?: string;
  skuName: string;
  specJson: string;
  imageUrl?: string;
  salePrice: number;
  salePriceText?: string;
  linePrice?: number;
  linePriceText?: string;
  stock: number;
  lockedStock?: number;
  skuStatus?: string;
  selectable?: boolean;
}

export interface ProductPayload {
  categoryId: number;
  productCode?: string;
  name: string;
  subtitle?: string;
  mainImageUrl: string;
  productType?: string;
  saleStatus?: string;
  deliveryType?: string;
  allowCart: boolean;
  allowSingleBuy: boolean;
  pointDeductEnabled: boolean;
  pointRewardEnabled: boolean;
  pointReward?: number;
  virtualSales?: number;
  noticeTitle?: string;
  noticeContent?: string;
  skus: ProductSku[];
}

export function getCategories() {
  return request<Category[]>('/api/categories');
}

export function getAdminProducts() {
  return request<AdminProduct[]>('/admin/products');
}

export function getProduct(id: number) {
  return request<ProductDetail>(`/api/products/${id}`);
}

export function createProduct(payload: ProductPayload) {
  return request<ProductDetail>('/admin/products', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function updateProduct(id: number, payload: ProductPayload) {
  return request<ProductDetail>(`/admin/products/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

export function onSaleProduct(id: number) {
  return request<ProductDetail>(`/admin/products/${id}/on-sale`, { method: 'POST' });
}

export function offSaleProduct(id: number) {
  return request<ProductDetail>(`/admin/products/${id}/off-sale`, { method: 'POST' });
}
