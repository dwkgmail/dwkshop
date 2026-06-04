package com.dwkshop.backend.product;

import com.dwkshop.backend.product.dto.AdminProductResponse;
import com.dwkshop.backend.product.dto.ProductDetailResponse;
import com.dwkshop.backend.product.dto.ProductUpsertRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<AdminProductResponse> listProducts() {
        return productService.listAdminProducts();
    }

    @PostMapping
    public ProductDetailResponse createProduct(@Valid @RequestBody ProductUpsertRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping("/{id}")
    public ProductDetailResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductUpsertRequest request) {
        return productService.updateProduct(id, request);
    }

    @PostMapping("/{id}/on-sale")
    public ProductDetailResponse onSale(@PathVariable Long id) {
        return productService.onSale(id);
    }

    @PostMapping("/{id}/off-sale")
    public ProductDetailResponse offSale(@PathVariable Long id) {
        return productService.offSale(id);
    }
}
