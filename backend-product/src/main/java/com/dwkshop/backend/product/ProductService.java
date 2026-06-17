package com.dwkshop.backend.product;

import com.dwkshop.backend.domain.entity.Product;
import com.dwkshop.backend.domain.entity.ProductCategory;
import com.dwkshop.backend.domain.entity.ProductNotice;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.ProductCategoryRepository;
import com.dwkshop.backend.domain.repository.ProductNoticeRepository;
import com.dwkshop.backend.domain.repository.ProductRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.product.dto.AdminProductResponse;
import com.dwkshop.backend.product.dto.CategoryResponse;
import com.dwkshop.backend.product.dto.ProductDetailResponse;
import com.dwkshop.backend.product.dto.ProductSkuRequest;
import com.dwkshop.backend.product.dto.ProductSkuResponse;
import com.dwkshop.backend.product.dto.ProductSkuSnapshotResponse;
import com.dwkshop.backend.product.dto.ProductSummaryResponse;
import com.dwkshop.backend.product.dto.ProductUpsertRequest;
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
    private final ProductSearchGateway productSearchGateway;

    public ProductService(
        ProductRepository productRepository,
        ProductSkuRepository productSkuRepository,
        ProductCategoryRepository productCategoryRepository,
        ProductNoticeRepository productNoticeRepository,
        ProductSearchGateway productSearchGateway
    ) {
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productNoticeRepository = productNoticeRepository;
        this.productSearchGateway = productSearchGateway;
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
        return new ProductSkuSnapshotResponse(
            product.getId(),
            sku.getId(),
            product.getName(),
            product.getMainImageUrl(),
            product.getSaleStatus(),
            product.getDeletedFlag(),
            product.getAllowCart(),
            product.getAllowSingleBuy(),
            product.getSupportPointDeduction(),
            sku.getSkuName(),
            sku.getSpecJson(),
            sku.getSalePrice(),
            sku.getStock(),
            sku.getSkuStatus()
        );
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
        product.setSubtitle(request.subtitle());
        product.setMainImageUrl(request.mainImageUrl());
        product.setProductType(blankToDefault(request.productType(), NORMAL));
        product.setSaleStatus(blankToDefault(request.saleStatus(), OFF_SALE));
        product.setDeliveryType(blankToDefault(request.deliveryType(), NORMAL));
        product.setAllowCart(defaultBool(request.allowCart(), true));
        product.setAllowSingleBuy(defaultBool(request.allowSingleBuy(), true));
        product.setSupportPointDeduction(defaultBool(request.pointDeductEnabled(), false));
        product.setSupportPointReward(defaultBool(request.pointRewardEnabled(), false));
        product.setPointReward(defaultInt(request.pointReward(), 0));
        product.setVirtualSales(defaultInt(request.virtualSales(), 0));
        if (creating) {
            product.setActualSales(0);
            product.setDeletedFlag(false);
            product.setCreatedAt(now);
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
            product.getSubtitle(),
            product.getMainImageUrl(),
            product.getProductType(),
            product.getSaleStatus(),
            product.getDeliveryType(),
            product.getAllowCart(),
            product.getAllowSingleBuy(),
            product.getSupportPointDeduction(),
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
