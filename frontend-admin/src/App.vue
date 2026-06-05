<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { changeAdminPassword, loginAdmin, logoutAdmin } from './api/auth';
import { AUTH_EXPIRED_EVENT, clearAuthToken, getAuthToken, setAuthTokens } from './api/client';
import { approveAftersale, getAftersales, rejectAftersale, type Aftersale } from './api/aftersales';
import {
  createProduct,
  getAdminProducts,
  getCategories,
  getProduct,
  offSaleProduct,
  onSaleProduct,
  updateProduct,
  type AdminProduct,
  type Category,
  type ProductDetail,
  type ProductPayload,
  type ProductSku
} from './api/products';
import { getOrder, getOrders, type OrderDetail, type OrderSummary } from './api/orders';

type Page =
  | 'dashboard'
  | 'products'
  | 'product-create'
  | 'product-edit'
  | 'orders'
  | 'aftersales'
  | 'order-detail'
  | 'placeholder';

const loggedIn = ref(Boolean(getAuthToken()));
const loginForm = reactive({ username: 'admin', password: 'admin123' });
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
const showPasswordPanel = ref(false);
const page = ref<Page>('dashboard');
const placeholderTitle = ref('');
const loading = ref(false);
const error = ref('');
const toast = ref('');

const products = ref<AdminProduct[]>([]);
const categories = ref<Category[]>([]);
const orders = ref<OrderSummary[]>([]);
const aftersales = ref<Aftersale[]>([]);
const currentOrder = ref<OrderDetail | null>(null);
const orderDetailsById = ref<Record<number, OrderDetail>>({});
const editingId = ref<number | null>(null);

const productFilters = reactive({ name: '', categoryId: '', saleStatus: '' });
const orderFilters = reactive({ orderNo: '', mobile: '', orderStatus: '' });

const productForm = reactive<ProductPayload>({
  categoryId: 1,
  productCode: '',
  name: '',
  subtitle: '',
  mainImageUrl: '/images/products/admin-product.png',
  productType: 'NORMAL',
  saleStatus: 'OFF_SALE',
  deliveryType: 'NORMAL',
  allowCart: true,
  allowSingleBuy: true,
  pointDeductEnabled: false,
  pointRewardEnabled: false,
  pointReward: 0,
  virtualSales: 0,
  noticeTitle: '鐢ㄦ埛璐拱椤荤煡',
  noticeContent: '',
  skus: [
    {
      skuName: '榛樿瑙勬牸',
      specJson: '{"瑙勬牸":"榛樿"}',
      salePrice: 9900,
      linePrice: 12900,
      stock: 100,
      skuStatus: 'ENABLED'
    }
  ]
});

const menu = [
  { key: 'dashboard', label: '棣栭〉' },
  { key: 'products', label: '鍟嗗搧绠＄悊' },
  { key: 'orders', label: '璁㈠崟绠＄悊' },
  { key: 'users', label: '鐢ㄦ埛绠＄悊' },
  { key: 'marketing', label: '钀ラ攢绠＄悊' },
  { key: 'aftersale', label: '鍞悗绠＄悊' },
  { key: 'finance', label: '璐㈠姟绠＄悊' },
  { key: 'permission', label: '鏉冮檺绠＄悊' }
];

const filteredProducts = computed(() => {
  return products.value.filter((item) => {
    const matchName = !productFilters.name || item.name.includes(productFilters.name);
    const matchCategory = !productFilters.categoryId || item.categoryId === Number(productFilters.categoryId);
    const matchStatus = !productFilters.saleStatus || item.saleStatus === productFilters.saleStatus;
    return matchName && matchCategory && matchStatus;
  });
});

const filteredOrders = computed(() => {
  return orders.value.filter((item) => {
    const matchNo = !orderFilters.orderNo || item.orderNo.includes(orderFilters.orderNo);
    const matchStatus = !orderFilters.orderStatus || item.orderStatus === orderFilters.orderStatus;
    const mobile = orderDetailsById.value[item.id]?.receiverMobile ?? '';
    const matchMobile = !orderFilters.mobile || mobile.includes(orderFilters.mobile);
    return matchNo && matchStatus && matchMobile;
  });
});

