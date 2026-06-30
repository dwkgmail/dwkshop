import { expect, test, type Page, type Request, type Route } from '@playwright/test';

const product = {
  id: 101,
  categoryId: 1,
  productCode: 'E2E-APPLE',
  name: 'E2E Apple Box',
  subtitle: 'Crisp test fruit',
  mainImageUrl: '/images/e2e-apple.png',
  saleStatus: 'ON_SALE',
  deliveryType: 'NORMAL',
  allowCart: true,
  allowSingleBuy: true,
  pointDeductEnabled: true,
  minSalePrice: 12900,
  minSalePriceText: '129',
  displayedSales: 88,
  productType: 'NORMAL',
  offSale: false,
  pointRewardEnabled: true,
  pointReward: 50,
  noticeTitle: 'E2E Notice',
  noticeContent: 'Keep refrigerated after delivery.',
  skus: [
    {
      id: 1001,
      skuCode: 'E2E-APPLE-6',
      skuName: '6 pack',
      specJson: '{"size":"6 pack"}',
      salePrice: 12900,
      salePriceText: '129',
      linePrice: 15900,
      linePriceText: '159',
      stock: 20,
      lockedStock: 0,
      skuStatus: 'ENABLED',
      selectable: true
    }
  ]
};

const cart = {
  userId: 7,
  badgeCount: 1,
  estimatedAmount: 12900,
  estimatedAmountText: '129',
  checkoutAvailable: true,
  invalidItemCount: 0,
  selectedItemCount: 1,
  items: [
    {
      id: 501,
      productId: product.id,
      skuId: product.skus[0].id,
      productName: product.name,
      productImageUrl: product.mainImageUrl,
      skuName: product.skus[0].skuName,
      specJson: product.skus[0].specJson,
      salePrice: 12900,
      salePriceText: '129',
      quantity: 1,
      stock: 20,
      checked: true,
      allowCart: true,
      allowSingleBuy: true,
      pointDeductEnabled: true,
      status: 'NORMAL',
      canCheck: true,
      estimatedAmount: 12900,
      estimatedAmountText: '129'
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
  orderStatus: 'WAIT_PAY',
  payStatus: 'UNPAID',
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

async function mockBuyerApi(page: Page, seen: Request[]) {
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    if (!url.pathname.startsWith('/api/')) return route.fallback();
    seen.push(request);

    if (url.pathname === '/api/products' && method === 'GET') return json(route, [product]);
    if (url.pathname === `/api/products/${product.id}` && method === 'GET') return json(route, product);
    if (url.pathname === '/api/auth/login' && method === 'POST') {
      return json(route, {
        token: 'buyer-token',
        refreshToken: 'buyer-refresh-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
        id: 7,
        name: 'E2E Buyer',
        role: 'USER'
      });
    }
    if (url.pathname === '/api/cart/items' && method === 'POST') return json(route, cart);
    if (url.pathname === '/api/cart/items' && method === 'GET') return json(route, cart);
    if (url.pathname === '/api/orders/confirm' && method === 'POST') {
      return json(route, {
        settlementToken: 'settlement-e2e',
        sourceType: 'CART',
        address: {
          id: 301,
          receiverName: 'E2E Buyer',
          receiverMobile: '13800000001',
          province: 'Shanghai',
          city: 'Shanghai',
          district: 'Pudong',
          detailAddress: 'Test Road 1',
          defaultFlag: true
        },
        items: [
          {
            cartItemId: 501,
            productId: product.id,
            skuId: product.skus[0].id,
            productName: product.name,
            skuName: product.skus[0].skuName,
            productImageUrl: product.mainImageUrl,
            salePrice: 12900,
            salePriceText: '129',
            quantity: 1,
            totalAmount: 12900,
            totalAmountText: '129',
            couponShareAmount: 1000,
            couponShareAmountText: '10',
            pointShareAmount: 500,
            pointShareAmountText: '5',
            freightShareAmount: 0,
            freightShareAmountText: '0',
            payAmount: 11400,
            payAmountText: '114',
            promotionShares: [],
            allowSingleBuy: true,
            pointDeductEnabled: true,
            noticeTitle: product.noticeTitle,
            noticeContent: product.noticeContent
          }
        ],
        freightAmount: 0,
        freightAmountText: '0',
        selectedCoupon: {
          couponUserId: 41,
          couponId: 4,
          name: 'E2E Coupon',
          couponType: 'FULL_REDUCTION',
          thresholdAmount: 10000,
          thresholdAmountText: '100',
          discountAmount: 1000,
          discountAmountText: '10',
          selected: true
        },
        availableCoupons: [],
        pointDeduction: {
          visible: true,
          availablePoints: 500,
          deductionAmount: 500,
          deductionAmountText: '5',
          selected: true
        },
        amount
      });
    }
    if (url.pathname === '/api/orders/create' && method === 'POST') return json(route, order);
    if (url.pathname === `/api/orders/${order.id}/pay` && method === 'POST') {
      return json(route, { ...order, orderStatus: 'WAIT_SHIP', payStatus: 'PAID' });
    }

    return json(route, { message: `Unhandled ${method} ${url.pathname}` }, 500);
  });
}

test('buyer can browse, sign in, checkout, create an order, and pay', async ({ page }) => {
  const seen: Request[] = [];
  await mockBuyerApi(page, seen);

  await page.goto('/');
  await expect(page.getByText(product.name)).toBeVisible();

  await page.locator('.product-card', { hasText: product.name }).click();
  await expect(page.locator('.detail-view')).toContainText(product.name);

  await page.locator('.action-bar .ghost').click();
  await page.locator('input[autocomplete="tel"]').fill('13800000001');
  await page.locator('input[autocomplete="current-password"]').fill('user123');
  await page.locator('form.login-card .primary').click();

  await expect(page.locator('.detail-view')).toContainText(product.name);
  await page.locator('.action-bar .ghost').click();
  await expect(page.locator('.toast')).toContainText(/加入|cart/i);

  await page.locator('.tabbar button').nth(3).click();
  await expect(page.locator('.cart-view')).toContainText(product.name);
  await page.locator('.cart-bar .primary').click();

  await expect(page.locator('.confirm-stack')).toContainText(product.name);
  await page.locator('.confirm-stack .primary').click();

  await expect(page.locator('.payment-view')).toContainText(order.orderNo);
  await page.locator('.payment-view .primary').click();
  await expect(page.locator('.success-text')).toContainText('Payment success');

  const addCartRequest = seen.find((request) => request.method() === 'POST' && new URL(request.url()).pathname === '/api/cart/items');
  expect(await addCartRequest?.postDataJSON()).toEqual({ skuId: product.skus[0].id, quantity: 1 });

  const confirmRequest = seen.find((request) => request.method() === 'POST' && new URL(request.url()).pathname === '/api/orders/confirm');
  expect(await confirmRequest?.postDataJSON()).toEqual({ sourceType: 'CART', cartItemIds: [501], usePoints: true });

  const createRequest = seen.find((request) => request.method() === 'POST' && new URL(request.url()).pathname === '/api/orders/create');
  expect(await createRequest?.postDataJSON()).toMatchObject({
    settlementToken: 'settlement-e2e',
    expectedPayAmount: 11400
  });
});
