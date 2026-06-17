package com.dwkshop.backend.search;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductSearchRepository extends ElasticsearchRepository<ProductSearchDocument, Long> {

    @Query("""
        {
          "bool": {
            "must": [
              {
                "multi_match": {
                  "query": "?1",
                  "fields": ["name^3", "subtitle", "productCode"],
                  "type": "best_fields"
                }
              }
            ],
            "filter": [
              { "term": { "saleStatus": "?0" } },
              { "term": { "deletedFlag": false } }
            ]
          }
        }
        """)
    List<ProductSearchDocument> searchOnSale(String saleStatus, String keyword, Pageable pageable);
}
