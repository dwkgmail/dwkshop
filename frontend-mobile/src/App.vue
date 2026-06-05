<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { changeUserPassword, loginUser, logoutUser, registerUser } from './api/auth';
import { createAftersale } from './api/aftersales';
import { AUTH_EXPIRED_EVENT, clearAuthToken, getAuthToken, setAuthTokens } from './api/client';
import {
  addCartItem,
  checkAllCartItems,
  deleteCartItem,
  getCart,
  updateCartChecked,
  updateCartItem,
  type CartResponse
} from './api/cart';
import {
  getCategories,
  getProduct,
  getProducts,
  searchProducts,
  type Category,
  type ProductDetail,
  type ProductSummary,
  type ProductSku
} from './api/products';
import {
  cancelOrder,
  confirmOrder,
  createOrder,
  getOrder,
  getOrders,
  payOrder,
  type ConfirmOrderPayload,
  type ConfirmOrderResponse,
  type OrderDetail,
  type OrderSummary
} from './api/orders';

type ViewName =
  | 'home'
  | 'login'
  | 'category'
  | 'discover'
  | 'search'
  | 'detail'
  | 'cart'
  | 'confirm'
  | 'payment'
  | 'orders'
  | 'order-detail'
  | 'mine';

const route = reactive<{ view: ViewName; params: Record<string, string | number> }>({
  view: 'home',
  params: {}
});

const loading = ref(false);
const error = ref('');
const toast = ref('');
const loggedIn = ref(Boolean(getAuthToken()));
const loginForm = reactive({ mobile: '13800000001', password: 'user123' });
const registerForm = reactive({ mobile: '', password: '', nickname: '' });
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
const authMode = ref<'login' | 'register'>('login');
const showPasswordPanel = ref(false);
const currentUserName = ref(localStorage.getItem('dwkshop-user-name') ?? '');
const postLoginTarget = ref<{ view: ViewName; params: Record<string, string | number> } | null>(null);
const products = ref<ProductSummary[]>([]);
const searchResults = ref<ProductSummary[]>([]);
const categories = ref<Category[]>([]);
const activeCategoryId = ref<number | null>(null);
const productDetail = ref<ProductDetail | null>(null);
const selectedSkuId = ref<number | null>(null);
const selectedQuantity = ref(1);
const cart = ref<CartResponse | null>(null);
const searchKeyword = ref('');
const confirmPayload = ref<ConfirmOrderPayload | null>(null);
const confirmData = ref<ConfirmOrderResponse | null>(null);
const currentOrder = ref<OrderDetail | null>(null);
const orders = ref<OrderSummary[]>([]);
const paymentMessage = ref('');

const cartBadge = computed(() => (loggedIn.value ? cart.value?.badgeCount ?? 0 : 0));
const userDisplayName = computed(() => currentUserName.value || '测试用户');
const userAvatarText = computed(() => userDisplayName.value.slice(0, 1));
const currentSku = computed(() => productDetail.value?.skus.find((item) => item.id === selectedSkuId.value) ?? null);
const allCartChecked = computed(() => {
  const items = cart.value?.items ?? [];
  const checkable = items.filter((item) => item.canCheck);
  return checkable.length > 0 && checkable.every((item) => item.checked);
});

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
    error.value = err instanceof Error ? err.message : '操作失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

function navigate(view: ViewName, params: Record<string, string | number> = {}) {
  if (requiresLogin(view) && !loggedIn.value) {
    postLoginTarget.value = { view, params };
    route.view = 'login';
    route.params = {};
    error.value = '';
    return;
  }
  route.view = view;
  route.params = params;
  error.value = '';
  if (view === 'home') loadHome();
  if (view === 'category') loadCategories();
  if (view === 'cart') loadCart();
  if (view === 'orders') loadOrders();
  if (view === 'mine') loadCart();
  if (view === 'detail' && params.id) loadProductDetail(Number(params.id));
  if (view === 'order-detail' && params.id) loadOrderDetail(Number(params.id));
}

function requiresLogin(view: ViewName) {
  return ['cart', 'confirm', 'payment', 'orders', 'order-detail', 'mine'].includes(view);
}

function openLogin(target: { view: ViewName; params: Record<string, string | number> } = { view: 'mine', params: {} }) {
  postLoginTarget.value = target;
  route.view = 'login';
  route.params = {};
  error.value = '';
}

