package com.dwkshop.backend.product;

import com.dwkshop.backend.product.dto.CategoryResponse;
import com.dwkshop.backend.product.dto.ProductDetailResponse;
import com.dwkshop.backend.product.dto.ProductSummaryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public List<ProductSummaryResponse> listProducts(@RequestParam(required = false) Long categoryId) {
        return productService.listProducts(categoryId);
    }

    @GetMapping("/products/{id}")
    public ProductDetailResponse getProduct(@PathVariable Long id) {
        return productService.getProductDetail(id);
    }

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return productService.listCategories();
    }

    @GetMapping("/search/products")
    public List<ProductSummaryResponse> searchProducts(@RequestParam String keyword) {
        return productService.searchProducts(keyword);
    }
}
