package com.dwkshop.backend;

import com.dwkshop.backend.domain.entity.Product;
import com.dwkshop.backend.domain.entity.ProductNotice;
import com.dwkshop.backend.domain.entity.ProductRefundCommand;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.ProductNoticeRepository;
import com.dwkshop.backend.domain.repository.ProductRefundCommandRepository;
import com.dwkshop.backend.domain.repository.ProductRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.auth.InternalServiceAuthConfig;
import com.dwkshop.backend.search.ProductSearchGateway;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProductInternalApiIntegrationTest {

    private static final String INTERNAL_SECRET = "dwkshop-local-internal-secret-change-me";

    private final MockMvc mockMvc;
    private final ProductRepository productRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductNoticeRepository productNoticeRepository;
    private final ProductRefundCommandRepository productRefundCommandRepository;

    @MockBean
    private ProductSearchGateway productSearchGateway;

    @Autowired
    ProductInternalApiIntegrationTest(
        MockMvc mockMvc,
        ProductRepository productRepository,
        ProductSkuRepository productSkuRepository,
        ProductNoticeRepository productNoticeRepository,
        ProductRefundCommandRepository productRefundCommandRepository
    ) {
        this.mockMvc = mockMvc;
        this.productRepository = productRepository;
        this.productSkuRepository = productSkuRepository;
        this.productNoticeRepository = productNoticeRepository;
        this.productRefundCommandRepository = productRefundCommandRepository;
    }

    @BeforeEach
    void resetState() {
        productRefundCommandRepository.deleteAllInBatch();
        productNoticeRepository.deleteAllInBatch();
        productSkuRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
    }

    @Test
    void internalSnapshotAndStockCommandsMatchContract() throws Exception {
        SeededProduct seededProduct = seedProduct("Product A", "SKU-A", 120, 10, 2, true, true);

        mockMvc.perform(get("/internal/products/skus/{skuId}/snapshot", seededProduct.skuId())
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productId").value(seededProduct.productId()))
            .andExpect(jsonPath("$.skuId").value(seededProduct.skuId()))
            .andExpect(jsonPath("$.productName").value("Product A"))
            .andExpect(jsonPath("$.noticeTitle").value("Buyer Guide"))
            .andExpect(jsonPath("$.saleStatus").value("ON_SALE"))
            .andExpect(jsonPath("$.skuStatus").value("ENABLED"))
            .andExpect(jsonPath("$.allowCart").value(true))
            .andExpect(jsonPath("$.allowSingleBuy").value(true))
            .andExpect(jsonPath("$.stock").value(10));

        mockMvc.perform(post("/internal/products/skus/{skuId}/stock-locks", seededProduct.skuId())
                .contentType(APPLICATION_JSON)
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET)
                .content("{\"quantity\":3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stock").value(7))
            .andExpect(jsonPath("$.lockedStock").value(5));

        mockMvc.perform(post("/internal/products/skus/{skuId}/stock-releases", seededProduct.skuId())
                .contentType(APPLICATION_JSON)
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET)
                .content("{\"quantity\":2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stock").value(9))
            .andExpect(jsonPath("$.lockedStock").value(3));
    }

    @Test
    void refundCommandsAreIdempotentPerCommandNo() throws Exception {
        SeededProduct seededProduct = seedProduct("Product B", "SKU-B", 8800, 10, 4, true, false);

        String releasePayload = """
            {
              "commandNo": "refund-release-001",
              "commandType": "RELEASE",
              "items": [
                {
                  "skuId": %d,
                  "quantity": 2
                }
              ]
            }
            """.formatted(seededProduct.skuId());

        mockMvc.perform(post("/internal/products/refunds/release")
                .contentType(APPLICATION_JSON)
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET)
                .content(releasePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commandNo").value("refund-release-001"))
            .andExpect(jsonPath("$.commandStatus").value("DONE"))
            .andExpect(jsonPath("$.items[0].stock").value(12))
            .andExpect(jsonPath("$.items[0].lockedStock").value(2));

        mockMvc.perform(post("/internal/products/refunds/release")
                .contentType(APPLICATION_JSON)
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET)
                .content(releasePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commandStatus").value("DONE"))
            .andExpect(jsonPath("$.items[0].stock").value(12))
            .andExpect(jsonPath("$.items[0].lockedStock").value(2));

        String restorePayload = """
            {
              "commandNo": "refund-restore-001",
              "commandType": "RESTORE",
              "items": [
                {
                  "skuId": %d,
                  "quantity": 1
                }
              ]
            }
            """.formatted(seededProduct.skuId());

        mockMvc.perform(post("/internal/products/refunds/restore")
                .contentType(APPLICATION_JSON)
                .header(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, INTERNAL_SECRET)
                .content(restorePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commandNo").value("refund-restore-001"))
            .andExpect(jsonPath("$.items[0].stock").value(11))
            .andExpect(jsonPath("$.items[0].lockedStock").value(3));

        ProductSku sku = productSkuRepository.findById(seededProduct.skuId()).orElseThrow();
        assertThat(sku.getStock()).isEqualTo(11);
        assertThat(sku.getLockedStock()).isEqualTo(3);

        List<ProductRefundCommand> commands = productRefundCommandRepository.findAll();
        assertThat(commands).hasSize(2);
    }

    private SeededProduct seedProduct(
        String productName,
        String skuName,
        int salePrice,
        int stock,
        int lockedStock,
        boolean allowCart,
        boolean allowSingleBuy
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 18, 10, 0);

        Product product = new Product();
        product.setCategoryId(1L);
        product.setProductCode("P-" + productName.replace(" ", "-").toUpperCase());
        product.setName(productName);
        product.setSubtitle("Subtitle");
        product.setMainImageUrl("/images/product-a.png");
        product.setProductType("NORMAL");
        product.setSaleStatus("ON_SALE");
        product.setDeliveryType("NORMAL");
        product.setAllowCart(allowCart);
        product.setAllowSingleBuy(allowSingleBuy);
        product.setSupportPointDeduction(true);
        product.setSupportPointReward(false);
        product.setPointReward(0);
        product.setVirtualSales(0);
        product.setActualSales(0);
        product.setDeletedFlag(false);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        Product savedProduct = productRepository.save(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(savedProduct.getId());
        sku.setSkuCode("SKU-" + skuName);
        sku.setSkuName(skuName);
        sku.setSpecJson("{\"color\":\"black\"}");
        sku.setImageUrl("/images/sku-a.png");
        sku.setSalePrice(salePrice);
        sku.setLinePrice(salePrice + 1000);
        sku.setStock(stock);
        sku.setLockedStock(lockedStock);
        sku.setSkuStatus("ENABLED");
        sku.setCreatedAt(now);
        sku.setUpdatedAt(now);
        ProductSku savedSku = productSkuRepository.save(sku);

        ProductNotice notice = new ProductNotice();
        notice.setProductId(savedProduct.getId());
        notice.setNoticeTitle("Buyer Guide");
        notice.setNoticeContent("Please read before ordering.");
        notice.setEnabledFlag(true);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        productNoticeRepository.save(notice);

        return new SeededProduct(savedProduct.getId(), savedSku.getId());
    }

    private record SeededProduct(Long productId, Long skuId) {
    }
}
