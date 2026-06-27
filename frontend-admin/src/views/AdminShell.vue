<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import {
  assignAdminRole,
  createCoupon,
  getAdminAccounts,
  getAdminMembers,
  getCoupons,
  getInventoryReconciliation,
  getOperationLogs,
  getRoles,
  repairInventoryLockedStock,
  updateAdminAccountStatus,
  updateCouponStatus,
  updateMemberStatus,
  type AdminAccount,
  type AdminCoupon,
  type AdminMember,
  type AdminRole,
  type CouponPayload,
  type InventoryReconciliationReport,
  type OperationLog
} from '../api/admin';
import {
  approveAftersale,
  closeAftersale,
  completeAftersaleRefund,
  confirmAftersaleReturned,
  failAftersaleRefund,
  getAftersales,
  rejectAftersale,
  retryAftersaleRefund,
  type Aftersale
} from '../api/aftersales';
import { changeAdminPassword, loginAdmin, logoutAdmin } from '../api/auth';
import { AUTH_EXPIRED_EVENT, clearAuthToken, getAuthToken, setAuthTokens } from '../api/client';
import { getOrder, getOrders, shipOrder, updateDeliveryStatus, type OrderDetail, type OrderSummary } from '../api/orders';
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
} from '../api/products';

type Page =
  | 'dashboard'
  | 'products'
  | 'product-create'
  | 'product-edit'
  | 'orders'
  | 'order-detail'
  | 'aftersales'
  | 'coupons'
  | 'coupon-create'
  | 'users'
  | 'permissions'
  | 'inventory-reconciliation'
  | 'logs'
  | 'placeholder';

const loggedIn = ref(Boolean(getAuthToken()));
const page = ref<Page>('dashboard');
const placeholderTitle = ref('');
const loading = ref(false);
const error = ref('');
const toast = ref('');
const showPasswordPanel = ref(false);

const loginForm = reactive({ username: 'admin', password: 'admin123' });
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });

const products = ref<AdminProduct[]>([]);
const categories = ref<Category[]>([]);
const orders = ref<OrderSummary[]>([]);
const aftersales = ref<Aftersale[]>([]);
const members = ref<AdminMember[]>([]);
const coupons = ref<AdminCoupon[]>([]);
const roles = ref<AdminRole[]>([]);
const adminAccounts = ref<AdminAccount[]>([]);
const operationLogs = ref<OperationLog[]>([]);
const inventoryReport = ref<InventoryReconciliationReport | null>(null);
const currentOrder = ref<OrderDetail | null>(null);
const orderDetailsById = ref<Record<number, OrderDetail>>({});
const editingId = ref<number | null>(null);

const productFilters = reactive({ name: '', categoryId: '', saleStatus: '' });
const orderFilters = reactive({ orderNo: '', mobile: '', orderStatus: '' });
const aftersaleFilters = reactive({ keyword: '', status: '' });
const memberFilters = reactive({ keyword: '', status: '' });
const couponFilters = reactive({ keyword: '', status: '' });
const logFilters = reactive({ module: '', keyword: '' });
const inventoryFilters = reactive({ onlyDiff: true });
const shippingForm = reactive({ logisticsCompany: '', logisticsNo: '', deliveryRemark: '' });
const deliveryStatusForm = reactive({ deliveryStatus: 'IN_TRANSIT', deliveryRemark: '' });

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
  skus: [defaultSku()]
});

const couponForm = reactive<CouponPayload>({
  name: '',
  couponCode: '',
  couponType: 'FULL_REDUCTION',
  thresholdAmount: 100000,
  discountAmount: 10000,
  totalQuantity: 100,
  receiveStartTime: localDateTimeInput(0),
  receiveEndTime: localDateTimeInput(30),
  useStartTime: localDateTimeInput(0),
  useEndTime: localDateTimeInput(60),
  couponStatus: 'ENABLED'
});

const menu = [
  { key: 'inventory-reconciliation', label: '库存对账' },
  { key: 'dashboard', label: '首页' },
  { key: 'products', label: '商品管理' },
  { key: 'orders', label: '订单管理' },
  { key: 'aftersale', label: '售后审核' },
  { key: 'coupons', label: '优惠券管理' },
  { key: 'users', label: '用户管理' },
  { key: 'permissions', label: '权限角色' },
  { key: 'logs', label: '操作日志' },
  { key: 'finance', label: '财务管理' }
];

const pageTitle = computed(() => {
  const titles: Record<Page, string> = {
    'inventory-reconciliation': '库存对账',
    dashboard: '后台首页',
    products: '商品管理',
    'product-create': '新增商品',
    'product-edit': '编辑商品',
    orders: '订单管理',
    'order-detail': '订单详情',
    aftersales: '售后审核',
    coupons: '优惠券管理',
    'coupon-create': '新增优惠券',
    users: '用户管理',
    permissions: '权限角色',
    logs: '操作日志',
    placeholder: placeholderTitle.value
  };
  return titles[page.value];
});

const displayError = computed(() => (error.value.startsWith('请求失败 (500)') ? '请求失败（500），请稍后重试' : error.value));

const filteredProducts = computed(() =>
  products.value.filter((item) => {
    const matchName = !productFilters.name || item.name.includes(productFilters.name);
    const matchCategory = !productFilters.categoryId || item.categoryId === Number(productFilters.categoryId);
    const matchStatus = !productFilters.saleStatus || item.saleStatus === productFilters.saleStatus;
    return matchName && matchCategory && matchStatus;
  })
);

const filteredOrders = computed(() =>
  orders.value.filter((item) => {
    const mobile = orderDetailsById.value[item.id]?.receiverMobile ?? '';
    return (
      (!orderFilters.orderNo || item.orderNo.includes(orderFilters.orderNo)) &&
      (!orderFilters.mobile || mobile.includes(orderFilters.mobile)) &&
      (!orderFilters.orderStatus || item.orderStatus === orderFilters.orderStatus)
    );
  })
);