const dashboard = computed(() => {
  const orderCount = orders.value.length;
  const payAmount = orders.value.reduce((sum, item) => sum + item.payAmount, 0);
  const waitShip = orders.value.filter((item) => item.orderStatus === 'WAIT_SHIP').length;
  const refundApplying = aftersales.value.filter((item) => item.aftersaleStatus === 'APPLYING').length;
  const lowStock = products.value.filter((item) => item.stock <= 10).length;
  return {
    orderCount,
    payAmountText: formatCents(payAmount),
    waitShip,
    refundApplying,
    lowStock
  };
});

function formatCents(cents: number) {
  return (cents / 100).toFixed(2).replace(/\.?0+$/, '');
}

function showToast(message: string) {
  toast.value = message;
  window.setTimeout(() => {
    if (toast.value === message) toast.value = '';
  }, 1800);
}

async function runTask(task: () => Promise<void>) {
  loading.value = true;
  error.value = '';
  try {
    await task();
  } catch (err) {
    error.value = err instanceof Error ? err.message : '鎿嶄綔澶辫触锛岃绋嶅悗閲嶈瘯';
  } finally {
    loading.value = false;
  }
}

async function login() {
  await runTask(async () => {
    const result = await loginAdmin(loginForm.username, loginForm.password);
    setAuthTokens(result.token, result.refreshToken);
    loggedIn.value = true;
    showToast(`娆㈣繋鍥炴潵锛?{result.name}`);
    await loadDashboard();
  });
}

async function changePassword() {
  const oldPassword = passwordForm.oldPassword.trim();
  const newPassword = passwordForm.newPassword.trim();
  if (!oldPassword || !newPassword) {
    showToast('Please enter old and new password');
    return;
  }
  if (newPassword !== passwordForm.confirmPassword.trim()) {
    showToast('New passwords do not match');
    return;
  }
  await runTask(async () => {
    const result = await changeAdminPassword(oldPassword, newPassword);
    setAuthTokens(result.token, result.refreshToken);
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' });
    showPasswordPanel.value = false;
    showToast('Password updated');
  });
}

function resetSession(message?: string) {
  loggedIn.value = false;
  clearAuthToken();
  products.value = [];
  orders.value = [];
  aftersales.value = [];
  categories.value = [];
  currentOrder.value = null;
  orderDetailsById.value = {};
  editingId.value = null;
  showPasswordPanel.value = false;
  Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' });
  page.value = 'dashboard';
  if (message) showToast(message);
}

async function logout() {
  try {
    if (getAuthToken()) await logoutAdmin();
  } catch {
    // Client-side logout still clears all local session state.
  }
  resetSession('Logged out');
}

function handleAuthExpired() {
  resetSession('Login expired, please sign in again');
}

function nav(key: string) {
  if (key === 'dashboard') {
    page.value = 'dashboard';
    loadDashboard();
  } else if (key === 'products') {
    page.value = 'products';
    loadProducts();
  } else if (key === 'orders') {
    page.value = 'orders';
    loadOrders();
  } else if (key === 'aftersale') {
    page.value = 'aftersales';
    loadAftersales();
  } else {
    placeholderTitle.value = menu.find((item) => item.key === key)?.label ?? '妯″潡';
    page.value = 'placeholder';
  }
}

async function loadDashboard() {
  await runTask(async () => {
    await Promise.all([loadProductsQuietly(), loadOrdersQuietly(), loadAftersalesQuietly(), loadCategoriesQuietly()]);
  });
}

async function loadProducts() {
  await runTask(async () => {
    await Promise.all([loadProductsQuietly(), loadCategoriesQuietly()]);
  });
}

async function loadOrders() {
  await runTask(async () => {
    await loadOrdersQuietly();
  });
}

async function loadAftersales() {
  await runTask(async () => {
    await loadAftersalesQuietly();
  });
}

async function loadCategoriesQuietly() {
  categories.value = await getCategories();
  if (!productForm.categoryId && categories.value.length > 0) {
    productForm.categoryId = categories.value[0].id;
  }
}

async function loadProductsQuietly() {
  products.value = await getAdminProducts();
}

async function loadOrdersQuietly() {
  orders.value = await getOrders();
  const details = await Promise.all(orders.value.map((order) => getOrder(order.id).catch(() => null)));
  orderDetailsById.value = details.reduce<Record<number, OrderDetail>>((acc, detail) => {
    if (detail) acc[detail.id] = detail;
    return acc;
  }, {});
}