async function login() {
  const mobile = loginForm.mobile.trim();
  const password = loginForm.password.trim();
  if (!mobile || !password) {
    showToast('Please enter mobile and password');
    return;
  }
  await runTask(async () => {
    const result = await loginUser(mobile, password);
    setAuthTokens(result.token, result.refreshToken);
    localStorage.setItem('dwkshop-user-name', result.name);
    currentUserName.value = result.name;
    loggedIn.value = true;
    showToast(`欢迎回来，${result.name}`);
    const target = postLoginTarget.value ?? { view: 'home' as ViewName, params: {} };
    postLoginTarget.value = null;
    navigate(target.view, target.params);
  });
}

async function register() {
  const mobile = registerForm.mobile.trim();
  const password = registerForm.password.trim();
  const nickname = registerForm.nickname.trim();
  if (!mobile || !password) {
    showToast('Please enter mobile and password');
    return;
  }
  await runTask(async () => {
    const result = await registerUser(mobile, password, nickname);
    setAuthTokens(result.token, result.refreshToken);
    localStorage.setItem('dwkshop-user-name', result.name);
    currentUserName.value = result.name;
    loggedIn.value = true;
    showToast(`Registered, welcome ${result.name}`);
    const target = postLoginTarget.value ?? { view: 'home' as ViewName, params: {} };
    postLoginTarget.value = null;
    navigate(target.view, target.params);
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
    const result = await changeUserPassword(oldPassword, newPassword);
    setAuthTokens(result.token, result.refreshToken);
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' });
    showPasswordPanel.value = false;
    showToast('Password updated');
  });
}

function resetSession(message?: string) {
  clearAuthToken();
  localStorage.removeItem('dwkshop-user-name');
  loggedIn.value = false;
  currentUserName.value = '';
  cart.value = null;
  orders.value = [];
  currentOrder.value = null;
  confirmData.value = null;
  confirmPayload.value = null;
  showPasswordPanel.value = false;
  Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' });
  if (message) showToast(message);
}

async function logout() {
  try {
    if (getAuthToken()) await logoutUser();
  } catch {
    // Client-side logout still clears all local session state.
  }
  resetSession('Logged out');
  navigate('home');
  return;
}
function handleAuthExpired() {
  resetSession('Login expired, please sign in again');
  postLoginTarget.value = requiresLogin(route.view) ? { view: route.view, params: { ...route.params } } : null;
  route.view = 'login';
  route.params = {};
  authMode.value = 'login';
}


async function loadHome() {
  await runTask(async () => {
    products.value = await getProducts();
    await loadCartQuietly();
  });
}

async function loadCategories() {
  await runTask(async () => {
    categories.value = await getCategories();
    products.value = await getProducts(activeCategoryId.value ?? undefined);
    await loadCartQuietly();
  });
}

async function selectCategory(id: number | null) {
  activeCategoryId.value = id;
  await runTask(async () => {
    products.value = await getProducts(id ?? undefined);
  });
}

async function loadProductDetail(id: number) {
  await runTask(async () => {
    const detail = await getProduct(id);
    productDetail.value = detail;
    selectedSkuId.value = detail.skus.find((sku) => sku.selectable)?.id ?? detail.skus[0]?.id ?? null;
    selectedQuantity.value = 1;
  });
}

async function loadCart() {
  await runTask(async () => {
    cart.value = await getCart();
  });
}

async function loadCartQuietly() {
  if (!loggedIn.value) {
    cart.value = null;
    return;
  }
  try {
    cart.value = await getCart();
  } catch {
    cart.value = null;
  }
}

async function submitSearch() {
  const keyword = searchKeyword.value.trim();
  if (!keyword) {
    showToast('请输入搜索关键词');
    return;
  }
  route.view = 'search';
  await runTask(async () => {
    searchResults.value = await searchProducts(keyword);
  });
}

async function addCurrentSkuToCart() {
  if (!loggedIn.value) {
    openLogin({ view: 'detail', params: productDetail.value ? { id: productDetail.value.id } : {} });
    return;
  }
  const sku = currentSku.value;
  if (!productDetail.value || !sku) {
    showToast('璇烽€夋嫨瑙勬牸');
    return;
  }
  await runTask(async () => {
    cart.value = await addCartItem(sku.id, selectedQuantity.value);
    showToast('已加入购物车');
  });
}

