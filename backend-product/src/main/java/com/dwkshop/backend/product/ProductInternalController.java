package com.dwkshop.backend.product;

import com.dwkshop.backend.product.dto.ProductSkuSnapshotResponse;
import com.dwkshop.backend.product.dto.LockSkuStockRequest;
import com.dwkshop.backend.product.dto.LockSkuStockResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/products")
public class ProductInternalController {

    private final ProductService productService;

    public ProductInternalController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/skus/{skuId}/snapshot")
    public ProductSkuSnapshotResponse getSkuSnapshot(@PathVariable Long skuId) {
        return productService.getSkuSnapshot(skuId);
    }

    @PostMapping("/skus/{skuId}/stock-locks")
    public LockSkuStockResponse lockSkuStock(@PathVariable Long skuId, @Valid @RequestBody LockSkuStockRequest request) {
        return productService.lockSkuStock(skuId, request.quantity());
    }

    @PostMapping("/skus/{skuId}/stock-releases")
    public LockSkuStockResponse releaseSkuStock(@PathVariable Long skuId, @Valid @RequestBody LockSkuStockRequest request) {
        return productService.releaseSkuStock(skuId, request.quantity());
    }
}
