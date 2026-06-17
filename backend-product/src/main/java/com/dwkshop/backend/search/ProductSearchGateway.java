package com.dwkshop.backend.search;

import com.dwkshop.backend.domain.entity.Product;
import java.util.List;
import java.util.Optional;

public interface ProductSearchGateway {

    Optional<List<Long>> searchOnSaleProductIds(String keyword);

    void indexProduct(Product product);

    void indexProducts(List<Product> products);
}
