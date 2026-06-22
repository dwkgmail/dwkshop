package com.dwkshop.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DatabaseOwnershipTest {

    private static final Pattern OWNERSHIP_ROW = Pattern.compile(
            "^\\| `(?<schema>dwkshop_[a-z]+)` \\| [^|]+ \\| (?<tables>[^|]+) \\|", Pattern.MULTILINE);
    private static final Pattern BACKTICK_VALUE = Pattern.compile("`([^`]+)`");
    private static final Pattern TABLE_ANNOTATION = Pattern.compile("@Table\\s*\\((?<body>[^)]*)\\)", Pattern.DOTALL);
    private static final Pattern NAME_ATTRIBUTE = Pattern.compile("\\bname\\s*=\\s*\"`?([^`\"]+)`?\"");
    private static final Pattern JPA_RELATION = Pattern.compile(
            "@(ManyToOne|OneToMany|OneToOne|ManyToMany|JoinColumn|JoinColumns)\\b");
    private static final Pattern QUALIFIED_SCHEMA = Pattern.compile("\\bdwkshop_[a-z]+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_TABLE_REFERENCE = Pattern.compile(
            "(?i)\\b(?:from|join|update|into|delete\\s+from)\\s+(`?[a-z][a-z0-9_]*`?)(?:\\s*\\.\\s*(`?[a-z][a-z0-9_]*`?))?");
    private static final Pattern SQL_TABLE_DECLARATION = Pattern.compile(
            "(?i)\\b(?:alter|create)\\s+table(?:\\s+if\\s+not\\s+exists)?\\s+(`?[a-z][a-z0-9_]*`?)(?:\\s*\\.\\s*(`?[a-z][a-z0-9_]*`?))?");
    private static final Pattern FOREIGN_KEY_REFERENCE = Pattern.compile(
            "(?i)\\breferences\\s+(`?[a-z][a-z0-9_]*`?)(?:\\s*\\.\\s*(`?[a-z][a-z0-9_]*`?))?");

    @Test
    void serviceJpaMappingsRespectDatabaseOwnership() throws IOException {
        Path root = repositoryRoot();
        Ownership ownership = readOwnership(root);
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> service : ownership.moduleSchemas.entrySet()) {
            for (Path javaFile : filesUnder(root.resolve(service.getKey()).resolve("src/main/java"), ".java")) {
                String source = Files.readString(javaFile);
                Matcher relation = JPA_RELATION.matcher(source);
                if (relation.find()) {
                    violations.add("%s uses forbidden JPA relationship %s"
                            .formatted(root.relativize(javaFile), relation.group()));
                }

                Matcher annotation = TABLE_ANNOTATION.matcher(source);
                while (annotation.find()) {
                    Matcher name = NAME_ATTRIBUTE.matcher(annotation.group("body"));
                    if (!name.find()) {
                        violations.add("%s has @Table without an explicit name".formatted(root.relativize(javaFile)));
                        continue;
                    }
                    String table = name.group(1).toLowerCase(Locale.ROOT);
                    List<String> schemas = ownership.tableSchemas.getOrDefault(table, List.of());
                    if (schemas.size() != 1) {
                        violations.add("%s maps table %s, which occurs in %d ownership rows"
                                .formatted(root.relativize(javaFile), table, schemas.size()));
                    } else if (!schemas.getFirst().equals(service.getValue())) {
                        violations.add("%s maps %s owned by %s (service schema is %s)"
                                .formatted(root.relativize(javaFile), table, schemas.getFirst(), service.getValue()));
                    }
                }
            }
        }

        assertThat(violations).as("database ownership violations").isEmpty();
    }

    @Test
    void servicesDoNotDirectlyAccessAnotherServicesSchemaOrTables() throws IOException {
        Path root = repositoryRoot();
        Ownership ownership = readOwnership(root);
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> service : ownership.moduleSchemas.entrySet()) {
            Path main = root.resolve(service.getKey()).resolve("src/main");
            for (Path file : filesUnder(main, ".java", ".sql", ".xml", ".yml", ".yaml", ".properties")) {
                String source = Files.readString(file);
                Matcher schema = QUALIFIED_SCHEMA.matcher(source);
                while (schema.find()) {
                    String referencedSchema = schema.group().toLowerCase(Locale.ROOT);
                    if (ownership.moduleSchemas.containsValue(referencedSchema)
                            && !referencedSchema.equals(service.getValue())) {
                        violations.add("%s references foreign schema %s"
                                .formatted(root.relativize(file), referencedSchema));
                    }
                }

                Matcher sqlReference = SQL_TABLE_REFERENCE.matcher(source);
                while (sqlReference.find()) {
                    String table = unquote(sqlReference.group(2) == null ? sqlReference.group(1) : sqlReference.group(2));
                    List<String> schemas = ownership.tableSchemas.getOrDefault(table, List.of());
                    if (schemas.size() == 1 && !schemas.getFirst().equals(service.getValue())) {
                        violations.add("%s directly queries foreign table %s (%s)"
                                .formatted(root.relativize(file), table, schemas.getFirst()));
                    }
                }
            }
        }

        assertThat(violations).as("cross-service database access violations").isEmpty();
    }

    @Test
    void flywayForeignKeysDoNotCrossSplitSchemas() throws IOException {
        Path root = repositoryRoot();
        Path migrations = root.resolve("backend-migrator/src/main/resources/db/migration");
        List<String> violations = new ArrayList<>();

        for (Path migration : filesUnder(migrations, ".sql")) {
            for (String statement : Files.readString(migration).split(";")) {
                Matcher table = SQL_TABLE_DECLARATION.matcher(statement);
                if (!table.find() || table.group(2) == null) {
                    continue; // Pre-split migrations use the legacy default schema.
                }
                String sourceSchema = unquote(table.group(1));
                if (!sourceSchema.startsWith("dwkshop_")) {
                    continue;
                }
                Matcher reference = FOREIGN_KEY_REFERENCE.matcher(statement);
                while (reference.find()) {
                    String targetSchema = reference.group(2) == null ? sourceSchema : unquote(reference.group(1));
                    if (!sourceSchema.equals(targetSchema)) {
                        violations.add("%s defines FK from %s to %s"
                                .formatted(root.relativize(migration), sourceSchema, targetSchema));
                    }
                }
            }
        }

        assertThat(violations).as("cross-schema Flyway foreign keys").isEmpty();
    }

    private static Ownership readOwnership(Path root) throws IOException {
        String document = Files.readString(root.resolve("docs/database-ownership.md"));
        Map<String, String> moduleSchemas = new LinkedHashMap<>();
        Map<String, List<String>> tableSchemas = new LinkedHashMap<>();
        Matcher row = OWNERSHIP_ROW.matcher(document);
        while (row.find()) {
            String schema = row.group("schema");
            moduleSchemas.put("backend-" + schema.substring("dwkshop_".length()), schema);
            Matcher table = BACKTICK_VALUE.matcher(row.group("tables"));
            while (table.find()) {
                tableSchemas.computeIfAbsent(table.group(1).toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                        .add(schema);
            }
        }
        assertThat(moduleSchemas).as("ownership rows parsed from docs/database-ownership.md").isNotEmpty();
        return new Ownership(moduleSchemas, tableSchemas);
    }

    private static List<Path> filesUnder(Path directory, String... suffixes) throws IOException {
        try (var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> List.of(suffixes).stream().anyMatch(path.toString()::endsWith))
                    .toList();
        }
    }

    private static String unquote(String identifier) {
        return identifier.replace("`", "").toLowerCase(Locale.ROOT);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("backend-auth"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return current;
    }

    private record Ownership(Map<String, String> moduleSchemas, Map<String, List<String>> tableSchemas) {}
}
