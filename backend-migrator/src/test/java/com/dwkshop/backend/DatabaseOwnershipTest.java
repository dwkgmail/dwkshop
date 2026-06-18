package com.dwkshop.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DatabaseOwnershipTest {

    private static final Pattern TABLE = Pattern.compile("@Table\\(name\\s*=\\s*\\\"(?:`)?([^`\\\"]+)(?:`)?\\\"\\)");
    private static final Pattern JPA_RELATION = Pattern.compile("@(ManyToOne|OneToMany|OneToOne|ManyToMany|JoinColumn)\\b");

    private static final Map<String, Set<String>> OWNERSHIP = new LinkedHashMap<>();

    static {
        OWNERSHIP.put("backend-auth", Set.of("user", "admin_user"));
        OWNERSHIP.put("backend-product", Set.of("product_category", "product", "product_sku", "product_notice", "product_refund_command"));
        OWNERSHIP.put("backend-cart", Set.of("cart_item"));
        OWNERSHIP.put("backend-member", Set.of("user_address", "user_point_account"));
        OWNERSHIP.put("backend-marketing", Set.of("coupon", "coupon_user"));
        OWNERSHIP.put("backend-order", Set.of("trade_order", "trade_order_item", "trade_order_amount"));
        OWNERSHIP.put("backend-aftersale", Set.of("aftersale_order", "aftersale_refund_flow", "aftersale_outbox_event"));
    }

    @Test
    void extractedServicesContainOnlyOwnedJpaTablesAndNoJpaRelationships() throws IOException {
        Path root = repositoryRoot();
        for (Map.Entry<String, Set<String>> owner : OWNERSHIP.entrySet()) {
            Path sources = root.resolve(owner.getKey()).resolve("src/main/java");
            Set<String> actualTables;
            try (var files = Files.walk(sources)) {
                var javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
                for (Path javaFile : javaFiles) {
                    String source = Files.readString(javaFile);
                    assertThat(JPA_RELATION.matcher(source).find())
                            .as("JPA relationship annotation in %s", root.relativize(javaFile))
                            .isFalse();
                }
                actualTables = javaFiles.stream()
                        .map(DatabaseOwnershipTest::readUnchecked)
                        .map(TABLE::matcher)
                        .filter(Matcher::find)
                        .map(matcher -> matcher.group(1))
                        .collect(Collectors.toSet());
            }
            assertThat(actualTables).as("tables owned by %s", owner.getKey()).isEqualTo(owner.getValue());
        }
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        if (Files.isDirectory(current.resolve("backend-auth"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("backend-auth"))) {
            return parent;
        }
        throw new IllegalStateException("Cannot locate repository root from " + current);
    }
}