async function buyNow() {
  if (!loggedIn.value) {
    openLogin({ view: 'detail', params: productDetail.value ? { id: productDetail.value.id } : {} });
    return;
  }
  const sku = currentSku.value;
  if (!productDetail.value || !sku) {
    showToast('璇烽€夋嫨瑙勬牸');
    return;
  }
  await openConfirm({
    sourceType: 'BUY_NOW',
    skuId: sku.id,
    quantity: selectedQuantity.value,
    usePoints: true
  });
}

async function openCartConfirm() {
  if (!loggedIn.value) {
    openLogin({ view: 'cart', params: {} });
    return;
  }
  const ids = (cart.value?.items ?? []).filter((item) => item.checked && item.canCheck).map((item) => item.id);
  if (ids.length === 0) {
    showToast('Please select items to checkout');
    return;
  }
  await openConfirm({ sourceType: 'CART', cartItemIds: ids, usePoints: true });
}

async function openConfirm(payload: ConfirmOrderPayload) {
  confirmPayload.value = payload;
  route.view = 'confirm';
  await runTask(async () => {
    confirmData.value = await confirmOrder(payload);
  });
}

async function toggleUsePoints() {
  if (!confirmPayload.value || !confirmData.value?.pointDeduction.visible) return;
  confirmPayload.value = {
    ...confirmPayload.value,
    usePoints: !confirmData.value.pointDeduction.selected
  };
  await openConfirm(confirmPayload.value);
}

async function submitOrder() {
  if (!confirmData.value) return;
  await runTask(async () => {
    currentOrder.value = await createOrder(confirmData.value!.settlementToken, confirmData.value!.amount.payAmount);
    await loadCartQuietly();
    paymentMessage.value = '';
    route.view = 'payment';
  });
}

async function payCurrentOrder() {
  if (!currentOrder.value) return;
  await runTask(async () => {
    currentOrder.value = await payOrder(currentOrder.value!.id);
    paymentMessage.value = 'Payment success, order is waiting for shipment';
    showToast('Payment success');
  });
}

async function loadOrders() {
  await runTask(async () => {
    orders.value = await getOrders();
  });
}

async function loadOrderDetail(id: number) {
  await runTask(async () => {
    currentOrder.value = await getOrder(id);
  });
}

async function cancelCurrentOrder(id: number) {
  await runTask(async () => {
    currentOrder.value = await cancelOrder(id);
    showToast('Order canceled');
  });
}

async function applyRefund(order: OrderDetail) {
  const reason = window.prompt('Refund reason', 'I want to request a refund')?.trim();
  if (!reason) return;
  await runTask(async () => {
    await createAftersale(order.id, reason);
    currentOrder.value = await getOrder(order.id);
    showToast('Refund request submitted');
  });
}

async function changeCartQuantity(id: number, quantity: number) {
  if (quantity < 1) return;
  await runTask(async () => {
    cart.value = await updateCartItem(id, quantity);
  });
}

async function removeCartItem(id: number) {
  await runTask(async () => {
    cart.value = await deleteCartItem(id);
    showToast('Deleted');
  });
}

async function toggleCartItem(id: number, checked: boolean) {
  await runTask(async () => {
    cart.value = await updateCartChecked(id, checked);
  });
}

async function toggleAllCartItems() {
  await runTask(async () => {
    cart.value = await checkAllCartItems(!allCartChecked.value);
  });
}

function statusText(status: string) {
  const map: Record<string, string> = {
    WAIT_PAY: 'Pending payment',
    CANCELED: 'Canceled',
    WAIT_SHIP: 'Waiting shipment',
    WAIT_RECEIVE: 'Waiting receipt',
    FINISHED: 'Finished',
    REFUNDED: 'Refunded',
    APPLYING: 'Refund applying',
    REJECTED: 'Refund rejected'
  };
  return map[status] ?? status;
}

function productTag(product: ProductSummary) {
  if (product.deliveryType === 'COLD_CHAIN') return 'Cold chain';
  if (!product.allowCart) return 'No cart';
  if (!product.allowSingleBuy) return 'Bundle only';
  if (product.pointDeductEnabled) return 'Points';
  return 'Hot';
}

function imageTone(id: number) {
  const tones = ['tone-orange', 'tone-green', 'tone-blue', 'tone-dark', 'tone-pink'];
  return tones[id % tones.length];
}

onMounted(() => {
  window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
  loadHome();
});

onUnmounted(() => {
  window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
});
</script>

