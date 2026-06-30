import { expect, test, type Page, type Request, type Route } from '@playwright/test';

const product = {
  id: 101,
  categoryId: 1,
  productCode: 'E2E-APPLE',
  name: 'E2E Apple Box',
  subtitle: 'Admin visible product',
  mainImageUrl: '/images/e2e-apple.png',
  productType: 'NORMAL',
  saleStatus: 'ON_SALE',
  deliveryType: 'NORMAL',
  allowCart: true,
  allowSingleBuy: true,
  pointDeductEnabled: true,
  pointRewardEnabled: true,
  pointReward: 50,
  virtualSales: 8,
  actualSales: 12,
  minSalePrice: 12900,
  minSalePriceText: '129',
  stock: 20,
  offSale: false,
  skus: [
    {
      id: 1001,
      skuCode: 'E2E-APPLE-6',
      skuName: '6 pack',
      specJson: '{"size":"6 pack"}',
      salePrice: 12900,
      salePriceText: '129',
      stock: 20,
      lockedStock: 1,
      skuStatus: 'ENABLED',
      selectable: true
    }
  ]
};

const amount = {
  productAmount: 12900,
  productAmountText: '129',
  productDiscountAmount: 0,
  productDiscountAmountText: '0',
  couponDiscountAmount: 1000,
  couponDiscountAmountText: '10',
  pointDiscountAmount: 500,
  pointDiscountAmountText: '5',
  freightAmount: 0,
  freightAmountText: '0',
  freightDiscountAmount: 0,
  freightDiscountAmountText: '0',
  payAmount: 11400,
  payAmountText: '114',
  promotionTraces: [],
  promotionTraceJson: '[]'
};

const order = {
  id: 9001,
  orderNo: 'E2E-ORDER-9001',
  userId: 7,
  orderStatus: 'WAIT_SHIP',
  payStatus: 'PAID',
  deliveryStatus: 'UNSHIPPED',
  aftersaleStatus: 'NONE',
  payAmount: 11400,
  payAmountText: '114',
  createdAt: '2026-06-30T09:00:00',
  receiverName: 'E2E Buyer',
  receiverMobile: '13800000001',
  receiverAddress: 'Shanghai Test Road 1',
  payExpireTime: '2026-06-30T09:30:00',
  amount,
  items: [
    {
      id: 7001,
      productId: product.id,
      skuId: product.skus[0].id,
      productName: product.name,
      skuName: product.skus[0].skuName,
      productImageUrl: product.mainImageUrl,
      salePrice: 12900,
      salePriceText: '129',
      quantity: 1,
      payAmount: 11400,
      payAmountText: '114',
      couponShareAmount: 1000,
      couponShareAmountText: '10',
      pointShareAmount: 500,
      pointShareAmountText: '5',
      freightShareAmount: 0,
      freightShareAmountText: '0',
      promotionShares: [],
      refundableQuantity: 1,
      refundedQuantity: 0,
      aftersaleQuantity: 0,
      refundAmount: 11400,
      refundAmountText: '114',
      refundStatus: 'NONE'
    }
  ]
};

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body)
  });
}

function shippedOrder() {
  return {
    ...order,
    deliveryStatus: 'SHIPPED',
    logisticsCompany: 'SF Express',
    logisticsNo: 'SF1234567890',
    deliveryRemark: 'Handle with care',
    deliveryTime: '2026-06-30T10:00:00'
  };
}

