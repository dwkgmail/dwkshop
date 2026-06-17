package com.dwkshop.backend.search;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Getter
@Setter
@NoArgsConstructor
@Document(indexName = "dwkshop_products")
public class ProductSearchDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Keyword)
    private String productCode;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String subtitle;

    @Field(type = FieldType.Keyword)
    private String saleStatus;

    @Field(type = FieldType.Boolean)
    private Boolean deletedFlag;

    @Field(type = FieldType.Date)
    private LocalDateTime updatedAt;
}
