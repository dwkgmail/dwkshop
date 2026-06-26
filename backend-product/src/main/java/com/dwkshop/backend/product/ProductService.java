package com.dwkshop.backend.product;

import com.dwkshop.backend.domain.entity.Product;
import com.dwkshop.backend.domain.entity.ProductCategory;
import com.dwkshop.backend.domain.entity.ProductNotice;
import com.dwkshop.backend.domain.entity.ProductRefundCommand;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.ProductCategoryRepository;
import com.dwkshop.backend.domain.repository.ProductNoticeRepository;
import com.dwkshop.backend.domain.repository.ProductRepository;
import com.dwkshop.backend.domain.repository.ProductRefundCommandRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.product.dto.AdminProductResponse;
import com.dwkshop.backend.product.dto.CategoryResponse;
import com.dwkshop.backend.product.dto.LockSkuStockResponse;
import com.dwkshop.backend.product.dto.ProductDetailResponse;
import com.dwkshop.backend.product.dto.ProductSkuRequest;
import com.dwkshop.backend.product.dto.ProductSkuResponse;
import com.dwkshop.backend.product.dto.ProductSkuSnapshotResponse;
import com.dwkshop.backend.product.dto.ProductSummaryResponse;
import com.dwkshop.backend.product.dto.ProductUpsertRequest;
import com.dwkshop.backend.product.dto.RefundStockItemRequest;
import com.dwkshop.backend.product.dto.RefundStockItemResponse;
import com.dwkshop.backend.product.dto.RefundStockRequest;
import com.dwkshop.backend.product.dto.RefundStockResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dwkshop.backend.util.PriceFormatter;
import com.dwkshop.backend.search.ProductSearchGateway;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

    private static final String ON_SALE = "ON_SALE";
    private static final String OFF_SALE = "OFF_SALE";
    private static final String NORMAL = "NORMAL";
    private static final String ENABLED = "ENABLED";

    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductNoticeRepository productNoticeRepository;
    private final ProductRefundCommandRepository productRefundCommandRepository;
    private final ProductSearchGateway productSearchGateway;
    private final ObjectMapper objectMapper;

    public ProductService(
        ProductRepository productRepository,
        ProductSkuRepository productSkuRepository,
        ProductCategoryRepository productCategoryRepository,
        ProductNoticeRepository productNoticeRepository,
        ProductRefundCommandRepository productRefundCommandRepository,
        ProductSearchGateway productSearchGateway,
        ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productNoticeRepository = productNoticeRepository;
        this.productRefundCommandRepository = productRefundCommandRepository;
        this.productSearchGateway = productSearchGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> listProducts(Long categoryId) {
        List<Product> products = categoryId == null
            ? productRepository.findByDeletedFlagFalseAndSaleStatusOrderByIdDesc(ON_SALE)
            : productRepository.findByDeletedFlagFalseAndSaleStatusAndCategoryIdOrderByIdDesc(ON_SALE, categoryId);
        return toSummaries(products);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> searchProducts(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return List.of();
        }
        return productSearchGateway.searchOnSaleProductIds(normalizedKeyword)
            .map(this::loadProductsBySearchOrder)
            .map(this::toSummaries)
            .orElseGet(() -> toSummaries(
                productRepository.findByDeletedFlagFalseAndSaleStatusAndNameContainingOrderByIdDesc(ON_SALE, normalizedKeyword)
            ));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetail(Long id) {
        Product product = productRepository.findById(id)
            .filter(item -> !Boolean.TRUE.equals(item.getDeletedFlag()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        List<ProductSku> skus = productSkuRepository.findByProductId(id);
        ProductNotice notice = productNoticeRepository.findByProductIdAndEnabledFlagTrue(id).orElse(null);
        boolean offSale = !ON_SALE.equals(product.getSaleStatus());
        return toDetail(product, skus, notice, offSale);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return productCategoryRepository.findByStatusOrderBySortOrderAscIdAsc(ENABLED)
            .stream()
            .map(this::toCategory)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProductSkuSnapshotResponse getSkuSnapshot(Long skuId) {
        ProductSku sku = productSkuRepository.findById(skuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKU does not exist"));
        Product product = productRepository.findById(sku.getProductId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product does not exist"));
        ProductNotice notice = productNoticeRepository.findByProductIdAndEnabledFlagTrue(product.getId()).orElse(null);
        return new ProductSkuSnapshotResponse(
            product.getId(),
            sku.getId(),
            product.getCategoryId(),
            product.getName(),
            product.getBrandName(),
            product.getMainImageUrl(),
            product.getSaleStatus(),
            product.getDeliveryType(),
            product.getDeletedFlag(),
            product.getAllowCart(),
            product.getAllowSingleBuy(),
            product.getSupportPointDeduction(),
            product.getSupportRefund(),
            product.getSnapshotVersion(),
            notice == null ? null : notice.getNoticeTitle(),
            notice == null ? null : notice.getNoticeContent(),
            sku.getSkuName(),
            sku.getSpecJson(),
            sku.getSalePrice(),
            sku.getStock(),
            sku.getSkuStatus()
        );
    }

    @Transactional
    public LockSkuStockResponse lockSkuStock(Long skuId, int quantity) {
        ProductSku sku = productSkuRepository.findByIdForUpdate(skuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品规格已失效"));
        if (!ENABLED.equals(sku.getSkuStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分商品规格已失效，请重新选择");
        }
        if (sku.getStock() < quantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足，请修改购买数量");
        }
        sku.setStock(sku.getStock() - quantity);
        sku.setLockedStock(sku.getLockedStock() + quantity);
        sku.setUpdatedAt(LocalDateTime.now());
        ProductSku saved = productSkuRepository.save(sku);
        return new LockSkuStockResponse(
            saved.getId(),
            saved.getSkuName(),
            saved.getSalePrice(),
            saved.getStock(),
            saved.getLockedStock(),
            saved.getSkuStatus()
        );
    }

    @Transactional
    public LockSkuStockResponse releaseSkuStock(Long skuId, int quantity) {
        ProductSku sku = productSkuRepository.findByIdForUpdate(skuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品规格不存在"));
        if (quantity <= 0 || sku.getLockedStock() < quantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "释放的锁定库存数量无效");
        }
        sku.setLockedStock(sku.getLockedStock() - quantity);
        sku.setStock(sku.getStock() + quantity);
        sku.setUpdatedAt(LocalDateTime.now());
        ProductSku saved = productSkuRepository.save(sku);
        return new LockSkuStockResponse(
            saved.getId(),
            saved.getSkuName(),
            saved.getSalePrice(),
            saved.getStock(),
            saved.getLockedStock(),
            saved.getSkuStatus()
        );
    }

    @Transactional
    public RefundStockResponse releaseRefundStock(RefundStockRequest request) {
        return executeRefundCommand(request, "RELEASE");
    }

    @Transactional
    public RefundStockResponse restoreRefundStock(RefundStockRequest request) {
        return executeRefundCommand(request, "RESTORE");
    }

    @Transactional(readOnly = true)
    public List<AdminProductResponse> listAdminProducts() {
        return toAdminProducts(productRepository.findByDeletedFlagFalseOrderByIdDesc());
    }

    @Transactional
    public ProductDetailResponse createProduct(ProductUpsertRequest request) {
        ensureCategoryExists(request.categoryId());
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product();
        // 商品主信息、SKU 和购买须知分开保存，便于后台独立维护。
        applyProductRequest(product, request, now, true);
        Product saved = productRepository.save(product);
        List<ProductSku> skus = saveSkus(saved.getId(), request.skus(), now);
        ProductNotice notice = saveNotice(saved.getId(), request.noticeTitle(), request.noticeContent(), now);
        productSearchGateway.indexProduct(saved);
        return toDetail(saved, skus, notice, !ON_SALE.equals(saved.getSaleStatus()));
    }

    @Transactional
    public ProductDetailResponse updateProduct(Long id, ProductUpsertRequest request) {
        ensureCategoryExists(request.categoryId());
        Product product = productRepository.findById(id)
            .filter(item -> !Boolean.TRUE.equals(item.getDeletedFlag()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        LocalDateTime now = LocalDateTime.now();
        applyProductRequest(product, request, now, false);
        Product saved = productRepository.save(product);
        productSkuRepository.deleteByProductId(saved.getId());
        List<ProductSku> skus = saveSkus(saved.getId(), request.skus(), now);
        ProductNotice notice = saveNotice(saved.getId(), request.noticeTitle(), request.noticeContent(), now);
        productSearchGateway.indexProduct(saved);
        return toDetail(saved, skus, notice, !ON_SALE.equals(saved.getSaleStatus()));
    }

    @Transactional
    public ProductDetailResponse onSale(Long id) {
        return changeSaleStatus(id, ON_SALE);
    }

    @Transactional
    public ProductDetailResponse offSale(Long id) {
        return changeSaleStatus(id, OFF_SALE);
    }

    private ProductDetailResponse changeSaleStatus(Long id, String saleStatus) {
        Product product = productRepository.findById(id)
            .filter(item -> !Boolean.TRUE.equals(item.getDeletedFlag()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在"));
        product.setSaleStatus(saleStatus);
        product.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(product);
        List<ProductSku> skus = productSkuRepository.findByProductId(saved.getId());
        ProductNotice notice = productNoticeRepository.findByProductIdAndEnabledFlagTrue(saved.getId()).orElse(null);
        productSearchGateway.indexProduct(saved);
        return toDetail(saved, skus, notice, !ON_SALE.equals(saved.getSaleStatus()));
    }

    private List<Product> loadProductsBySearchOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> rankMap = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            rankMap.put(ids.get(i), i);
        }
        return productRepository.findByIdIn(ids).stream()
            .filter(product -> !Boolean.TRUE.equals(product.getDeletedFlag()) && ON_SALE.equals(product.getSaleStatus()))
            .sorted(Comparator.comparingInt(product -> rankMap.getOrDefault(product.getId(), Integer.MAX_VALUE)))
            .toList();
    }

    private void applyProductRequest(Product product, ProductUpsertRequest request, LocalDateTime now, boolean creating) {
        // 对请求字段统一做默认值兜底，避免状态类字段出现 null。
        product.setCategoryId(request.categoryId());
        product.setProductCode(blankToDefault(request.productCode(), "P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        product.setName(request.name().trim());
        product.setBrandName(blankToDefault(request.brandName(), ""));
        product.setSubtitle(request.subtitle());
        product.setMainImageUrl(request.mainImageUrl());
        product.setProductType(blankToDefault(request.productType(), NORMAL));
        product.setSaleStatus(blankToDefault(request.saleStatus(), OFF_SALE));
        product.setDeliveryType(blankToDefault(request.deliveryType(), NORMAL));
        product.setAllowCart(defaultBool(request.allowCart(), true));
        product.setAllowSingleBuy(defaultBool(request.allowSingleBuy(), true));
        product.setSupportPointDeduction(defaultBool(request.pointDeductEnabled(), false));
        product.setSupportRefund(defaultBool(request.supportRefund(), true));
        product.setSupportPointReward(defaultBool(request.pointRewardEnabled(), false));
        product.setPointReward(defaultInt(request.pointReward(), 0));
        product.setVirtualSales(defaultInt(request.virtualSales(), 0));
        if (creating) {
            product.setSnapshotVersion(1);
            product.setActualSales(0);
            product.setDeletedFlag(false);
            product.setCreatedAt(now);
        } else {
            product.setSnapshotVersion(defaultInt(product.getSnapshotVersion(), 1) + 1);
        }
        product.setUpdatedAt(now);
    }

    private List<ProductSku> saveSkus(Long productId, List<ProductSkuRequest> requests, LocalDateTime now) {
        return requests.stream().map(request -> {
            // 每次提交都按请求内容重建 SKU，保证数据库状态和后台表单一致。
            ProductSku sku = new ProductSku();
            sku.setProductId(productId);
            sku.setSkuCode(blankToDefault(request.skuCode(), "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
            sku.setSkuName(request.skuName().trim());
            sku.setSpecJson(request.specJson());
            sku.setImageUrl(request.imageUrl());
            sku.setSalePrice(request.salePrice());
            sku.setLinePrice(request.linePrice());
            sku.setStock(request.stock());
            sku.setLockedStock(0);
            sku.setSkuStatus(blankToDefault(request.skuStatus(), ENABLED));
            sku.setCreatedAt(now);
            sku.setUpdatedAt(now);
            return productSkuRepository.save(sku);
        }).toList();
    }

    private ProductNotice saveNotice(Long productId, String title, String content, LocalDateTime now) {
        ProductNotice notice = productNoticeRepository.findByProductIdAndEnabledFlagTrue(productId).orElse(null);
        if (isBlank(content)) {
            // 购买须知清空时不直接删记录，而是关闭启用标记保留历史。
            if (notice != null) {
                notice.setEnabledFlag(false);
                notice.setUpdatedAt(now);
                productNoticeRepository.save(notice);
            }
            return null;
        }
        ProductNotice target = notice == null ? new ProductNotice() : notice;
        target.setProductId(productId);
        target.setNoticeTitle(blankToDefault(title, "用户购买须知"));
        target.setNoticeContent(content);
        target.setEnabledFlag(true);
        if (target.getCreatedAt() == null) {
            target.setCreatedAt(now);
        }
        target.setUpdatedAt(now);
        return productNoticeRepository.save(target);
    }

    private List<ProductSummaryResponse> toSummaries(List<Product> products) {
        Map<Long, List<ProductSku>> skuMap = loadSkuMap(products);
        return products.stream()
            .map(product -> toSummary(product, skuMap.getOrDefault(product.getId(), List.of())))
            .toList();
    }

    private List<AdminProductResponse> toAdminProducts(List<Product> products) {
        Map<Long, List<ProductSku>> skuMap = loadSkuMap(products);
        return products.stream()
            .map(product -> toAdminProduct(product, skuMap.getOrDefault(product.getId(), List.of())))
            .toList();
    }

    private Map<Long, List<ProductSku>> loadSkuMap(List<Product> products) {
        List<Long> productIds = products.stream().map(Product::getId).toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        // 列表场景批量预加载 SKU，避免 DTO 转换时出现 N+1 查询。
        return productSkuRepository.findByProductIdIn(productIds).stream()
            .collect(Collectors.groupingBy(ProductSku::getProductId));
    }

    private ProductSummaryResponse toSummary(Product product, List<ProductSku> skus) {
        Integer minPrice = minSalePrice(skus);
        return new ProductSummaryResponse(
            product.getId(),
            product.getCategoryId(),
            product.getProductCode(),
            product.getName(),
            product.getSubtitle(),
            product.getMainImageUrl(),
            product.getSaleStatus(),
            product.getDeliveryType(),
            product.getAllowCart(),
            product.getAllowSingleBuy(),
            product.getSupportPointDeduction(),
            minPrice,
            PriceFormatter.formatCents(minPrice),
            product.getActualSales() + product.getVirtualSales()
        );
    }

    private ProductDetailResponse toDetail(Product product, List<ProductSku> skus, ProductNotice notice, boolean offSale) {
        return new ProductDetailResponse(
            product.getId(),
            product.getCategoryId(),
            product.getProductCode(),
            product.getName(),
            product.getBrandName(),
            product.getSubtitle(),
            product.getMainImageUrl(),
            product.getProductType(),
            product.getSaleStatus(),
            offSale,
            offSale ? "商品已下架" : null,
            product.getDeliveryType(),
            product.getAllowCart(),
            product.getAllowSingleBuy(),
            product.getSupportPointDeduction(),
            product.getSupportRefund(),
            product.getSupportPointReward(),
            product.getPointReward(),
            product.getActualSales() + product.getVirtualSales(),
            notice == null ? null : notice.getNoticeTitle(),
            notice == null ? null : notice.getNoticeContent(),
            skus.stream().map(this::toSku).toList()
        );
    }

    private AdminProductResponse toAdminProduct(Product product, List<ProductSku> skus) {
        Integer minPrice = minSalePrice(skus);
        Integer stock = skus.stream().map(ProductSku::getStock).reduce(0, Integer::sum);
        return new AdminProductResponse(
            product.getId(),
            product.getCategoryId(),
            product.getProductCode(),
            product.getName(),
            product.getBrandName(),
            product.getSubtitle(),
            product.getMainImageUrl(),
            product.getProductType(),
            product.getSaleStatus(),
            product.getDeliveryType(),
            product.getAllowCart(),
            product.getAllowSingleBuy(),
            product.getSupportPointDeduction(),
            product.getSupportRefund(),
            product.getSupportPointReward(),
            product.getPointReward(),
            product.getVirtualSales(),
            product.getActualSales(),
            minPrice,
            PriceFormatter.formatCents(minPrice),
            stock
        );
    }

    private ProductSkuResponse toSku(ProductSku sku) {
        boolean selectable = ENABLED.equals(sku.getSkuStatus()) && sku.getStock() > 0;
        return new ProductSkuResponse(
            sku.getId(),
            sku.getSkuCode(),
            sku.getSkuName(),
            sku.getSpecJson(),
            sku.getImageUrl(),
            sku.getSalePrice(),
            PriceFormatter.formatCents(sku.getSalePrice()),
            sku.getLinePrice(),
            PriceFormatter.formatCents(sku.getLinePrice()),
            sku.getStock(),
            sku.getLockedStock(),
            sku.getSkuStatus(),
            selectable
        );
    }

    private CategoryResponse toCategory(ProductCategory category) {
        return new CategoryResponse(
            category.getId(),
            category.getParentId(),
            category.getName(),
            category.getLevel(),
            category.getSortOrder(),
            category.getStatus()
        );
    }

    private RefundStockResponse executeRefundCommand(RefundStockRequest request, String expectedType) {
        if (!expectedType.equals(request.commandType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported refund command type");
        }
        ProductRefundCommand existing = productRefundCommandRepository.findByCommandNo(request.commandNo()).orElse(null);
        if (existing != null) {
            if ("DONE".equals(existing.getCommandStatus())) {
                return readRefundResponse(existing);
            }
            if ("PROCESSING".equals(existing.getCommandStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund command is processing");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ProductRefundCommand command = existing == null ? new ProductRefundCommand() : existing;
        command.setCommandNo(request.commandNo());
        command.setCommandType(request.commandType());
        command.setCommandStatus("PROCESSING");
        command.setPayloadJson(writeValue(request));
        command.setUpdatedAt(now);
        if (command.getCreatedAt() == null) {
            command.setCreatedAt(now);
        }
        productRefundCommandRepository.save(command);

        try {
            List<RefundStockItemResponse> resultItems = switch (expectedType) {
                case "RELEASE" -> applyRelease(request.items());
                case "RESTORE" -> applyRestore(request.items());
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported refund command type");
            };
            RefundStockResponse response = new RefundStockResponse(request.commandNo(), request.commandType(), "DONE", resultItems);
            command.setCommandStatus("DONE");
            command.setResultJson(writeValue(response));
            command.setLastError(null);
            command.setUpdatedAt(LocalDateTime.now());
            productRefundCommandRepository.save(command);
            return response;
        } catch (RuntimeException ex) {
            command.setCommandStatus("FAILED");
            command.setLastError(trimError(ex.getMessage()));
            command.setUpdatedAt(LocalDateTime.now());
            productRefundCommandRepository.save(command);
            throw ex;
        }
    }

    private List<RefundStockItemResponse> applyRelease(List<RefundStockItemRequest> items) {
        return items.stream().map(item -> {
            ProductSku sku = productSkuRepository.findByIdForUpdate(item.skuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品规格不存在"));
            if (!ENABLED.equals(sku.getSkuStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分商品规格已失效，请重新选择");
            }
            if (sku.getLockedStock() < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "锁定库存不足，无法执行退款释放");
            }
            sku.setLockedStock(sku.getLockedStock() - item.quantity());
            sku.setStock(sku.getStock() + item.quantity());
            sku.setUpdatedAt(LocalDateTime.now());
            ProductSku saved = productSkuRepository.save(sku);
            return new RefundStockItemResponse(
                saved.getId(),
                saved.getSkuName(),
                saved.getStock(),
                saved.getLockedStock(),
                saved.getSkuStatus(),
                item.quantity(),
                item.quantity(),
                -item.quantity()
            );
        }).toList();
    }

    private List<RefundStockItemResponse> applyRestore(List<RefundStockItemRequest> items) {
        return items.stream().map(item -> {
            ProductSku sku = productSkuRepository.findByIdForUpdate(item.skuId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品规格不存在"));
            if (!ENABLED.equals(sku.getSkuStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部分商品规格已失效，请重新选择");
            }
            if (sku.getStock() < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "可用库存不足，无法补偿退款释放");
            }
            sku.setStock(sku.getStock() - item.quantity());
            sku.setLockedStock(sku.getLockedStock() + item.quantity());
            sku.setUpdatedAt(LocalDateTime.now());
            ProductSku saved = productSkuRepository.save(sku);
            return new RefundStockItemResponse(
                saved.getId(),
                saved.getSkuName(),
                saved.getStock(),
                saved.getLockedStock(),
                saved.getSkuStatus(),
                item.quantity(),
                -item.quantity(),
                item.quantity()
            );
        }).toList();
    }

    private RefundStockResponse readRefundResponse(ProductRefundCommand command) {
        try {
            return objectMapper.readValue(command.getResultJson(), RefundStockResponse.class);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Refund command cache is corrupted", ex);
        }
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize refund command", ex);
        }
    }

    private String trimError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private Integer minSalePrice(List<ProductSku> skus) {
        return skus.stream()
            .min(Comparator.comparing(ProductSku::getSalePrice))
            .map(ProductSku::getSalePrice)
            .orElse(null);
    }

    private void ensureCategoryExists(Long categoryId) {
        if (!productCategoryRepository.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品分类不存在");
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Boolean defaultBool(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Integer defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
