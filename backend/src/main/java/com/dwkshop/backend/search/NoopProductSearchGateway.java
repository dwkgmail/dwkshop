package com.dwkshop.backend.search;

import com.dwkshop.backend.domain.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "dwkshop.search.elasticsearch", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopProductSearchGateway implements ProductSearchGateway {

    @Override
    public Optional<List<Long>> searchOnSaleProductIds(String keyword) {
        return Optional.empty();
    }

    @Override
    public void indexProduct(Product product) {
    }

    @Override
    public void indexProducts(List<Product> products) {
    }
}