const filteredAftersales = computed(() =>
  aftersales.value.filter((item) => {
    const text = `${item.aftersaleNo}${item.orderNo}${item.receiverMobile}${item.reason}`;
    return (!aftersaleFilters.keyword || text.includes(aftersaleFilters.keyword)) && (!aftersaleFilters.status || item.aftersaleStatus === aftersaleFilters.status);
  })
);

const filteredMembers = computed(() =>
  members.value.filter((item) => {
    const text = `${item.mobile}${item.nickname}`;
    return (!memberFilters.keyword || text.includes(memberFilters.keyword)) && (!memberFilters.status || item.status === memberFilters.status);
  })
);

const filteredCoupons = computed(() =>
  coupons.value.filter((item) => {
    const text = `${item.couponCode}${item.name}`;
    return (!couponFilters.keyword || text.includes(couponFilters.keyword)) && (!couponFilters.status || item.couponStatus === couponFilters.status);
  })
);

const filteredLogs = computed(() =>
  operationLogs.value.filter((item) => {
    const text = `${item.operatorName}${item.operationType}${item.bizType}${item.bizId ?? ''}${item.reason ?? ''}`;
    return (!logFilters.module || item.bizType === logFilters.module) && (!logFilters.keyword || text.includes(logFilters.keyword));
  })
);

const dashboard = computed(() => {
  const payAmount = orders.value.reduce((sum, item) => sum + item.payAmount, 0);
  return {
    orderCount: orders.value.length,
    payAmountText: formatCents(payAmount),
    waitShip: orders.value.filter((item) => item.orderStatus === 'WAIT_SHIP').length,
    refundApplying: aftersales.value.filter((item) => ['APPLYING', 'WAIT_RETURN', 'REFUNDING', 'REFUND_FAILED'].includes(item.aftersaleStatus)).length,
    couponEnabled: coupons.value.filter((item) => item.couponStatus === 'ENABLED').length,
    activeMembers: members.value.filter((item) => item.status === 'ACTIVE').length
  };
});

const canShipCurrentOrder = computed(() => currentOrder.value?.orderStatus === 'WAIT_SHIP' && currentOrder.value?.payStatus === 'PAID');
const canUpdateDeliveryCurrentOrder = computed(() => !!currentOrder.value && currentOrder.value.deliveryStatus !== 'UNSHIPPED' && !!currentOrder.value.deliveryTime);

function defaultSku(): ProductSku {
  return {
    skuName: '默认规格',
    specJson: '{"规格":"默认"}',
    salePrice: 9900,
    linePrice: 12900,
    stock: 100,
    skuStatus: 'ENABLED'
  };
}

function localDateTimeInput(offsetDays: number) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
}

function formatCents(cents: number) {
  return (cents / 100).toFixed(2).replace(/\.?0+$/, '');
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

function formatLogValue(value?: string | null) {
  if (!value) return '-';
  const compact = value.replace(/\s+/g, ' ').trim();
  return compact.length > 80 ? `${compact.slice(0, 80)}...` : compact;
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

async function login() {
  await runTask(async () => {
    const result = await loginAdmin(loginForm.username, loginForm.password);
    setAuthTokens(result.token, result.refreshToken);
    loggedIn.value = true;
    showToast(`欢迎回来，${result.name}`);
    await loadDashboard();
  });
}

async function changePassword() {
  if (!passwordForm.oldPassword.trim() || !passwordForm.newPassword.trim()) {
    showToast('请输入旧密码和新密码');
    return;
  }
  if (passwordForm.newPassword.trim() !== passwordForm.confirmPassword.trim()) {
    showToast('两次新密码不一致');
    return;
  }
  await runTask(async () => {
    const result = await changeAdminPassword(passwordForm.oldPassword.trim(), passwordForm.newPassword.trim());
    setAuthTokens(result.token, result.refreshToken);
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' });
    showPasswordPanel.value = false;
    showToast('密码已更新');
  });
}

function resetSession(message?: string) {
  loggedIn.value = false;
  clearAuthToken();
  products.value = [];
  orders.value = [];
  aftersales.value = [];
  members.value = [];
  coupons.value = [];
  roles.value = [];
  adminAccounts.value = [];
  operationLogs.value = [];
  inventoryReport.value = null;
  currentOrder.value = null;
  page.value = 'dashboard';
  if (message) showToast(message);
}

async function logout() {
  try {
    if (getAuthToken()) await logoutAdmin();
  } catch {
    // Local logout is still valid when the server session has already expired.
  }
  resetSession('已退出登录');
}

function handleAuthExpired() {
  resetSession('登录已过期，请重新登录');
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
  } else if (key === 'coupons') {
    page.value = 'coupons';
    loadCoupons();
  } else if (key === 'users') {
    page.value = 'users';
    loadMembers();
  } else if (key === 'permissions') {
    page.value = 'permissions';
    loadPermissions();
  } else if (key === 'inventory-reconciliation') {
    page.value = 'inventory-reconciliation';
    loadInventoryReconciliation();
  } else if (key === 'logs') {
    page.value = 'logs';
    loadLogs();
  } else {
    placeholderTitle.value = menu.find((item) => item.key === key)?.label ?? '模块';
    page.value = 'placeholder';
  }
}

async function loadDashboard() {
  await runTask(async () => {
    await Promise.all([loadProductsQuietly(), loadOrdersQuietly(), loadAftersalesQuietly(), loadMembersQuietly(), loadCouponsQuietly()]);
  });
}

async function loadProducts() {
  await runTask(async () => {
    await Promise.all([loadProductsQuietly(), loadCategoriesQuietly()]);
  });
}

async function loadOrders() {
  await runTask(loadOrdersQuietly);
}

async function loadAftersales() {
  await runTask(loadAftersalesQuietly);
}

async function loadMembers() {
  await runTask(loadMembersQuietly);
}

async function loadCoupons() {
  await runTask(loadCouponsQuietly);
}

async function loadPermissions() {
  await runTask(async () => {
    await Promise.all([loadRolesQuietly(), loadAdminAccountsQuietly()]);
  });
}

async function loadInventoryReconciliation() {
  await runTask(loadInventoryReconciliationQuietly);
}

async function loadLogs() {
  await runTask(loadLogsQuietly);
}

async function loadCategoriesQuietly() {
  categories.value = await getCategories();
  if (!productForm.categoryId && categories.value.length > 0) productForm.categoryId = categories.value[0].id;
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

async function loadMembersQuietly() {
  members.value = await getAdminMembers();
}

async function loadCouponsQuietly() {
  coupons.value = await getCoupons();
}

async function loadRolesQuietly() {
  roles.value = await getRoles();
}

async function loadAdminAccountsQuietly() {
  adminAccounts.value = await getAdminAccounts();
}

async function loadInventoryReconciliationQuietly() {
  inventoryReport.value = await getInventoryReconciliation(inventoryFilters.onlyDiff);
}

async function loadLogsQuietly() {
  operationLogs.value = await getOperationLogs();
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
    skus: [defaultSku()]
  });
}

