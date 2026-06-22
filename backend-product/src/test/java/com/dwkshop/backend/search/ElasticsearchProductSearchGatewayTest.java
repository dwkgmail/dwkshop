package com.dwkshop.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.dwkshop.backend.domain.entity.Product;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ElasticsearchProductSearchGatewayTest {

    @Test
    void indexesUpdatedAtAsACompleteIsoLocalDateTime() {
        ProductSearchRepository repository = Mockito.mock(ProductSearchRepository.class);
        ElasticsearchProductSearchGateway gateway = new ElasticsearchProductSearchGateway(repository);
        Product product = new Product();
        product.setId(1L);
        product.setUpdatedAt(LocalDateTime.of(2026, 6, 22, 13, 45, 12));

        gateway.indexProduct(product);

        ArgumentCaptor<ProductSearchDocument> captor = ArgumentCaptor.forClass(ProductSearchDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo("2026-06-22T13:45:12");
    }

    @Test
    void acceptsLegacyDateOnlyValuesFromExistingIndexDocuments() {
        ProductSearchDocument document = new ProductSearchDocument();

        document.setUpdatedAt("2026-06-22");

        assertThat(document.getUpdatedAt()).isEqualTo("2026-06-22");
    }
}
