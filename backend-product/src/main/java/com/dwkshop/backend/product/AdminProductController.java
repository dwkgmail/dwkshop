package com.dwkshop.backend.product;

import com.dwkshop.backend.auth.RequiresConfirmation;
import com.dwkshop.backend.auth.RequiresPermission;
import com.dwkshop.backend.product.dto.AdminProductResponse;
import com.dwkshop.backend.product.dto.ProductDetailResponse;
import com.dwkshop.backend.product.dto.ProductUpsertRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    @RequiresPermission("product:read")
    public List<AdminProductResponse> listProducts() {
        return productService.listAdminProducts();
    }

    @PostMapping
    @RequiresPermission("product:write")
    public ProductDetailResponse createProduct(@Valid @RequestBody ProductUpsertRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission("product:write")
    public ProductDetailResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductUpsertRequest request) {
        return productService.updateProduct(id, request);
    }

    @PostMapping("/{id}/on-sale")
    @RequiresPermission("product:publish")
    public ProductDetailResponse onSale(@PathVariable Long id) {
        return productService.onSale(id);
    }

    @PostMapping("/{id}/off-sale")
    @RequiresPermission("product:publish")
    @RequiresConfirmation
    public ProductDetailResponse offSale(@PathVariable Long id) {
        return productService.offSale(id);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("product:write")
    @RequiresConfirmation
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }
}