async function mockAdminApi(page: Page, seen: Request[], options: { couponStatus?: number } = {}) {
  const handler = async (route: Route) => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    if (!url.pathname.startsWith('/api/') && !url.pathname.startsWith('/admin/')) return route.fallback();
    seen.push(request);

    if (url.pathname === '/admin/auth/login' && method === 'POST') {
      return json(route, {
        token: 'admin-token',
        refreshToken: 'admin-refresh-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
        id: 1,
        name: 'Admin E2E',
        role: 'ADMIN'
      });
    }
    if (url.pathname === '/admin/products' && method === 'GET') return json(route, [product]);
    if (url.pathname === '/api/categories' && method === 'GET') {
      return json(route, [{ id: 1, name: 'Fruit', level: 1, sortOrder: 1, status: 'ENABLED' }]);
    }
    if (url.pathname === `/api/products/${product.id}` && method === 'GET') return json(route, product);
    if (url.pathname === '/admin/orders' && method === 'GET') return json(route, [order]);
    if (url.pathname === `/admin/orders/${order.id}` && method === 'GET') return json(route, order);
    if (url.pathname === `/admin/orders/${order.id}/ship` && method === 'POST') return json(route, shippedOrder());
    if (url.pathname === '/admin/aftersales' && method === 'GET') return json(route, []);
    if (url.pathname === '/admin/users' && method === 'GET') {
      return json(route, [
        {
          id: 7,
          mobile: '13800000001',
          nickname: 'E2E Buyer',
          status: 'ACTIVE',
          availablePoints: 500,
          lockedPoints: 0,
          orderCount: 1,
          couponCount: 1,
          createdAt: '2026-06-30T08:00:00'
        }
      ]);
    }
    if (url.pathname === '/admin/coupons' && method === 'GET') {
      if (options.couponStatus) return json(route, { message: 'not found' }, options.couponStatus);
      return json(route, [
        {
          id: 4,
          couponCode: 'E2E-10',
          name: 'E2E Coupon',
          couponType: 'FULL_REDUCTION',
          thresholdAmount: 10000,
          thresholdAmountText: '100',
          discountAmount: 1000,
          discountAmountText: '10',
          totalQuantity: 100,
          receivedQuantity: 1,
          usedQuantity: 0,
          receiveStartTime: '2026-06-01T00:00:00',
          receiveEndTime: '2026-07-01T00:00:00',
          useStartTime: '2026-06-01T00:00:00',
          useEndTime: '2026-07-01T00:00:00',
          couponStatus: 'ENABLED'
        }
      ]);
    }

    return json(route, { message: `Unhandled ${method} ${url.pathname}` }, 500);
  };

  await page.route('**/api/**', handler);
  await page.route('**/admin/**', handler);
}

async function login(page: Page) {
  await page.goto('/');
  await page.locator('.login-page input').first().fill('admin');
  await page.locator('.login-page input[type="password"]').fill('admin123');
  await page.locator('.login-page .primary').click();
  await expect(page.locator('.admin-layout')).toBeVisible();
}

test('admin can inspect products, inspect an order, and ship it', async ({ page }) => {
  const seen: Request[] = [];
  await mockAdminApi(page, seen);

  await login(page);

  await page.locator('.sidebar nav button').nth(2).click();
  await expect(page.locator('tbody')).toContainText(product.name);

  await page.locator('.sidebar nav button').nth(3).click();
  await expect(page.locator('tbody')).toContainText(order.orderNo);
  await page.locator('tr', { hasText: order.orderNo }).locator('.actions button').click();

  await expect(page.locator('.detail-grid')).toContainText(order.receiverMobile);
  await page.locator('.detail-grid .full-row').nth(1).locator('input').nth(0).fill('SF Express');
  await page.locator('.detail-grid .full-row').nth(1).locator('input').nth(1).fill('SF1234567890');
  await page.locator('.detail-grid .full-row').nth(1).locator('textarea').fill('Handle with care');
  await page.locator('.detail-grid .full-row').nth(1).locator('.primary').click();

  await expect(page.locator('.detail-grid')).toContainText('SF Express');
  await expect(page.locator('.detail-grid')).toContainText('SF1234567890');

  const shipRequest = seen.find((request) => request.method() === 'POST' && new URL(request.url()).pathname === `/admin/orders/${order.id}/ship`);
  expect(await shipRequest?.postDataJSON()).toEqual({
    logisticsCompany: 'SF Express',
    logisticsNo: 'SF1234567890',
    deliveryRemark: 'Handle with care'
  });
});

test('dashboard degrades when a secondary module returns 404 without showing a global error', async ({ page }) => {
  const seen: Request[] = [];
  await mockAdminApi(page, seen, { couponStatus: 404 });

  await login(page);

  await expect(page.locator('.metrics')).toContainText('-');
  await page.locator('.quick-actions button').nth(2).click();

  await expect(page.locator('.page')).toContainText('该模块尚未接入微服务，暂不可用');
  await expect(page.locator('.error-box')).toBeHidden();
  expect(seen.some((request) => new URL(request.url()).pathname === '/admin/coupons')).toBe(true);
});
