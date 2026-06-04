import { request } from './client';

export interface ProductSummary {
  id: number;
  categoryId: number;
  productCode: string;
  name: string;
  subtitle?: string;
  mainImageUrl: string;
  saleStatus: string;
  deliveryType: string;
  allowCart: boolean;
  allowSingleBuy: boolean;
  pointDeductEnabled: boolean;
  minSalePrice: number;
  minSalePriceText: string;
  displayedSales: number;
}

export interface ProductSku {
  id: number;
  skuCode: string;
  skuName: string;
  specJson: string;
  imageUrl?: string;
  salePrice: number;
  salePriceText: string;
  linePrice?: number;
  linePriceText?: string;
  stock: number;
  lockedStock: number;
  skuStatus: string;
  selectable: boolean;
}

export interface ProductDetail extends ProductSummary {
  productType: string;
  offSale: boolean;
  offSaleMessage?: string;
  pointRewardEnabled: boolean;
  pointReward: number;
  noticeTitle?: string;
  noticeContent?: string;
  skus: ProductSku[];
}

export interface Category {
  id: number;
  parentId?: number;
  name: string;
  level: number;
  sortOrder: number;
  status: string;
}

export function getProducts(categoryId?: number) {
  const query = categoryId ? `?categoryId=${categoryId}` : '';
  return request<ProductSummary[]>(`/api/products${query}`);
}

export function getProduct(id: number) {
  return request<ProductDetail>(`/api/products/${id}`);
}

export function getCategories() {
  return request<Category[]>('/api/categories');
}

export function searchProducts(keyword: string) {
  return request<ProductSummary[]>(`/api/search/products?keyword=${encodeURIComponent(keyword)}`);
}