function openCreateProduct() {
  resetProductForm();
  page.value = 'product-create';
}

async function openEditProduct(id: number) {
  page.value = 'product-edit';
  editingId.value = id;
  await runTask(async () => {
    await loadCategoriesQuietly();
    fillProductForm(await getProduct(id));
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
    skus: detail.skus.map((sku) => ({ ...sku }))
  });
}

function addSku() {
  productForm.skus.push({ ...defaultSku(), skuName: `规格${productForm.skus.length + 1}`, specJson: '{"规格":"新增"}' });
}

function removeSku(index: number) {
  if (productForm.skus.length === 1) {
    showToast('至少保留一个 SKU');
    return;
  }
  productForm.skus.splice(index, 1);
}

function productPayload(): ProductPayload {
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
      await updateProduct(editingId.value, productPayload());
      showToast('商品已保存');
    } else {
      await createProduct(productPayload());
      showToast('商品已创建');
    }
    page.value = 'products';
    await loadProductsQuietly();
  });
}

async function changeSaleStatus(id: number, status: 'ON_SALE' | 'OFF_SALE') {
  await runTask(async () => {
    if (status === 'ON_SALE') await onSaleProduct(id);
    else await offSaleProduct(id);
    await loadProductsQuietly();
    showToast(status === 'ON_SALE' ? '商品已上架' : '商品已下架');
  });
}

async function openOrderDetail(id: number) {
  page.value = 'order-detail';
  await runTask(async () => {
    currentOrder.value = await getOrder(id);
    fillDeliveryForms(currentOrder.value);
  });
}

function fillDeliveryForms(order: OrderDetail) {
  Object.assign(shippingForm, {
    logisticsCompany: order.logisticsCompany ?? '',
    logisticsNo: order.logisticsNo ?? '',
    deliveryRemark: order.deliveryRemark ?? ''
  });
  Object.assign(deliveryStatusForm, {
    deliveryStatus: order.deliveryStatus === 'DELIVERED' ? 'DELIVERED' : order.deliveryStatus === 'SHIPPED' ? 'SHIPPED' : 'IN_TRANSIT',
    deliveryRemark: order.deliveryRemark ?? ''
  });
}

async function submitShipment() {
  const order = currentOrder.value;
  if (!order) return;
  if (!shippingForm.logisticsCompany.trim() || !shippingForm.logisticsNo.trim()) {
    showToast('请输入物流公司和单号');
    return;
  }
  await runTask(async () => {
    currentOrder.value = await shipOrder(order.id, {
      logisticsCompany: shippingForm.logisticsCompany.trim(),
      logisticsNo: shippingForm.logisticsNo.trim(),
      deliveryRemark: shippingForm.deliveryRemark.trim() || undefined
    });
    fillDeliveryForms(currentOrder.value);
    await loadOrdersQuietly();
    showToast('发货成功');
  });
}

async function submitDeliveryStatus() {
  const order = currentOrder.value;
  if (!order) return;
  await runTask(async () => {
    currentOrder.value = await updateDeliveryStatus(order.id, {
      deliveryStatus: deliveryStatusForm.deliveryStatus,
      deliveryRemark: deliveryStatusForm.deliveryRemark.trim() || undefined
    });
    fillDeliveryForms(currentOrder.value);
    await loadOrdersQuietly();
    showToast('配送状态已更新');
  });
}