<template>
  <main class="app-shell">
    <div v-if="toast" class="toast">{{ toast }}</div>
    <section class="app-content">
      <header v-if="route.view !== 'home' && route.view !== 'login'" class="page-header">
        <button class="icon-btn" @click="navigate('home')">&lt;</button>
        <strong>
          {{
            route.view === 'category' ? 'Category' :
            route.view === 'search' ? 'Search' :
            route.view === 'detail' ? 'Product detail' :
            route.view === 'cart' ? 'Cart' :
            route.view === 'confirm' ? 'Confirm order' :
            route.view === 'payment' ? 'Payment' :
            route.view === 'orders' ? 'Orders' :
            route.view === 'order-detail' ? 'Order detail' :
            route.view === 'mine' ? 'Mine' : 'Discover'
          }}
        </strong>
        <span></span>
      </header>

      <section v-if="route.view === 'login'" class="view login-view">
        <div class="login-brand">
          <span>DWK Shop</span>
          <h1>用户登录</h1>
          <p>鐧诲綍鍚庡彲浣跨敤璐墿杞︺€佷笅鍗曞拰璁㈠崟鏌ヨ銆</p>
        </div>
        <div class="login-card auth-switch">
          <button type="button" :class="{ active: authMode === 'login' }" @click="authMode = 'login'">Login</button>
          <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'">Register</button>
        </div>
        <form v-if="authMode === 'login'" class="login-card" @submit.prevent="login">
          <label>
            <span>鎵嬫満鍙</span>
            <input v-model="loginForm.mobile" inputmode="tel" autocomplete="tel" placeholder="请输入手机号" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="Password" />
          </label>
          <button class="primary wide" type="submit">登录</button>
          <button class="ghost wide" type="button" @click="navigate('home')">鍏堝幓閫涢€</button>
          <p class="demo-account">测试账号：13800000001 / user123</p>
        </form>
        <form v-else class="login-card" @submit.prevent="register">
          <label>
            <span>Mobile</span>
            <input v-model="registerForm.mobile" inputmode="tel" autocomplete="tel" placeholder="Mobile number" />
          </label>
          <label>
            <span>Nickname</span>
            <input v-model="registerForm.nickname" autocomplete="nickname" placeholder="Nickname" />
          </label>
          <label>
            <span>Password</span>
            <input v-model="registerForm.password" type="password" autocomplete="new-password" placeholder="Password" />
          </label>
          <button class="primary wide" type="submit">Register</button>
          <button class="ghost wide" type="button" @click="navigate('home')">Browse first</button>
        </form>
      </section>

      <section v-else-if="route.view === 'home'" class="view">
        <div class="home-top">
          <div>
            <span>DWK Shop</span>
            <h1>绮鹃€夊ソ鐗</h1>
          </div>
          <button class="round-btn" @click="loggedIn ? navigate('orders') : openLogin({ view: 'orders', params: {} })">
            {{ loggedIn ? '订单' : '登录' }}
          </button>
        </div>
        <form class="search-bar" @submit.prevent="submitSearch">
          <input v-model="searchKeyword" placeholder="Search products" />
          <button type="submit">搜索</button>
        </form>
        <section class="hero-card">
          <span>澶忓鐒曟柊瀛</span>
          <strong>好物低至 5 鎶</strong>
          <p>浠庡晢鍝佹祻瑙堝埌涓嬪崟鏀粯锛屼竴绔欏紡浣撻獙銆</p>
        </section>
        <section class="shortcut-grid">
          <button @click="navigate('category')">分类</button>
          <button @click="navigate('search')">搜索</button>
          <button @click="navigate('cart')">璐墿杞</button>
          <button @click="navigate('orders')">订单</button>
        </section>
        <div class="section-title">
          <h2>绮鹃€夋帹鑽</h2>
          <button @click="loadHome">刷新</button>
        </div>
        <div class="product-grid">
          <article v-for="product in products" :key="product.id" class="product-card" @click="navigate('detail', { id: product.id })">
            <div class="product-visual" :class="imageTone(product.id)">{{ product.name.slice(0, 2) }}</div>
            <div class="product-info">
              <strong>{{ product.name }}</strong>
              <span>{{ product.subtitle }}</span>
              <div class="tags"><em>{{ productTag(product) }}</em><em>已售 {{ product.displayedSales }}</em></div>
              <div class="price">¥{{ product.minSalePriceText }}</div>
            </div>
          </article>
        </div>
      </section>

      <section v-else-if="route.view === 'category'" class="view category-layout">
        <aside>
          <button :class="{ active: activeCategoryId === null }" @click="selectCategory(null)">全部</button>
          <button v-for="category in categories" :key="category.id" :class="{ active: activeCategoryId === category.id }" @click="selectCategory(category.id)">
            {{ category.name }}
          </button>
        </aside>
        <div class="list-column">
          <article v-for="product in products" :key="product.id" class="list-product" @click="navigate('detail', { id: product.id })">
            <div class="small-visual" :class="imageTone(product.id)">{{ product.name.slice(0, 1) }}</div>
            <div>
              <strong>{{ product.name }}</strong>
              <span>{{ productTag(product) }} · 已售 {{ product.displayedSales }}</span>
              <p>¥{{ product.minSalePriceText }}</p>
            </div>
          </article>
        </div>
      </section>

      <section v-else-if="route.view === 'search'" class="view">
        <form class="search-bar sticky" @submit.prevent="submitSearch">
          <input v-model="searchKeyword" placeholder="Search keyword" />
          <button type="submit">搜索</button>
        </form>
        <div v-if="searchResults.length === 0" class="empty-state">暂无搜索结果</div>
        <article v-for="product in searchResults" :key="product.id" class="list-product" @click="navigate('detail', { id: product.id })">
          <div class="small-visual" :class="imageTone(product.id)">{{ product.name.slice(0, 1) }}</div>
          <div>
            <strong>{{ product.name }}</strong>
            <span>{{ product.subtitle }}</span>
            <p>¥{{ product.minSalePriceText }}</p>
          </div>
        </article>
      </section>

      <section v-else-if="route.view === 'discover'" class="view">
        <section class="hero-card subtle">
          <span>发现</span>
          <strong>鏂板搧銆佸喎閾俱€佹惌閰嶈喘</strong>
          <p>杩欓噷鑱氬悎鎼滅储銆佹椿鍔ㄥ拰鎺ㄨ崘鍏ュ彛銆</p>
        </section>
        <button class="primary wide" @click="navigate('search')">鍘绘悳绱㈠晢鍝</button>
      </section>

      <section v-else-if="route.view === 'detail' && productDetail" class="view detail-view">
        <div class="detail-visual" :class="imageTone(productDetail.id)">{{ productDetail.name.slice(0, 2) }}</div>
        <div class="detail-title">
          <span v-if="productDetail.offSale">{{ productDetail.offSaleMessage }}</span>
          <h1>{{ productDetail.name }}</h1>
          <p>{{ productDetail.subtitle }}</p>
          <strong>¥{{ currentSku?.salePriceText ?? productDetail.minSalePriceText }}</strong>
        </div>
        <section class="panel">
          <h2>选择规格</h2>
          <div class="sku-list">
            <button
              v-for="sku in productDetail.skus"
              :key="sku.id"
              :disabled="!sku.selectable"
              :class="{ active: selectedSkuId === sku.id }"
              @click="selectedSkuId = sku.id"
            >
              {{ sku.skuName }} <span>{{ sku.stock > 0 ? `库存 ${sku.stock}` : '售罄' }}</span>
            </button>
          </div>
        </section>
        <section v-if="productDetail.noticeTitle" class="panel">
          <h2>{{ productDetail.noticeTitle }}</h2>
          <p>{{ productDetail.noticeContent }}</p>
        </section>
        <div class="quantity-row">
          <span>数量</span>
          <div class="stepper">
            <button @click="selectedQuantity = Math.max(1, selectedQuantity - 1)">-</button>
            <strong>{{ selectedQuantity }}</strong>
            <button @click="selectedQuantity += 1">+</button>
          </div>
        </div>
        <footer class="action-bar">
          <button class="ghost" :disabled="!productDetail.allowCart || productDetail.offSale" @click="addCurrentSkuToCart">鍔犲叆璐墿杞</button>
          <button class="primary" :disabled="productDetail.offSale" @click="buyNow">立即购买</button>
        </footer>
      </section>

      <section v-else-if="route.view === 'cart'" class="view cart-view">
        <div v-if="!cart || cart.items.length === 0" class="empty-state">璐墿杞﹁繕鏄┖鐨</div>
        <article v-for="item in cart?.items" :key="item.id" class="cart-item">
          <input type="checkbox" :checked="item.checked" :disabled="!item.canCheck" @change="toggleCartItem(item.id, ($event.target as HTMLInputElement).checked)" />
          <div class="small-visual" :class="imageTone(item.productId)">{{ item.productName?.slice(0, 1) }}</div>
          <div class="cart-info">
            <strong>{{ item.productName }}</strong>
            <span>{{ item.skuName }}</span>
            <em v-if="item.status !== 'NORMAL'">{{ item.statusMessage }}</em>
            <div class="cart-bottom">
              <p>¥{{ item.salePriceText }}</p>
              <div class="stepper">
                <button @click="changeCartQuantity(item.id, item.quantity - 1)">-</button>
                <strong>{{ item.quantity }}</strong>
                <button @click="changeCartQuantity(item.id, item.quantity + 1)">+</button>
              </div>
              <button class="text-danger" @click="removeCartItem(item.id)">删除</button>
            </div>
          </div>
        </article>
        <footer class="cart-bar">
          <label><input type="checkbox" :checked="allCartChecked" @change="toggleAllCartItems" /> 鍏ㄩ€</label>
          <div><span>合计</span><strong>¥{{ cart?.estimatedAmountText ?? '0' }}</strong></div>
          <button class="primary" @click="openCartConfirm">鍘荤粨绠</button>
        </footer>
      </section>

      <section v-else-if="route.view === 'confirm'" class="view">
        <div v-if="confirmData" class="confirm-stack">
          <section class="panel address-panel">
            <strong>{{ confirmData.address.receiverName }} {{ confirmData.address.receiverMobile }}</strong>
            <p>{{ confirmData.address.province }}{{ confirmData.address.city }}{{ confirmData.address.district }}{{ confirmData.address.detailAddress }}</p>
          </section>
          <article v-for="item in confirmData.items" :key="item.skuId" class="order-line">
            <div class="small-visual" :class="imageTone(item.productId)">{{ item.productName.slice(0, 1) }}</div>
            <div>
              <strong>{{ item.productName }}</strong>
              <span>{{ item.skuName }} × {{ item.quantity }}</span>
              <p>¥{{ item.totalAmountText }}</p>
              <em v-if="item.noticeTitle">{{ item.noticeTitle }}：{{ item.noticeContent }}</em>
            </div>
          </article>
          <section class="panel detail-list">
            <div><span>Coupon</span><strong>{{ confirmData.selectedCoupon ? '-¥' + confirmData.selectedCoupon.discountAmountText : 'None' }}</strong></div>
            <div v-if="confirmData.pointDeduction.visible" @click="toggleUsePoints">
              <span>Points</span><strong>{{ confirmData.pointDeduction.selected ? '-¥' + confirmData.pointDeduction.deductionAmountText : 'Unused' }}</strong>
            </div>
            <div><span>Freight</span><strong>¥{{ confirmData.freightAmountText }}</strong></div>
          </section>
          <section class="panel detail-list">
            <div><span>Products</span><strong>¥{{ confirmData.amount.productAmountText }}</strong></div>
            <div><span>Coupon discount</span><strong>-¥{{ confirmData.amount.couponDiscountAmountText }}</strong></div>
            <div><span>Point discount</span><strong>-¥{{ confirmData.amount.pointDiscountAmountText }}</strong></div>
            <div><span>Pay amount</span><strong class="orange">¥{{ confirmData.amount.payAmountText }}</strong></div>
          </section>
          <button class="primary wide" @click="submitOrder">提交订单</button>
        </div>
      </section>

      <section v-else-if="route.view === 'payment'" class="view payment-view">
        <section class="pay-amount">
          <span>{{ currentOrder?.orderStatus === 'WAIT_SHIP' ? 'Waiting shipment' : '寰呮敮浠' }}</span>
          <strong>¥{{ currentOrder?.payAmountText ?? '0' }}</strong>
          <p>{{ currentOrder?.orderNo }}</p>
        </section>
        <section class="panel payment-methods">
          <label><input type="radio" checked /> 模拟微信支付</label>
          <label><input type="radio" /> 妯℃嫙鏀粯瀹濇敮浠</label>
        </section>
        <button class="primary wide" :disabled="currentOrder?.orderStatus !== 'WAIT_PAY'" @click="payCurrentOrder">立即支付</button>
        <p v-if="paymentMessage" class="success-text">{{ paymentMessage }}</p>
        <button class="ghost wide" @click="currentOrder && navigate('order-detail', { id: currentOrder.id })">查看订单</button>
      </section>

      <section v-else-if="route.view === 'orders'" class="view">
        <div v-if="orders.length === 0" class="empty-state">暂无订单</div>
        <article v-for="order in orders" :key="order.id" class="order-card" @click="navigate('order-detail', { id: order.id })">
          <div><strong>{{ order.orderNo }}</strong><span>{{ statusText(order.orderStatus) }}</span></div>
          <p>实付 ¥{{ order.payAmountText }}</p>
        </article>
      </section>

      <section v-else-if="route.view === 'order-detail' && currentOrder" class="view">
        <section class="panel detail-list">
          <div><span>璁㈠崟鐘舵€</span><strong>{{ statusText(currentOrder.orderStatus) }}</strong></div>
          <div><span>After-sale</span><strong>{{ statusText(currentOrder.aftersaleStatus) }}</strong></div>
          <div><span>订单编号</span><strong>{{ currentOrder.orderNo }}</strong></div>
          <div><span>收货信息</span><strong>{{ currentOrder.receiverName }} {{ currentOrder.receiverMobile }}</strong></div>
          <p>{{ currentOrder.receiverAddress }}</p>
        </section>
        <article v-for="item in currentOrder.items" :key="item.id" class="order-line">
          <div class="small-visual" :class="imageTone(item.productId)">{{ item.productName.slice(0, 1) }}</div>
          <div>
            <strong>{{ item.productName }}</strong>
            <span>{{ item.skuName }} × {{ item.quantity }}</span>
            <p>¥{{ item.payAmountText }}</p>
          </div>
        </article>
        <section class="panel detail-list">
          <div><span>实付金额</span><strong class="orange">¥{{ currentOrder.payAmountText }}</strong></div>
        </section>
        <button v-if="currentOrder.orderStatus === 'WAIT_PAY'" class="ghost wide" @click="cancelCurrentOrder(currentOrder.id)">取消订单</button>
        <button v-if="currentOrder.payStatus === 'PAID' && currentOrder.aftersaleStatus === 'NONE'" class="ghost wide" @click="applyRefund(currentOrder)">Apply refund</button>
      </section>

      <section v-else-if="route.view === 'mine'" class="view mine-view">
        <section class="profile-card">
          <div class="avatar">{{ userAvatarText }}</div>
          <div>
            <strong>{{ userDisplayName }}</strong>
            <span>鏅€氫細鍛</span>
          </div>
          <button class="logout-btn" @click="logout">閫€鍑</button>
        </section>
        <section class="stats">
          <button @click="navigate('orders')"><strong>订单</strong><span>查看全部</span></button>
          <button @click="navigate('cart')"><strong>{{ cartBadge }}</strong><span>璐墿杞</span></button>
          <button><strong>5000</strong><span>积分</span></button>
        </section>
        <section class="panel menu-list">
          <button @click="navigate('orders')">我的订单</button>
          <button @click="navigate('cart')">璐墿杞</button>
          <button @click="navigate('search')">搜索商品</button>
          <button @click="showPasswordPanel = !showPasswordPanel">Change password</button>
        </section>
        <form v-if="showPasswordPanel" class="panel password-panel" @submit.prevent="changePassword">
          <label>
            <span>Old password</span>
            <input v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" />
          </label>
          <label>
            <span>New password</span>
            <input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" />
          </label>
          <label>
            <span>Confirm password</span>
            <input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" />
          </label>
          <button class="primary wide" type="submit">Save password</button>
        </form>
      </section>

      <div v-if="loading" class="loading-mask">鍔犺浇涓?..</div>
      <div v-if="error" class="error-box">
        <span>{{ error }}</span>
        <button @click="error = ''">鐭ラ亾浜</button>
      </div>
    </section>

    <nav v-if="route.view !== 'login'" class="tabbar">
      <button :class="{ active: route.view === 'home' }" @click="navigate('home')">首页</button>
      <button :class="{ active: route.view === 'category' }" @click="navigate('category')">分类</button>
      <button :class="{ active: route.view === 'discover' || route.view === 'search' }" @click="navigate('discover')">发现</button>
      <button :class="{ active: route.view === 'cart' }" @click="navigate('cart')">Cart <span v-if="cartBadge">{{ cartBadge }}</span></button>
      <button :class="{ active: route.view === 'mine' }" @click="navigate('mine')">我的</button>
    </nav>
  </main>
</template>