async function loadAftersalesQuietly() {
  aftersales.value = await getAftersales();
}

function resetProductForm() {
  editingId.value = null;
  Object.assign(productForm, {
    categoryId: categories.value[0]?.id ?? 1,
    productCode: '',
    name: '',
    subtitle: '',
    mainImageUrl: '/images/products/admin-product.png',
    productType: 'NORMAL',
    saleStatus: 'OFF_SALE',
    deliveryType: 'NORMAL',
    allowCart: true,
    allowSingleBuy: true,
    pointDeductEnabled: false,
    pointRewardEnabled: false,
    pointReward: 0,
    virtualSales: 0,
    noticeTitle: '鐢ㄦ埛璐拱椤荤煡',
    noticeContent: '',
    skus: [
      {
        skuName: '榛樿瑙勬牸',
        specJson: '{"瑙勬牸":"榛樿"}',
        salePrice: 9900,
        linePrice: 12900,
        stock: 100,
        skuStatus: 'ENABLED'
      }
    ]
  });
}

function openCreate() {
  resetProductForm();
  page.value = 'product-create';
}

async function openEdit(id: number) {
  page.value = 'product-edit';
  editingId.value = id;
  await runTask(async () => {
    await loadCategoriesQuietly();
    const detail = await getProduct(id);
    fillProductForm(detail);
  });
}

function fillProductForm(detail: ProductDetail) {
  Object.assign(productForm, {
    categoryId: detail.categoryId,
    productCode: detail.productCode,
    name: detail.name,
    subtitle: detail.subtitle ?? '',
    mainImageUrl: detail.mainImageUrl,
    productType: detail.productType,
    saleStatus: detail.saleStatus,
    deliveryType: detail.deliveryType,
    allowCart: detail.allowCart,
    allowSingleBuy: detail.allowSingleBuy,
    pointDeductEnabled: detail.pointDeductEnabled,
    pointRewardEnabled: detail.pointRewardEnabled,
    pointReward: detail.pointReward,
    virtualSales: detail.virtualSales ?? 0,
    noticeTitle: detail.noticeTitle ?? '鐢ㄦ埛璐拱椤荤煡',
    noticeContent: detail.noticeContent ?? '',
    skus: detail.skus.map((sku) => ({
      skuCode: sku.skuCode,
      skuName: sku.skuName,
      specJson: sku.specJson,
      imageUrl: sku.imageUrl,
      salePrice: sku.salePrice,
      linePrice: sku.linePrice,
      stock: sku.stock,
      skuStatus: sku.skuStatus
    }))
  });
}

function addSku() {
  productForm.skus.push({
    skuName: `瑙勬牸${productForm.skus.length + 1}`,
    specJson: '{"瑙勬牸":"鏂板"}',
    salePrice: 9900,
    linePrice: 12900,
    stock: 100,
    skuStatus: 'ENABLED'
  });
}

function removeSku(index: number) {
  if (productForm.skus.length === 1) {
    showToast('鑷冲皯淇濈暀涓€涓?SKU');
    return;
  }
  productForm.skus.splice(index, 1);
}

function payload(): ProductPayload {
  return {
    ...productForm,
    productCode: productForm.productCode || undefined,
    pointReward: Number(productForm.pointReward ?? 0),
    virtualSales: Number(productForm.virtualSales ?? 0),
    skus: productForm.skus.map((sku: ProductSku) => ({
      ...sku,
      salePrice: Number(sku.salePrice),
      linePrice: sku.linePrice == null ? undefined : Number(sku.linePrice),
      stock: Number(sku.stock),
      skuStatus: sku.skuStatus || 'ENABLED'
    }))
  };
}

async function saveProduct() {
  if (!productForm.name.trim()) {
    showToast('Please enter product name');
    return;
  }
  await runTask(async () => {
    if (page.value === 'product-edit' && editingId.value) {
      await updateProduct(editingId.value, payload());
      showToast('Product saved');
    } else {
      await createProduct(payload());
      showToast('Product created');
    }
    page.value = 'products';
    await loadProductsQuietly();
  });
}