async function approveRefund(id: number) {
  await runTask(async () => {
    await approveAftersale(id);
    await Promise.all([loadAftersalesQuietly(), loadOrdersQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('售后已通过');
  });
}

async function confirmReturned(id: number) {
  await runTask(async () => {
    await confirmAftersaleReturned(id);
    await Promise.all([loadAftersalesQuietly(), loadOrdersQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('退货已确认，退款处理中');
  });
}

async function completeRefund(id: number) {
  await runTask(async () => {
    await completeAftersaleRefund(id);
    await Promise.all([loadAftersalesQuietly(), loadOrdersQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('退款已完成');
  });
}

async function failRefund(id: number) {
  const failureReason = window.prompt('请输入退款失败原因', '支付渠道退款失败，等待重试或人工处理')?.trim() || '支付渠道退款失败';
  await runTask(async () => {
    await failAftersaleRefund(id, failureReason);
    await Promise.all([loadAftersalesQuietly(), loadOrdersQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('退款已标记失败');
  });
}

async function retryRefund(id: number) {
  await runTask(async () => {
    await retryAftersaleRefund(id);
    await Promise.all([loadAftersalesQuietly(), loadOrdersQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('退款已重新发起');
  });
}

async function closeRefund(id: number) {
  await runTask(async () => {
    await closeAftersale(id);
    await Promise.all([loadAftersalesQuietly(), loadOrdersQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('售后单已关闭');
  });
}

async function rejectRefund(id: number) {
  const rejectReason = window.prompt('请输入拒绝原因', '凭证不足，售后申请被拒绝')?.trim() || '售后申请被拒绝';
  await runTask(async () => {
    await rejectAftersale(id, rejectReason);
    await Promise.all([loadAftersalesQuietly(), loadOrdersQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('售后已拒绝');
  });
}

function openCreateCoupon() {
  Object.assign(couponForm, {
    name: '',
    couponCode: '',
    couponType: 'FULL_REDUCTION',
    thresholdAmount: 100000,
    discountAmount: 10000,
    discountRate: undefined,
    totalQuantity: 100,
    receiveStartTime: localDateTimeInput(0),
    receiveEndTime: localDateTimeInput(30),
    useStartTime: localDateTimeInput(0),
    useEndTime: localDateTimeInput(60),
    couponStatus: 'ENABLED'
  });
  page.value = 'coupon-create';
}

async function saveCoupon() {
  if (!couponForm.name.trim()) {
    showToast('请输入优惠券名称');
    return;
  }
  await runTask(async () => {
    await createCoupon({
      ...couponForm,
      couponCode: couponForm.couponCode?.trim() || undefined,
      thresholdAmount: Number(couponForm.thresholdAmount),
      discountAmount: Number(couponForm.discountAmount),
      discountRate: couponForm.discountRate == null ? undefined : Number(couponForm.discountRate),
      totalQuantity: Number(couponForm.totalQuantity)
    });
    page.value = 'coupons';
    await Promise.all([loadCouponsQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('优惠券已创建');
  });
}

async function toggleCoupon(item: AdminCoupon) {
  await runTask(async () => {
    await updateCouponStatus(item.id, item.couponStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED');
    await Promise.all([loadCouponsQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('优惠券状态已更新');
  });
}

async function toggleMember(item: AdminMember) {
  await runTask(async () => {
    await updateMemberStatus(item.id, item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
    await Promise.all([loadMembersQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('用户状态已更新');
  });
}

async function toggleAdminAccount(item: AdminAccount) {
  await runTask(async () => {
    await updateAdminAccountStatus(item.id, item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
    await Promise.all([loadAdminAccountsQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('管理员状态已更新');
  });
}

async function changeAdminRole(account: AdminAccount, event: Event) {
  const roleId = Number((event.target as HTMLSelectElement).value);
  if (!roleId) return;
  await runTask(async () => {
    await assignAdminRole(account.id, roleId);
    await Promise.all([loadAdminAccountsQuietly(), loadLogsQuietly().catch(() => undefined)]);
    showToast('角色已分配');
  });
}

async function repairInventoryItem(skuId: number) {
  const reason = window.prompt('请输入修复原因', '库存对账修复 locked_stock')?.trim() || '库存对账修复 locked_stock';
  await runTask(async () => {
    await repairInventoryLockedStock(skuId, reason);
    await loadInventoryReconciliationQuietly();
    showToast('库存修复记录已保存');
  });
}

function statusText(status: string) {
  const map: Record<string, string> = {
    ACTIVE: '启用',
    ENABLED: '启用',
    DISABLED: '停用',
    ON_SALE: '上架',
    OFF_SALE: '下架',
    WAIT_PAY: '待支付',
    CANCELED: '已取消',
    WAIT_SHIP: '待发货',
    WAIT_RECEIVE: '待收货',
    FINISHED: '已完成',
    REFUNDED: '已退款',
    UNPAID: '未支付',
    PAID: '已支付',
    UNSHIPPED: '未发货',
    SHIPPED: '已发货',
    IN_TRANSIT: '运输中',
    DELIVERED: '已送达',
    NONE: '无售后',
    APPLYING: '待审核',
    APPROVED: '已审核',
    WAIT_RETURN: '待退货',
    RETURNED: '已退货',
    REFUNDING: '退款中',
    REFUND_FAILED: '退款失败',
    REJECTED: '已拒绝',
    CLOSED: '已关闭',
    FULL_REDUCTION: '满减券'
  };
  return map[status] ?? status;
}

function isActiveMenu(key: string) {
  return (
    page.value === key ||
    (key === 'products' && page.value.startsWith('product')) ||
    (key === 'orders' && page.value.startsWith('order')) ||
    (key === 'aftersale' && page.value === 'aftersales') ||
    (key === 'coupons' && page.value.startsWith('coupon')) ||
    (key === 'inventory-reconciliation' && page.value === 'inventory-reconciliation')
  );
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
        <h1>电商管理后台</h1>
        <p>商品、订单、售后与运营管理</p>
      </div>
      <form @submit.prevent="login">
        <label>账号<input v-model="loginForm.username" /></label>
        <label>密码<input v-model="loginForm.password" type="password" /></label>
        <button class="primary wide" type="submit">登录</button>
      </form>
    </section>
  </main>

  <main v-else class="admin-layout">
    <div v-if="toast" class="toast">{{ toast }}</div>
    <aside class="sidebar">
      <div class="brand">DWK Shop 后台</div>
      <nav>
        <button v-for="item in menu" :key="item.key" :class="{ active: isActiveMenu(item.key) }" type="button" @click="nav(item.key)">
          {{ item.label }}
        </button>
      </nav>
    </aside>

    <section class="content">
      <header class="topbar">
        <div>
          <h1>{{ pageTitle }}</h1>
          <span>真实接口驱动的运营闭环</span>
        </div>
        <div class="topbar-actions">
          <button class="ghost" type="button" @click="showPasswordPanel = !showPasswordPanel">修改密码</button>
          <button class="ghost" type="button" @click="logout">退出登录</button>
        </div>
      </header>

      <form v-if="showPasswordPanel" class="panel password-panel" @submit.prevent="changePassword">
        <label>旧密码<input v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" /></label>
        <label>新密码<input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" /></label>
        <label>确认密码<input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" /></label>
        <button class="primary" type="submit">保存密码</button>
      </form>

      <section v-if="page === 'dashboard'" class="page">
        <section class="metrics">
          <article><span>订单总数</span><strong>{{ dashboard.orderCount }}</strong></article>
          <article><span>支付金额</span><strong>¥{{ dashboard.payAmountText }}</strong></article>
          <article><span>待发货</span><strong>{{ dashboard.waitShip }}</strong></article>
          <article><span>待审售后</span><strong>{{ dashboard.refundApplying }}</strong></article>
          <article><span>启用优惠券</span><strong>{{ dashboard.couponEnabled }}</strong></article>
          <article><span>活跃用户</span><strong>{{ dashboard.activeMembers }}</strong></article>
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
              <button type="button" @click="openCreateProduct">新增商品</button>
              <button type="button" @click="nav('aftersale')">售后审核</button>
              <button type="button" @click="openCreateCoupon">新增优惠券</button>
              <button type="button" @click="nav('logs')">操作日志</button>
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
          <button class="primary" type="button" @click="openCreateProduct">新增商品</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead><tr><th>商品</th><th>分类</th><th>价格</th><th>库存</th><th>销量</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="product in filteredProducts" :key="product.id">
                <td><div class="product-cell"><div class="thumb">{{ product.name.slice(0, 1) }}</div><div><strong>{{ product.name }}</strong><span>{{ product.productCode }}</span></div></div></td>
                <td>{{ categories.find((item) => item.id === product.categoryId)?.name ?? '-' }}</td>
                <td>¥{{ product.minSalePriceText }}</td>
                <td>{{ product.stock }}</td>
                <td>{{ product.actualSales + product.virtualSales }}</td>
                <td><em :class="['status', product.saleStatus]">{{ statusText(product.saleStatus) }}</em></td>
                <td class="actions">
                  <button type="button" @click="openEditProduct(product.id)">编辑</button>
                  <button v-if="product.saleStatus !== 'ON_SALE'" type="button" @click="changeSaleStatus(product.id, 'ON_SALE')">上架</button>
                  <button v-else type="button" @click="changeSaleStatus(product.id, 'OFF_SALE')">下架</button>
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
          <label>销售状态<select v-model="productForm.saleStatus"><option value="OFF_SALE">下架</option><option value="ON_SALE">上架</option></select></label>
          <label>副标题<input v-model="productForm.subtitle" /></label>
          <label>配送<select v-model="productForm.deliveryType"><option value="NORMAL">普通</option><option value="COLD_CHAIN">冷链</option></select></label>
        </section>
        <section class="panel toggles">
          <label><input v-model="productForm.allowCart" type="checkbox" /> 允许加购</label>
          <label><input v-model="productForm.allowSingleBuy" type="checkbox" /> 允许单买</label>
          <label><input v-model="productForm.pointDeductEnabled" type="checkbox" /> 积分抵扣</label>
          <label><input v-model="productForm.pointRewardEnabled" type="checkbox" /> 返积分</label>
          <label>返积分<input v-model.number="productForm.pointReward" type="number" min="0" /></label>
        </section>
        <section class="panel">
          <div class="section-heading"><h2>SKU</h2><button class="ghost" type="button" @click="addSku">新增 SKU</button></div>
          <div v-for="(sku, index) in productForm.skus" :key="index" class="sku-editor">
            <label>SKU 名称<input v-model="sku.skuName" /></label>
            <label>规格 JSON<input v-model="sku.specJson" /></label>
            <label>价格分<input v-model.number="sku.salePrice" type="number" min="0" /></label>
            <label>库存<input v-model.number="sku.stock" type="number" min="0" /></label>
            <label>状态<select v-model="sku.skuStatus"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label>
            <button class="danger" type="button" @click="removeSku(index)">删除</button>
          </div>
        </section>
        <section class="panel form-grid">
          <label>购买须知标题<input v-model="productForm.noticeTitle" /></label>
          <label class="full">购买须知内容<textarea v-model="productForm.noticeContent"></textarea></label>
        </section>
        <div class="form-actions">
          <button class="ghost" type="button" @click="page = 'products'">取消</button>
          <button class="primary" type="button" @click="saveProduct">保存</button>
        </div>
      </section>

      <section v-else-if="page === 'orders'" class="page">
        <section class="panel filters">
          <input v-model="orderFilters.orderNo" placeholder="订单编号" />
          <input v-model="orderFilters.mobile" placeholder="手机号" />
          <select v-model="orderFilters.orderStatus">
            <option value="">全部状态</option>
            <option value="WAIT_PAY">待支付</option>
            <option value="WAIT_SHIP">待发货</option>
            <option value="WAIT_RECEIVE">待收货</option>
            <option value="FINISHED">已完成</option>
          </select>
          <button class="primary" type="button" @click="loadOrders">查询</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead><tr><th>订单编号</th><th>手机号</th><th>下单时间</th><th>金额</th><th>订单状态</th><th>支付状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="order in filteredOrders" :key="order.id">
                <td>{{ order.orderNo }}</td>
                <td>{{ orderDetailsById[order.id]?.receiverMobile ?? '-' }}</td>
                <td>{{ formatTime(order.createdAt) }}</td>
                <td>¥{{ order.payAmountText }}</td>
                <td><em class="status">{{ statusText(order.orderStatus) }}</em></td>
                <td>{{ statusText(order.payStatus) }}</td>
                <td class="actions"><button type="button" @click="openOrderDetail(order.id)">详情</button></td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredOrders.length === 0" class="empty">暂无订单</div>
        </section>
      </section>

      <section v-else-if="page === 'aftersales'" class="page">
        <section class="panel filters">
          <input v-model="aftersaleFilters.keyword" placeholder="售后号 / 订单号 / 手机号" />
          <select v-model="aftersaleFilters.status">
            <option value="">全部状态</option>
            <option value="APPLYING">待审核</option>
            <option value="WAIT_RETURN">待退货</option>
            <option value="REFUNDING">退款中</option>
            <option value="REFUND_FAILED">退款失败</option>
            <option value="REFUNDED">已退款</option>
            <option value="REJECTED">已拒绝</option>
            <option value="CLOSED">已关闭</option>
          </select>
          <button class="primary" type="button" @click="loadAftersales">刷新</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead><tr><th>售后编号</th><th>订单编号</th><th>手机号</th><th>退款金额</th><th>原因</th><th>状态</th><th>申请时间</th><th>审核结果</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in filteredAftersales" :key="item.id">
                <td>{{ item.aftersaleNo }}</td>
                <td>{{ item.orderNo }}</td>
                <td>{{ item.receiverMobile }}</td>
                <td>¥{{ item.refundAmountText }}</td>
                <td>{{ item.reason }}</td>
                <td><em class="status">{{ statusText(item.aftersaleStatus) }}</em></td>
                <td>{{ formatTime(item.applyTime) }}</td>
                <td>{{ item.rejectReason || (item.refundTime ? `退款于 ${formatTime(item.refundTime)}` : '-') }}</td>
                <td class="actions">
                  <button v-if="item.aftersaleStatus === 'APPLYING'" type="button" @click="approveRefund(item.id)">通过</button>
                  <button v-if="item.aftersaleStatus === 'APPLYING'" type="button" @click="rejectRefund(item.id)">拒绝</button>
                  <button v-if="item.aftersaleStatus === 'WAIT_RETURN'" type="button" @click="confirmReturned(item.id)">确认退货</button>
                  <button v-if="item.aftersaleStatus === 'REFUNDING'" type="button" @click="completeRefund(item.id)">完成退款</button>
                  <button v-if="item.aftersaleStatus === 'REFUNDING'" type="button" @click="failRefund(item.id)">标记失败</button>
                  <button v-if="item.aftersaleStatus === 'REFUND_FAILED'" type="button" @click="retryRefund(item.id)">重试退款</button>
                  <button v-if="item.aftersaleStatus === 'REJECTED' || item.aftersaleStatus === 'CANCELED'" type="button" @click="closeRefund(item.id)">关闭</button>
                  <button type="button" @click="openOrderDetail(item.orderId)">订单</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredAftersales.length === 0" class="empty">暂无售后申请</div>
        </section>
      </section>

      <section v-else-if="page === 'coupons'" class="page">
        <section class="panel filters">
          <input v-model="couponFilters.keyword" placeholder="券名称 / 券码" />
          <select v-model="couponFilters.status">
            <option value="">全部状态</option>
            <option value="ENABLED">启用</option>
            <option value="DISABLED">停用</option>
          </select>
          <button class="primary" type="button" @click="openCreateCoupon">新增优惠券</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead><tr><th>优惠券</th><th>门槛</th><th>优惠</th><th>库存</th><th>已领 / 已用</th><th>领取时间</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="coupon in filteredCoupons" :key="coupon.id">
                <td><strong>{{ coupon.name }}</strong><br /><span>{{ coupon.couponCode }}</span></td>
                <td>¥{{ coupon.thresholdAmountText }}</td>
                <td>¥{{ coupon.discountAmountText }}</td>
                <td>{{ coupon.totalQuantity }}</td>
                <td>{{ coupon.receivedQuantity }} / {{ coupon.usedQuantity }}</td>
                <td>{{ formatTime(coupon.receiveStartTime) }} - {{ formatTime(coupon.receiveEndTime) }}</td>
                <td><em class="status">{{ statusText(coupon.couponStatus) }}</em></td>
                <td class="actions"><button type="button" @click="toggleCoupon(coupon)">{{ coupon.couponStatus === 'ENABLED' ? '停用' : '启用' }}</button></td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredCoupons.length === 0" class="empty">暂无优惠券</div>
        </section>
      </section>

      <section v-else-if="page === 'coupon-create'" class="page form-page">
        <section class="panel form-grid">
          <label>优惠券名称<input v-model="couponForm.name" /></label>
          <label>券码<input v-model="couponForm.couponCode" placeholder="留空自动生成" /></label>
          <label>门槛金额（分）<input v-model.number="couponForm.thresholdAmount" type="number" min="0" /></label>
          <label>优惠金额（分）<input v-model.number="couponForm.discountAmount" type="number" min="0" /></label>
          <label>发放总量<input v-model.number="couponForm.totalQuantity" type="number" min="1" /></label>
          <label>状态<select v-model="couponForm.couponStatus"><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></label>
          <label>领取开始<input v-model="couponForm.receiveStartTime" type="datetime-local" /></label>
          <label>领取结束<input v-model="couponForm.receiveEndTime" type="datetime-local" /></label>
          <label>使用开始<input v-model="couponForm.useStartTime" type="datetime-local" /></label>
          <label>使用结束<input v-model="couponForm.useEndTime" type="datetime-local" /></label>
        </section>
        <div class="form-actions">
          <button class="ghost" type="button" @click="page = 'coupons'">取消</button>
          <button class="primary" type="button" @click="saveCoupon">保存</button>
        </div>
      </section>

      <section v-else-if="page === 'users'" class="page">
        <section class="panel filters">
          <input v-model="memberFilters.keyword" placeholder="手机号 / 昵称" />
          <select v-model="memberFilters.status">
            <option value="">全部状态</option>
            <option value="ACTIVE">启用</option>
            <option value="DISABLED">停用</option>
          </select>
          <button class="primary" type="button" @click="loadMembers">刷新</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead><tr><th>用户</th><th>手机号</th><th>积分</th><th>订单数</th><th>优惠券数</th><th>注册时间</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="member in filteredMembers" :key="member.id">
                <td>{{ member.nickname }}</td>
                <td>{{ member.mobile }}</td>
                <td>{{ member.availablePoints }} 可用 / {{ member.lockedPoints }} 锁定</td>
                <td>{{ member.orderCount }}</td>
                <td>{{ member.couponCount }}</td>
                <td>{{ formatTime(member.createdAt) }}</td>
                <td><em class="status">{{ statusText(member.status) }}</em></td>
                <td class="actions"><button type="button" @click="toggleMember(member)">{{ member.status === 'ACTIVE' ? '停用' : '启用' }}</button></td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredMembers.length === 0" class="empty">暂无用户</div>
        </section>
      </section>

      <section v-else-if="page === 'permissions'" class="page">
        <section class="dashboard-grid">
          <section class="panel table-panel">
            <div class="section-heading"><h2>角色</h2><button class="ghost" type="button" @click="loadPermissions">刷新</button></div>
            <table>
              <thead><tr><th>角色</th><th>编码</th><th>权限范围</th><th>状态</th></tr></thead>
              <tbody>
                <tr v-for="role in roles" :key="role.id">
                  <td>{{ role.roleName }}</td>
                  <td>{{ role.roleCode }}</td>
                  <td>{{ role.permissions }}</td>
                  <td><em class="status">{{ statusText(role.status) }}</em></td>
                </tr>
              </tbody>
            </table>
          </section>
          <section class="panel table-panel">
            <div class="section-heading"><h2>管理员</h2></div>
            <table>
              <thead><tr><th>账号</th><th>姓名</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="account in adminAccounts" :key="account.id">
                  <td>{{ account.username }}</td>
                  <td>{{ account.displayName }}</td>
                  <td>
                    <select :value="account.roleId" @change="changeAdminRole(account, $event)">
                      <option v-for="role in roles" :key="role.id" :value="role.id">{{ role.roleName }}</option>
                    </select>
                  </td>
                  <td><em class="status">{{ statusText(account.status) }}</em></td>
                  <td class="actions"><button type="button" @click="toggleAdminAccount(account)">{{ account.status === 'ACTIVE' ? '停用' : '启用' }}</button></td>
                </tr>
              </tbody>
            </table>
          </section>
        </section>
      </section>

      <section v-else-if="page === 'inventory-reconciliation'" class="page">
        <section class="panel filters">
          <label><input v-model="inventoryFilters.onlyDiff" type="checkbox" /> 只看差异</label>
          <button class="primary" type="button" @click="loadInventoryReconciliation">刷新对账</button>
        </section>
        <section class="dashboard-grid">
          <section v-for="check in inventoryReport?.checks ?? []" :key="check.checkType" class="panel info-list">
            <h2>{{ check.checkType }}</h2>
            <div><span>状态</span><strong>{{ check.status }}</strong></div>
            <div><span>数量</span><strong>{{ check.count }}</strong></div>
            <div><span>说明</span><strong>{{ check.message }}</strong></div>
          </section>
        </section>
        <section class="panel table-panel">
          <div class="section-heading">
            <h2>SKU 锁定库存对账</h2>
            <span>检查时间 {{ formatTime(inventoryReport?.checkedAt) }}</span>
          </div>
          <table>
            <thead><tr><th>SKU</th><th>当前库存</th><th>Projected locked</th><th>实际 locked</th><th>差异</th><th>关联订单</th><th>最近事件</th><th>修复记录</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in inventoryReport?.items ?? []" :key="item.skuId">
                <td><strong>{{ item.skuName }}</strong><br /><span>{{ item.productName }} / {{ item.skuCode }}</span></td>
                <td>{{ item.currentStock }}</td>
                <td>{{ item.projectedLockedStock }}</td>
                <td>{{ item.actualLockedStock }}</td>
                <td><em class="status">{{ item.difference }}</em></td>
                <td>
                  <div v-for="order in item.relatedOrders" :key="`${item.skuId}-${order.orderId}`">
                    #{{ order.orderNo || order.orderId }} {{ order.state }} x{{ order.quantity }}
                  </div>
                  <span v-if="item.relatedOrders.length === 0">-</span>
                </td>
                <td>
                  <div v-for="event in item.recentEvents" :key="`${item.skuId}-${event.eventId}`">
                    {{ event.eventType }} {{ formatTime(event.consumedAt) }}
                  </div>
                  <span v-if="item.recentEvents.length === 0">-</span>
                </td>
                <td>
                  <div v-for="record in item.repairRecords.slice(0, 2)" :key="record.id">
                    {{ record.repairStatus }} {{ formatTime(record.createdAt) }}
                  </div>
                  <span v-if="item.repairRecords.length === 0">-</span>
                </td>
                <td class="actions">
                  <button type="button" :disabled="!item.autoRepairAllowed" @click="repairInventoryItem(item.skuId)">自动修复</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="(inventoryReport?.items.length ?? 0) === 0" class="empty">暂无库存差异</div>
        </section>
      </section>

      <section v-else-if="page === 'logs'" class="page">
        <section class="panel filters">
          <select v-model="logFilters.module">
            <option value="">????</option>
            <option value="PRODUCT">??</option>
            <option value="SKU">SKU</option>
            <option value="ORDER">??</option>
            <option value="AFTERSALE">??</option>
            <option value="COUPON">???</option>
            <option value="USER">??</option>
            <option value="POINT">??</option>
            <option value="INVENTORY">??</option>
            <option value="PERMISSION">??</option>
          </select>
          <input v-model="logFilters.keyword" placeholder="??? / ?? / ??" />
          <button class="primary" type="button" @click="loadLogs">??</button>
        </section>
        <section class="panel table-panel">
          <table>
            <thead><tr><th>??</th><th>???</th><th>??</th><th>??</th><th>??</th><th>??</th><th>??</th><th>IP</th><th>User Agent</th></tr></thead>
            <tbody>
              <tr v-for="log in filteredLogs" :key="log.id">
                <td>{{ formatTime(log.createdAt) }}</td>
                <td>{{ log.operatorName }}</td>
                <td>{{ log.operationType }}</td>
                <td>{{ log.bizType }} #{{ log.bizId ?? '-' }}</td>
                <td :title="log.beforeValue || ''">{{ formatLogValue(log.beforeValue) }}</td>
                <td :title="log.afterValue || ''">{{ formatLogValue(log.afterValue) }}</td>
                <td>{{ log.reason }}</td>
                <td>{{ log.ip || '-' }}</td>
                <td :title="log.userAgent || ''">{{ formatLogValue(log.userAgent) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="filteredLogs.length === 0" class="empty">??????</div>
        </section>
      </section>

      <section v-else-if="page === 'order-detail' && currentOrder" class="page detail-grid">
        <section class="panel info-list">
          <h2>订单信息</h2>
          <div><span>订单编号</span><strong>{{ currentOrder.orderNo }}</strong></div>
          <div><span>订单状态</span><strong>{{ statusText(currentOrder.orderStatus) }}</strong></div>
          <div><span>售后状态</span><strong>{{ statusText(currentOrder.aftersaleStatus) }}</strong></div>
          <div><span>下单时间</span><strong>{{ formatTime(currentOrder.createdAt) }}</strong></div>
          <div><span>订单备注</span><strong>{{ currentOrder.remark || '-' }}</strong></div>
        </section>
        <section class="panel info-list">
          <h2>收货信息</h2>
          <div><span>收货人</span><strong>{{ currentOrder.receiverName }}</strong></div>
          <div><span>手机号</span><strong>{{ currentOrder.receiverMobile }}</strong></div>
          <div><span>收货地址</span><strong>{{ currentOrder.receiverAddress }}</strong></div>
        </section>
        <section class="panel table-panel full-row">
          <h2>商品明细</h2>
          <table>
            <thead><tr><th>商品</th><th>SKU</th><th>单价</th><th>数量</th><th>实付</th></tr></thead>
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
          <h2>金额信息</h2>
          <div><span>商品金额</span><strong>¥{{ currentOrder.amount.productAmountText }}</strong></div>
          <div><span>优惠券</span><strong>-¥{{ currentOrder.amount.couponDiscountAmountText }}</strong></div>
          <div><span>积分抵扣</span><strong>-¥{{ currentOrder.amount.pointDiscountAmountText }}</strong></div>
          <div><span>运费</span><strong>¥{{ currentOrder.amount.freightAmountText }}</strong></div>
          <div><span>实付金额</span><strong class="orange">¥{{ currentOrder.amount.payAmountText }}</strong></div>
        </section>
        <section class="panel info-list">
          <h2>物流信息</h2>
          <div><span>配送状态</span><strong>{{ statusText(currentOrder.deliveryStatus) }}</strong></div>
          <div><span>物流公司</span><strong>{{ currentOrder.logisticsCompany || '-' }}</strong></div>
          <div><span>物流单号</span><strong>{{ currentOrder.logisticsNo || '-' }}</strong></div>
          <div><span>发货时间</span><strong>{{ formatTime(currentOrder.deliveryTime) }}</strong></div>
        </section>
        <section class="panel full-row">
          <h2>订单发货</h2>
          <div class="form-grid">
            <label>物流公司<input v-model="shippingForm.logisticsCompany" :disabled="!canShipCurrentOrder" /></label>
            <label>物流单号<input v-model="shippingForm.logisticsNo" :disabled="!canShipCurrentOrder" /></label>
            <label class="full">发货备注<textarea v-model="shippingForm.deliveryRemark" :disabled="!canShipCurrentOrder"></textarea></label>
          </div>
          <div class="form-actions"><button class="primary" type="button" :disabled="!canShipCurrentOrder" @click="submitShipment">确认发货</button></div>
        </section>
        <section class="panel full-row">
          <h2>更新配送状态</h2>
          <div class="form-grid">
            <label>配送状态<select v-model="deliveryStatusForm.deliveryStatus" :disabled="!canUpdateDeliveryCurrentOrder"><option value="SHIPPED">已发货</option><option value="IN_TRANSIT">运输中</option><option value="DELIVERED">已送达</option></select></label>
            <label class="full">配送备注<textarea v-model="deliveryStatusForm.deliveryRemark" :disabled="!canUpdateDeliveryCurrentOrder"></textarea></label>
          </div>
          <div class="form-actions"><button class="primary" type="button" :disabled="!canUpdateDeliveryCurrentOrder" @click="submitDeliveryStatus">更新配送状态</button></div>
        </section>
      </section>

      <section v-else class="page">
        <section class="panel placeholder">
          <h2>{{ placeholderTitle }}</h2>
          <p>该模块已预留菜单，后续可接入结算、对账与财务报表。</p>
        </section>
      </section>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-if="error" class="error-box"><span>{{ displayError }}</span><button type="button" @click="error = ''">关闭</button></div>
    </section>
  </main>
</template>
