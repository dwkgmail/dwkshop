package com.dwkshop.backend.search;

import com.dwkshop.backend.domain.entity.Product;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "dwkshop.search.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchProductSearchGateway implements ProductSearchGateway {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchProductSearchGateway.class);
    private static final int SEARCH_SIZE = 50;

    private final ProductSearchRepository productSearchRepository;

    public ElasticsearchProductSearchGateway(ProductSearchRepository productSearchRepository) {
        this.productSearchRepository = productSearchRepository;
    }

    @Override
    public Optional<List<Long>> searchOnSaleProductIds(String keyword) {
        try {
            List<Long> ids = productSearchRepository.searchOnSale("ON_SALE", keyword, PageRequest.of(0, SEARCH_SIZE))
                .stream()
                .map(ProductSearchDocument::getId)
                .toList();
            return Optional.of(ids);
        } catch (RuntimeException ex) {
            log.warn("Elasticsearch product search failed, falling back to database search", ex);
            return Optional.empty();
        }
    }

    @Override
    public void indexProduct(Product product) {
        try {
            productSearchRepository.save(toDocument(product));
        } catch (RuntimeException ex) {
            log.warn("Failed to index product {} into Elasticsearch", product.getId(), ex);
        }
    }

    @Override
    public void indexProducts(List<Product> products) {
        if (products.isEmpty()) {
            return;
        }
        try {
            productSearchRepository.saveAll(products.stream().map(this::toDocument).toList());
        } catch (RuntimeException ex) {
            log.warn("Failed to bulk index products into Elasticsearch", ex);
        }
    }

    private ProductSearchDocument toDocument(Product product) {
        ProductSearchDocument document = new ProductSearchDocument();
        document.setId(product.getId());
        document.setCategoryId(product.getCategoryId());
        document.setProductCode(product.getProductCode());
        document.setName(product.getName());
        document.setSubtitle(product.getSubtitle());
        document.setSaleStatus(product.getSaleStatus());
        document.setDeletedFlag(product.getDeletedFlag());
        document.setUpdatedAt(product.getUpdatedAt());
        return document;
    }
}
