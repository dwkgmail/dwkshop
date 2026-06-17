package com.dwkshop.backend.search;

import com.dwkshop.backend.domain.repository.ProductRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "dwkshop.search.elasticsearch", name = "enabled", havingValue = "true")
public class ProductSearchIndexInitializer {

    private final ProductRepository productRepository;
    private final ProductSearchGateway productSearchGateway;

    public ProductSearchIndexInitializer(ProductRepository productRepository, ProductSearchGateway productSearchGateway) {
        this.productRepository = productRepository;
        this.productSearchGateway = productSearchGateway;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexExistingProducts() {
        productSearchGateway.indexProducts(productRepository.findByDeletedFlagFalseOrderByIdDesc());
    }
}