async function changeSaleStatus(id: number, status: 'ON_SALE' | 'OFF_SALE') {
  await runTask(async () => {
    if (status === 'ON_SALE') {
      await onSaleProduct(id);
      showToast('Product on sale');
    } else {
      await offSaleProduct(id);
      showToast('Product off sale');
    }
    await loadProductsQuietly();
  });
}

async function openOrderDetail(id: number) {
  page.value = 'order-detail';
  await runTask(async () => {
    currentOrder.value = await getOrder(id);
  });
}

async function approveRefund(id: number) {
  await runTask(async () => {
    await approveAftersale(id);
    await Promise.all([loadAftersalesQuietly(), loadOrdersQuietly()]);
    showToast('Refund approved');
  });
}

async function rejectRefund(id: number) {
  const rejectReason = window.prompt('Reject reason', 'Refund request rejected')?.trim() || 'Refund request rejected';
  await runTask(async () => {
    await rejectAftersale(id, rejectReason);
    await Promise.all([loadAftersalesQuietly(), loadOrdersQuietly()]);
    showToast('Refund rejected');
  });
}

function statusText(status: string) {
  const map: Record<string, string> = {
    ON_SALE: 'On sale',
    OFF_SALE: 'Off sale',
    WAIT_PAY: 'Pending payment',
    CANCELED: 'Canceled',
    WAIT_SHIP: 'Waiting shipment',
    WAIT_RECEIVE: 'Waiting receipt',
    FINISHED: 'Finished',
    UNPAID: 'Unpaid',
    PAID: 'Paid',
    UNSHIPPED: 'Unshipped',
    NONE: 'None',
    APPLYING: 'Refund applying',
    REJECTED: 'Refund rejected',
    REFUNDED: 'Refunded'
  };
  return map[status] ?? status;
}

onMounted(() => {
  window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
  if (loggedIn.value) loadDashboard();
});

onUnmounted(() => {
  window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
});
</script>

