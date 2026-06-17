package com.dwkshop.backend.product;

import com.dwkshop.backend.product.dto.ProductSkuSnapshotResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
