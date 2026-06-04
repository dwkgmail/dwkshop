<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
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
  | 'order-detail'
  | 'placeholder';

const loggedIn = ref(localStorage.getItem('dwkshop-admin-login') === '1');
const loginForm = reactive({ username: 'admin', password: 'admin123' });
const page = ref<Page>('dashboard');
const placeholderTitle = ref('');
const loading = ref(false);
const error = ref('');
const toast = ref('');

const products = ref<AdminProduct[]>([]);
const categories = ref<Category[]>([]);
const orders = ref<OrderSummary[]>([]);
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
  noticeTitle: '用户购买须知',
  noticeContent: '',
  skus: [
    {
      skuName: '默认规格',
      specJson: '{"规格":"默认"}',
      salePrice: 9900,
      linePrice: 12900,
      stock: 100,
      skuStatus: 'ENABLED'
    }
  ]
});

const menu = [
  { key: 'dashboard', label: '首页' },
  { key: 'products', label: '商品管理' },
  { key: 'orders', label: '订单管理' },
  { key: 'users', label: '用户管理' },
  { key: 'marketing', label: '营销管理' },
  { key: 'aftersale', label: '售后管理' },
  { key: 'finance', label: '财务管理' },
  { key: 'permission', label: '权限管理' }
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
  const lowStock = products.value.filter((item) => item.stock <= 10).length;
  return {
    orderCount,
    payAmountText: formatCents(payAmount),
    waitShip,
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
    error.value = err instanceof Error ? err.message : '操作失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

function login() {
  loggedIn.value = true;
  localStorage.setItem('dwkshop-admin-login', '1');
  loadDashboard();
}

function logout() {
  loggedIn.value = false;
  localStorage.removeItem('dwkshop-admin-login');
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
  } else {
    placeholderTitle.value = menu.find((item) => item.key === key)?.label ?? '模块';
    page.value = 'placeholder';
  }
}

async function loadDashboard() {
  await runTask(async () => {
    await Promise.all([loadProductsQuietly(), loadOrdersQuietly(), loadCategoriesQuietly()]);
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
    noticeTitle: '用户购买须知',
    noticeContent: '',
    skus: [
      {
        skuName: '默认规格',
        specJson: '{"规格":"默认"}',
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
    noticeTitle: detail.noticeTitle ?? '用户购买须知',
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
    skuName: `规格${productForm.skus.length + 1}`,
    specJson: '{"规格":"新增"}',
    salePrice: 9900,
    linePrice: 12900,
    stock: 100,
    skuStatus: 'ENABLED'
  });
}

function removeSku(index: number) {
  if (productForm.skus.length === 1) {
    showToast('至少保留一个 SKU');
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
    showToast('请输入商品名称');
    return;
  }
  await runTask(async () => {
    if (page.value === 'product-edit' && editingId.value) {
      await updateProduct(editingId.value, payload());
      showToast('商品已保存');
    } else {
      await createProduct(payload());
      showToast('商品已新增');
    }
    page.value = 'products';
    await loadProductsQuietly();
  });
}

async function changeSaleStatus(id: number, status: 'ON_SALE' | 'OFF_SALE') {
  await runTask(async () => {
    if (status === 'ON_SALE') {
      await onSaleProduct(id);
      showToast('已上架');
    } else {
      await offSaleProduct(id);
      showToast('已下架');
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

function statusText(status: string) {
  const map: Record<string, string> = {
    ON_SALE: '上架',
    OFF_SALE: '下架',
    WAIT_PAY: '待支付',
    CANCELED: '已取消',
    WAIT_SHIP: '待发货',
    WAIT_RECEIVE: '待收货',
    FINISHED: '已完成',
    UNPAID: '未支付',
    PAID: '已支付',
    UNSHIPPED: '未发货'
  };
  return map[status] ?? status;
}

onMounted(() => {
  if (loggedIn.value) loadDashboard();
});
</script>

<template>
  <main v-if="!loggedIn" class="login-page">
    <section class="login-card">
      <div class="login-brand">
        <span>福客满</span>
        <h1>电商管理后台</h1>
        <p>商品、订单与运营数据管理</p>
      </div>
      <form @submit.prevent="login">
        <label>
          账号
          <input v-model="loginForm.username" />
        </label>
        <label>
          密码
          <input v-model="loginForm.password" type="password" />
        </label>
        <button class="primary wide" type="submit">模拟登录</button>
      </form>
    </section>
  </main>

  <main v-else class="admin-layout">
    <div v-if="toast" class="toast">{{ toast }}</div>
    <aside class="sidebar">
      <div class="brand">福客满后台</div>
      <nav>
        <button v-for="item in menu" :key="item.key" :class="{ active: page === item.key || (item.key === 'products' && page.startsWith('product')) || (item.key === 'orders' && page.startsWith('order')) }" @click="nav(item.key)">
          {{ item.label }}
        </button>
      </nav>
    </aside>

    <section class="content">
      <header class="topbar">
        <div>
          <h1>
            {{
              page === 'dashboard' ? '后台首页' :
              page === 'products' ? '商品列表' :
              page === 'product-create' ? '新增商品' :
              page === 'product-edit' ? '编辑商品' :
              page === 'orders' ? '订单列表' :
              page === 'order-detail' ? '订单详情' : placeholderTitle
            }}
          </h1>
          <span>高效管理商品、订单与运营数据</span>
        </div>
        <button class="ghost" @click="logout">退出</button>
      </header>

      <section v-if="page === 'dashboard'" class="page">
        <section class="metrics">
          <article><span>订单总数</span><strong>{{ dashboard.orderCount }}</strong></article>
          <article><span>支付金额</span><strong>¥{{ dashboard.payAmountText }}</strong></article>
          <article><span>待发货</span><strong>{{ dashboard.waitShip }}</strong></article>
          <article><span>库存预警</span><strong>{{ dashboard.lowStock }}</strong></article>
        </section>
        <section class="dashboard-grid">
          <div class="panel">
            <h2>销售趋势</h2>
            <div class="chart-line">
              <i v-for="height in [38, 54, 42, 76, 62, 88, 104]" :key="height" :style="{ height: `${height}px` }"></i>
            </div>
          </div>
          <div class="panel">
            <h2>快捷入口</h2>
            <div class="quick-actions">
              <button @click="openCreate">新增商品</button>
              <button @click="nav('orders')">查看订单</button>
              <button @click="nav('products')">商品管理</button>
            </div>
          </div>
        </section>
      </section>

      <section v-else-if="page === 'products'" class="page">
        <section class="panel filters">
          <input v-model="productFilters.name" placeholder="商品名称" />
          <select v-model="productFilters.categoryId">
            <option value="">全部分类</option>
            <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
          </select>
          <select v-model="productFilters.saleStatus">
            <option value="">全部状态</option>
            <option value="ON_SALE">上架</option>
            <option value="OFF_SALE">下架</option>
          </select>
          <button class="primary" @click="openCreate">新增商品</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead>
              <tr>
                <th>商品</th>
                <th>分类</th>
                <th>价格</th>
                <th>库存</th>
                <th>销量</th>
                <th>状态</th>
                <th>操作</th>
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
                <td>¥{{ product.minSalePriceText }}</td>
                <td>{{ product.stock }}</td>
                <td>{{ product.actualSales + product.virtualSales }}</td>
                <td><em :class="['status', product.saleStatus]">{{ statusText(product.saleStatus) }}</em></td>
                <td class="actions">
                  <button @click="openEdit(product.id)">编辑</button>
                  <button v-if="product.saleStatus !== 'ON_SALE'" @click="changeSaleStatus(product.id, 'ON_SALE')">上架</button>
                  <button v-else @click="changeSaleStatus(product.id, 'OFF_SALE')">下架</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredProducts.length === 0" class="empty">暂无商品</div>
        </section>
      </section>

      <section v-else-if="page === 'product-create' || page === 'product-edit'" class="page form-page">
        <section class="panel form-grid">
          <label>商品名称<input v-model="productForm.name" /></label>
          <label>分类<select v-model.number="productForm.categoryId"><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select></label>
          <label>主图<input v-model="productForm.mainImageUrl" /></label>
          <label>商品状态<select v-model="productForm.saleStatus"><option value="OFF_SALE">下架</option><option value="ON_SALE">上架</option></select></label>
          <label>商品副标题<input v-model="productForm.subtitle" /></label>
          <label>配送类型<select v-model="productForm.deliveryType"><option value="NORMAL">普通快递</option><option value="COLD_CHAIN">冷链配送</option></select></label>
        </section>
        <section class="panel toggles">
          <label><input v-model="productForm.allowCart" type="checkbox" /> 允许加购</label>
          <label><input v-model="productForm.allowSingleBuy" type="checkbox" /> 允许单独购买</label>
          <label><input v-model="productForm.pointDeductEnabled" type="checkbox" /> 支持积分抵扣</label>
          <label><input v-model="productForm.pointRewardEnabled" type="checkbox" /> 返积分</label>
          <label>返积分数量<input v-model.number="productForm.pointReward" type="number" min="0" /></label>
        </section>
        <section class="panel">
          <div class="section-heading">
            <h2>SKU</h2>
            <button class="ghost" @click="addSku">新增 SKU</button>
          </div>
          <div v-for="(sku, index) in productForm.skus" :key="index" class="sku-editor">
            <label>SKU 名称<input v-model="sku.skuName" /></label>
            <label>规格 JSON<input v-model="sku.specJson" /></label>
            <label>价格(分)<input v-model.number="sku.salePrice" type="number" min="0" /></label>
            <label>库存<input v-model.number="sku.stock" type="number" min="0" /></label>
            <label>状态<select v-model="sku.skuStatus"><option value="ENABLED">启用</option><option value="DISABLED">禁用</option></select></label>
            <button class="danger" @click="removeSku(index)">删除</button>
          </div>
        </section>
        <section class="panel form-grid">
          <label>购买须知标题<input v-model="productForm.noticeTitle" /></label>
          <label class="full">购买须知内容<textarea v-model="productForm.noticeContent"></textarea></label>
        </section>
        <div class="form-actions">
          <button class="ghost" @click="page = 'products'">取消</button>
          <button class="primary" @click="saveProduct">保存</button>
        </div>
      </section>

      <section v-else-if="page === 'orders'" class="page">
        <section class="panel filters">
          <input v-model="orderFilters.orderNo" placeholder="订单编号" />
          <input v-model="orderFilters.mobile" placeholder="手机号" />
          <select v-model="orderFilters.orderStatus">
            <option value="">全部状态</option>
            <option value="WAIT_PAY">待支付</option>
            <option value="CANCELED">已取消</option>
            <option value="WAIT_SHIP">待发货</option>
          </select>
          <button class="primary" @click="loadOrders">查询</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead>
              <tr>
                <th>订单编号</th>
                <th>手机号</th>
                <th>下单时间</th>
                <th>金额</th>
                <th>订单状态</th>
                <th>支付状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in filteredOrders" :key="order.id">
                <td>{{ order.orderNo }}</td>
                <td>{{ orderDetailsById[order.id]?.receiverMobile ?? '-' }}</td>
                <td>{{ order.createdAt?.replace('T', ' ').slice(0, 19) }}</td>
                <td>¥{{ order.payAmountText }}</td>
                <td><em class="status">{{ statusText(order.orderStatus) }}</em></td>
                <td>{{ statusText(order.payStatus) }}</td>
                <td class="actions"><button @click="openOrderDetail(order.id)">详情</button></td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredOrders.length === 0" class="empty">暂无订单</div>
        </section>
      </section>

      <section v-else-if="page === 'order-detail' && currentOrder" class="page detail-grid">
        <section class="panel info-list">
          <h2>订单信息</h2>
          <div><span>订单编号</span><strong>{{ currentOrder.orderNo }}</strong></div>
          <div><span>订单状态</span><strong>{{ statusText(currentOrder.orderStatus) }}</strong></div>
          <div><span>下单时间</span><strong>{{ currentOrder.createdAt?.replace('T', ' ').slice(0, 19) }}</strong></div>
          <div><span>备注</span><strong>{{ currentOrder.remark || '-' }}</strong></div>
        </section>
        <section class="panel info-list">
          <h2>收货信息</h2>
          <div><span>收货人</span><strong>{{ currentOrder.receiverName }}</strong></div>
          <div><span>手机号</span><strong>{{ currentOrder.receiverMobile }}</strong></div>
          <div><span>地址</span><strong>{{ currentOrder.receiverAddress }}</strong></div>
        </section>
        <section class="panel table-panel full-row">
          <h2>商品信息</h2>
          <table>
            <thead><tr><th>商品</th><th>SKU</th><th>单价</th><th>数量</th><th>小计</th></tr></thead>
            <tbody>
              <tr v-for="item in currentOrder.items" :key="item.id">
                <td>{{ item.productName }}</td>
                <td>{{ item.skuName }}</td>
                <td>¥{{ item.salePriceText }}</td>
                <td>{{ item.quantity }}</td>
                <td>¥{{ item.payAmountText }}</td>
              </tr>
            </tbody>
          </table>
        </section>
        <section class="panel info-list">
          <h2>金额明细</h2>
          <div><span>商品金额</span><strong>¥{{ currentOrder.amount.productAmountText }}</strong></div>
          <div><span>优惠券</span><strong>-¥{{ currentOrder.amount.couponDiscountAmountText }}</strong></div>
          <div><span>积分抵扣</span><strong>-¥{{ currentOrder.amount.pointDiscountAmountText }}</strong></div>
          <div><span>运费</span><strong>¥{{ currentOrder.amount.freightAmountText }}</strong></div>
          <div><span>实付</span><strong class="orange">¥{{ currentOrder.amount.payAmountText }}</strong></div>
        </section>
        <section class="panel info-list">
          <h2>支付信息</h2>
          <div><span>支付状态</span><strong>{{ statusText(currentOrder.payStatus) }}</strong></div>
          <div><span>应付金额</span><strong>¥{{ currentOrder.payAmountText }}</strong></div>
          <div><span>支付截止</span><strong>{{ currentOrder.payExpireTime?.replace('T', ' ').slice(0, 19) }}</strong></div>
        </section>
      </section>

      <section v-else class="page">
        <section class="panel placeholder">
          <h2>{{ placeholderTitle }}</h2>
          <p>该模块已在菜单中预留，后续按 MVP 节奏接入。</p>
        </section>
      </section>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-if="error" class="error-box"><span>{{ error }}</span><button @click="error = ''">关闭</button></div>
    </section>
  </main>
</template>