<template>
  <main v-if="!loggedIn" class="login-page">
    <section class="login-card">
      <div class="login-brand">
        <span>DWK Shop</span>
        <h1>鐢靛晢绠＄悊鍚庡彴</h1>
        <p>鍟嗗搧銆佽鍗曚笌杩愯惀鏁版嵁绠＄悊</p>
      </div>
      <form @submit.prevent="login">
        <label>
          璐﹀彿
          <input v-model="loginForm.username" />
        </label>
        <label>
          瀵嗙爜
          <input v-model="loginForm.password" type="password" />
        </label>
        <button class="primary wide" type="submit">鐧诲綍</button>
      </form>
    </section>
  </main>

  <main v-else class="admin-layout">
    <div v-if="toast" class="toast">{{ toast }}</div>
    <aside class="sidebar">
      <div class="brand">DWK Shop 鍚庡彴</div>
      <nav>
        <button v-for="item in menu" :key="item.key" :class="{ active: page === item.key || (item.key === 'products' && page.startsWith('product')) || (item.key === 'orders' && page.startsWith('order')) || (item.key === 'aftersale' && page === 'aftersales') }" @click="nav(item.key)">
          {{ item.label }}
        </button>
      </nav>
    </aside>

    <section class="content">
      <header class="topbar">
        <div>
          <h1>
            {{
              page === 'dashboard' ? '鍚庡彴棣栭〉' :
              page === 'products' ? '鍟嗗搧鍒楄〃' :
              page === 'product-create' ? '鏂板鍟嗗搧' :
              page === 'product-edit' ? '缂栬緫鍟嗗搧' :
              page === 'orders' ? '璁㈠崟鍒楄〃' :
              page === 'aftersales' ? 'After-sale' :
              page === 'order-detail' ? '璁㈠崟璇︽儏' : placeholderTitle
            }}
          </h1>
          <span>楂樻晥绠＄悊鍟嗗搧銆佽鍗曚笌杩愯惀鏁版嵁</span>
        </div>
        <div class="topbar-actions">
          <button class="ghost" @click="showPasswordPanel = !showPasswordPanel">Change password</button>
          <button class="ghost" @click="logout">Logout</button>
        </div>
      </header>

      <form v-if="showPasswordPanel" class="panel password-panel" @submit.prevent="changePassword">
        <label>Old password<input v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" /></label>
        <label>New password<input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" /></label>
        <label>Confirm password<input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" /></label>
        <button class="primary" type="submit">Save password</button>
      </form>

      <section v-if="page === 'dashboard'" class="page">
        <section class="metrics">
          <article><span>璁㈠崟鎬绘暟</span><strong>{{ dashboard.orderCount }}</strong></article>
          <article><span>鏀粯閲戦</span><strong>楼{{ dashboard.payAmountText }}</strong></article>
          <article><span>Waiting shipment</span><strong>{{ dashboard.waitShip }}</strong></article>
          <article><span>Refund applying</span><strong>{{ dashboard.refundApplying }}</strong></article>
        </section>
        <section class="dashboard-grid">
          <div class="panel">
            <h2>閿€鍞秼鍔</h2>
            <div class="chart-line">
              <i v-for="height in [38, 54, 42, 76, 62, 88, 104]" :key="height" :style="{ height: `${height}px` }"></i>
            </div>
          </div>
          <div class="panel">
            <h2>蹇嵎鍏ュ彛</h2>
            <div class="quick-actions">
              <button @click="openCreate">鏂板鍟嗗搧</button>
              <button @click="nav('orders')">鏌ョ湅璁㈠崟</button>
              <button @click="nav('aftersale')">After-sale</button>
              <button @click="nav('products')">鍟嗗搧绠＄悊</button>
            </div>
          </div>
        </section>
      </section>

      <section v-else-if="page === 'products'" class="page">
        <section class="panel filters">
          <input v-model="productFilters.name" placeholder="鍟嗗搧鍚嶇О" />
          <select v-model="productFilters.categoryId">
            <option value="">鍏ㄩ儴鍒嗙被</option>
            <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
          </select>
          <select v-model="productFilters.saleStatus">
            <option value="">鍏ㄩ儴鐘舵€</option>
            <option value="ON_SALE">涓婃灦</option>
            <option value="OFF_SALE">涓嬫灦</option>
          </select>
          <button class="primary" @click="openCreate">鏂板鍟嗗搧</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead>
              <tr>
                <th>鍟嗗搧</th>
                <th>鍒嗙被</th>
                <th>浠锋牸</th>
                <th>搴撳瓨</th>
                <th>閿€閲</th>
                <th>鐘舵€</th>
                <th>鎿嶄綔</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="product in filteredProducts" :key="product.id">
                <td>
                  <div class="product-cell">
                    <div class="thumb">{{ product.name.slice(0, 1) }}</div>
                    <div><strong>{{ product.name }}</strong><span>{{ product.productCode }}</span></div>
                  </div>
                </td>
                <td>{{ categories.find((item) => item.id === product.categoryId)?.name ?? '-' }}</td>
                <td>楼{{ product.minSalePriceText }}</td>
                <td>{{ product.stock }}</td>
                <td>{{ product.actualSales + product.virtualSales }}</td>
                <td><em :class="['status', product.saleStatus]">{{ statusText(product.saleStatus) }}</em></td>
                <td class="actions">
                  <button @click="openEdit(product.id)">缂栬緫</button>
                  <button v-if="product.saleStatus !== 'ON_SALE'" @click="changeSaleStatus(product.id, 'ON_SALE')">涓婃灦</button>
                  <button v-else @click="changeSaleStatus(product.id, 'OFF_SALE')">涓嬫灦</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredProducts.length === 0" class="empty">鏆傛棤鍟嗗搧</div>
        </section>
      </section>

      <section v-else-if="page === 'product-create' || page === 'product-edit'" class="page form-page">
        <section class="panel form-grid">
          <label>鍟嗗搧鍚嶇О<input v-model="productForm.name" /></label>
          <label>鍒嗙被<select v-model.number="productForm.categoryId"><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select></label>
          <label>涓诲浘<input v-model="productForm.mainImageUrl" /></label>
          <label>Sale status<select v-model="productForm.saleStatus"><option value="OFF_SALE">Off sale</option><option value="ON_SALE">On sale</option></select></label>
          <label>Subtitle<input v-model="productForm.subtitle" /></label>
          <label>Delivery<select v-model="productForm.deliveryType"><option value="NORMAL">Normal</option><option value="COLD_CHAIN">Cold chain</option></select></label>
        </section>
        <section class="panel toggles">
          <label><input v-model="productForm.allowCart" type="checkbox" /> 鍏佽鍔犺喘</label>
          <label><input v-model="productForm.allowSingleBuy" type="checkbox" /> 鍏佽鍗曠嫭璐拱</label>
          <label><input v-model="productForm.pointDeductEnabled" type="checkbox" /> 鏀寔绉垎鎶垫墸</label>
          <label><input v-model="productForm.pointRewardEnabled" type="checkbox" /> 杩旂Н鍒</label>
          <label>Reward points<input v-model.number="productForm.pointReward" type="number" min="0" /></label>
        </section>
        <section class="panel">
          <div class="section-heading">
            <h2>SKU</h2>
            <button class="ghost" @click="addSku">鏂板 SKU</button>
          </div>
          <div v-for="(sku, index) in productForm.skus" :key="index" class="sku-editor">
            <label>SKU 鍚嶇О<input v-model="sku.skuName" /></label>
            <label>瑙勬牸 JSON<input v-model="sku.specJson" /></label>
            <label>Price cents<input v-model.number="sku.salePrice" type="number" min="0" /></label>
            <label>搴撳瓨<input v-model.number="sku.stock" type="number" min="0" /></label>
            <label>Status<select v-model="sku.skuStatus"><option value="ENABLED">Enabled</option><option value="DISABLED">Disabled</option></select></label>
            <button class="danger" @click="removeSku(index)">鍒犻櫎</button>
          </div>
        </section>
        <section class="panel form-grid">
          <label>璐拱椤荤煡鏍囬<input v-model="productForm.noticeTitle" /></label>
          <label class="full">璐拱椤荤煡鍐呭<textarea v-model="productForm.noticeContent"></textarea></label>
        </section>
        <div class="form-actions">
          <button class="ghost" @click="page = 'products'">鍙栨秷</button>
          <button class="primary" @click="saveProduct">淇濆瓨</button>
        </div>
      </section>

      <section v-else-if="page === 'orders'" class="page">
        <section class="panel filters">
          <input v-model="orderFilters.orderNo" placeholder="璁㈠崟缂栧彿" />
          <input v-model="orderFilters.mobile" placeholder="Mobile" />
          <select v-model="orderFilters.orderStatus">
            <option value="">鍏ㄩ儴鐘舵€</option>
            <option value="WAIT_PAY">寰呮敮浠</option>
            <option value="CANCELED">宸插彇娑</option>
            <option value="WAIT_SHIP">寰呭彂璐</option>
          </select>
          <button class="primary" @click="loadOrders">鏌ヨ</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead>
              <tr>
                <th>璁㈠崟缂栧彿</th>
                <th>鎵嬫満鍙</th>
                <th>涓嬪崟鏃堕棿</th>
                <th>閲戦</th>
                <th>璁㈠崟鐘舵€</th>
                <th>鏀粯鐘舵€</th>
                <th>鎿嶄綔</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in filteredOrders" :key="order.id">
                <td>{{ order.orderNo }}</td>
                <td>{{ orderDetailsById[order.id]?.receiverMobile ?? '-' }}</td>
                <td>{{ order.createdAt?.replace('T', ' ').slice(0, 19) }}</td>
                <td>楼{{ order.payAmountText }}</td>
                <td><em class="status">{{ statusText(order.orderStatus) }}</em></td>
                <td>{{ statusText(order.payStatus) }}</td>
                <td class="actions"><button @click="openOrderDetail(order.id)">璇︽儏</button></td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredOrders.length === 0" class="empty">鏆傛棤璁㈠崟</div>
        </section>
      </section>

      <section v-else-if="page === 'aftersales'" class="page">
        <section class="panel table-panel">
          <table>
            <thead>
              <tr>
                <th>After-sale No</th>
                <th>Order No</th>
                <th>Mobile</th>
                <th>Amount</th>
                <th>Reason</th>
                <th>Status</th>
                <th>Apply time</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in aftersales" :key="item.id">
                <td>{{ item.aftersaleNo }}</td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.receiverMobile }}</td>
                <td>楼{{ item.refundAmountText }}</td>
                <td>{{ item.reason }}</td>
                <td><em class="status">{{ statusText(item.aftersaleStatus) }}</em></td>
                <td>{{ item.applyTime?.replace('T', ' ').slice(0, 19) }}</td>
                <td class="actions">
                  <button v-if="item.aftersaleStatus === 'APPLYING'" @click="approveRefund(item.id)">Approve</button>
                  <button v-if="item.aftersaleStatus === 'APPLYING'" @click="rejectRefund(item.id)">Reject</button>
                  <button @click="openOrderDetail(item.orderId)">Order</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="aftersales.length === 0" class="empty">No after-sale requests</div>
        </section>
      </section>

      <section v-else-if="page === 'order-detail' && currentOrder" class="page detail-grid">
        <section class="panel info-list">
          <h2>璁㈠崟淇℃伅</h2>
          <div><span>璁㈠崟缂栧彿</span><strong>{{ currentOrder.orderNo }}</strong></div>
          <div><span>璁㈠崟鐘舵€</span><strong>{{ statusText(currentOrder.orderStatus) }}</strong></div>
          <div><span>After-sale</span><strong>{{ statusText(currentOrder.aftersaleStatus) }}</strong></div>
          <div><span>涓嬪崟鏃堕棿</span><strong>{{ currentOrder.createdAt?.replace('T', ' ').slice(0, 19) }}</strong></div>
          <div><span>澶囨敞</span><strong>{{ currentOrder.remark || '-' }}</strong></div>
        </section>
        <section class="panel info-list">
          <h2>鏀惰揣淇℃伅</h2>
          <div><span>鏀惰揣浜</span><strong>{{ currentOrder.receiverName }}</strong></div>
          <div><span>鎵嬫満鍙</span><strong>{{ currentOrder.receiverMobile }}</strong></div>
          <div><span>鍦板潃</span><strong>{{ currentOrder.receiverAddress }}</strong></div>
        </section>
        <section class="panel table-panel full-row">
          <h2>鍟嗗搧淇℃伅</h2>
          <table>
            <thead><tr><th>鍟嗗搧</th><th>SKU</th><th>鍗曚环</th><th>鏁伴噺</th><th>灏忚</th></tr></thead>
            <tbody>
              <tr v-for="item in currentOrder.items" :key="item.id">
                <td>{{ item.productName }}</td>
                <td>{{ item.skuName }}</td>
                <td>楼{{ item.salePriceText }}</td>
                <td>{{ item.quantity }}</td>
                <td>楼{{ item.payAmountText }}</td>
              </tr>
            </tbody>
          </table>
        </section>
        <section class="panel info-list">
          <h2>閲戦鏄庣粏</h2>
          <div><span>鍟嗗搧閲戦</span><strong>楼{{ currentOrder.amount.productAmountText }}</strong></div>
          <div><span>浼樻儬鍒</span><strong>-楼{{ currentOrder.amount.couponDiscountAmountText }}</strong></div>
          <div><span>绉垎鎶垫墸</span><strong>-楼{{ currentOrder.amount.pointDiscountAmountText }}</strong></div>
          <div><span>杩愯垂</span><strong>楼{{ currentOrder.amount.freightAmountText }}</strong></div>
          <div><span>瀹炰粯</span><strong class="orange">楼{{ currentOrder.amount.payAmountText }}</strong></div>
        </section>
        <section class="panel info-list">
          <h2>鏀粯淇℃伅</h2>
          <div><span>鏀粯鐘舵€</span><strong>{{ statusText(currentOrder.payStatus) }}</strong></div>
          <div><span>搴斾粯閲戦</span><strong>楼{{ currentOrder.payAmountText }}</strong></div>
          <div><span>鏀粯鎴</span><strong>{{ currentOrder.payExpireTime?.replace('T', ' ').slice(0, 19) }}</strong></div>
        </section>
      </section>

      <section v-else class="page">
        <section class="panel placeholder">
          <h2>{{ placeholderTitle }}</h2>
          <p>璇ユā鍧楀凡鍦ㄨ彍鍗曚腑棰勭暀锛屽悗缁寜 MVP 鑺傚鎺ュ叆銆</p>
        </section>
      </section>

      <div v-if="loading" class="loading">鍔犺浇涓?..</div>
      <div v-if="error" class="error-box"><span>{{ error }}</span><button @click="error = ''">鍏抽棴</button></div>
    </section>
  </main>
</template>

