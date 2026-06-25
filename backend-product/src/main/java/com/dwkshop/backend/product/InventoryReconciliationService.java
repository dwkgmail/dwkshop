package com.dwkshop.backend.product;

import com.dwkshop.backend.domain.entity.InventoryReconciliationRepairRecord;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.InventoryReconciliationRepairRecordRepository;
import com.dwkshop.backend.domain.repository.ProductSkuRepository;
import com.dwkshop.backend.product.dto.InventoryHealthCheckResponse;
import com.dwkshop.backend.product.dto.InventoryReconciliationEventResponse;
import com.dwkshop.backend.product.dto.InventoryReconciliationItemResponse;
import com.dwkshop.backend.product.dto.InventoryReconciliationOrderResponse;
import com.dwkshop.backend.product.dto.InventoryReconciliationResponse;
import com.dwkshop.backend.product.dto.InventoryRepairRecordResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class InventoryReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReconciliationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ProductSkuRepository productSkuRepository;
    private final InventoryReconciliationRepairRecordRepository repairRecordRepository;
    private final ObjectProvider<RabbitAdmin> rabbitAdminProvider;
    private final List<String> deadLetterQueues;
    private final int pendingMinutes;

    public InventoryReconciliationService(
        JdbcTemplate jdbcTemplate,
        ProductSkuRepository productSkuRepository,
        InventoryReconciliationRepairRecordRepository repairRecordRepository,
        ObjectProvider<RabbitAdmin> rabbitAdminProvider,
        @Value("${dwkshop.inventory-reconciliation.pending-minutes:10}") int pendingMinutes,
        @Value("#{'${dwkshop.inventory-reconciliation.dead-letter-queues:dwkshop.inventory.product.dead,dwkshop.refund.approved.product.dead}'.split(',')}") List<String> deadLetterQueues
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.productSkuRepository = productSkuRepository;
        this.repairRecordRepository = repairRecordRepository;
        this.rabbitAdminProvider = rabbitAdminProvider;
        this.pendingMinutes = pendingMinutes;
        this.deadLetterQueues = deadLetterQueues;
    }

    @Transactional(readOnly = true)
    public InventoryReconciliationResponse getReport(boolean onlyDiff) {
        List<Row> rows = jdbcTemplate.query("""
            SELECT *
            FROM (
                SELECT sku.id AS sku_id,
                       sku.sku_code,
                       sku.sku_name,
                       sku.product_id,
                       p.name AS product_name,
                       sku.stock,
                       sku.locked_stock,
                       COALESCE(SUM(CASE WHEN s.state = 'LOCKED' THEN s.quantity ELSE 0 END), 0) AS projected_locked
                FROM product_sku sku
                LEFT JOIN product p ON p.id = sku.product_id
                LEFT JOIN inventory_order_item_state s ON s.sku_id = sku.id
                GROUP BY sku.id, sku.sku_code, sku.sku_name, sku.product_id, p.name, sku.stock, sku.locked_stock
            ) reconciled
            WHERE ? = 0 OR locked_stock <> projected_locked
            ORDER BY ABS(locked_stock - projected_locked) DESC, sku_id ASC
            """, this::mapRow, onlyDiff ? 1 : 0);

        List<InventoryReconciliationItemResponse> items = rows.stream()
            .map(row -> new InventoryReconciliationItemResponse(
                row.skuId(),
                row.skuCode(),
                row.skuName(),
                row.productId(),
                row.productName(),
                row.currentStock(),
                row.projectedLockedStock(),
                row.actualLockedStock(),
                row.difference(),
                row.difference() != 0,
                relatedOrders(row.skuId()),
                recentEvents(row.skuId()),
                repairRecords(row.skuId())
            ))
            .toList();

        return new InventoryReconciliationResponse(LocalDateTime.now(), items, healthChecks());
    }

    @Transactional
    public InventoryRepairRecordResponse repairLockedStock(Long skuId, String operator, String reason) {
        Row row = loadRow(skuId);
        if (row.difference() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU locked stock already matches projected locked stock");
        }
        ProductSku sku = productSkuRepository.findByIdForUpdate(skuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKU does not exist"));
        productSkuRepository.updateLockedStock(skuId, row.projectedLockedStock());

        InventoryReconciliationRepairRecord record = new InventoryReconciliationRepairRecord();
        record.setSkuId(skuId);
        record.setBeforeLockedStock(sku.getLockedStock());
        record.setProjectedLockedStock(row.projectedLockedStock());
        record.setDifference(row.difference());
        record.setRepairType("LOCKED_STOCK");
        record.setRepairStatus("SUCCESS");
        record.setOperator(blankToDefault(operator, "system"));
        record.setReason(trim(reason));
        record.setCreatedAt(LocalDateTime.now());
        return toRepairRecord(repairRecordRepository.save(record));
    }

    @Scheduled(cron = "${dwkshop.inventory-reconciliation.cron:0 0 * * * *}")
    public void scheduledCheck() {
        try {
            InventoryReconciliationResponse report = getReport(true);
            long warningCount = report.items().size() + report.checks().stream().mapToLong(InventoryHealthCheckResponse::count).sum();
            if (warningCount > 0) {
                log.warn("inventory reconciliation found {} warnings", warningCount);
            }
        } catch (RuntimeException ex) {
            log.warn("inventory reconciliation scheduled check failed: {}", ex.getMessage());
        }
    }

    private Row loadRow(Long skuId) {
        return jdbcTemplate.queryForObject("""
            SELECT sku.id AS sku_id,
                   sku.sku_code,
                   sku.sku_name,
                   sku.product_id,
                   p.name AS product_name,
                   sku.stock,
                   sku.locked_stock,
                   COALESCE(SUM(CASE WHEN s.state = 'LOCKED' THEN s.quantity ELSE 0 END), 0) AS projected_locked
            FROM product_sku sku
            LEFT JOIN product p ON p.id = sku.product_id
            LEFT JOIN inventory_order_item_state s ON s.sku_id = sku.id
            WHERE sku.id = ?
            GROUP BY sku.id, sku.sku_code, sku.sku_name, sku.product_id, p.name, sku.stock, sku.locked_stock
            """, this::mapRow, skuId);
    }

    private List<InventoryReconciliationOrderResponse> relatedOrders(Long skuId) {
        try {
            return jdbcTemplate.query("""
                SELECT s.order_id, o.order_no, s.quantity, s.state, s.updated_at
                FROM inventory_order_item_state s
                LEFT JOIN dwkshop_order.trade_order o ON o.id = s.order_id
                WHERE s.sku_id = ?
                ORDER BY s.updated_at DESC
                LIMIT 10
                """, (rs, rowNum) -> new InventoryReconciliationOrderResponse(
                    rs.getLong("order_id"),
                    rs.getString("order_no"),
                    rs.getInt("quantity"),
                    rs.getString("state"),
                    rs.getTimestamp("updated_at").toLocalDateTime()
                ), skuId);
        } catch (DataAccessException ex) {
            log.debug("order schema is unavailable for inventory reconciliation detail: {}", ex.getMessage());
            return jdbcTemplate.query("""
                SELECT order_id, quantity, state, updated_at
                FROM inventory_order_item_state
                WHERE sku_id = ?
                ORDER BY updated_at DESC
                LIMIT 10
                """, (rs, rowNum) -> new InventoryReconciliationOrderResponse(
                    rs.getLong("order_id"),
                    null,
                    rs.getInt("quantity"),
                    rs.getString("state"),
                    rs.getTimestamp("updated_at").toLocalDateTime()
                ), skuId);
        }
    }

    private List<InventoryReconciliationEventResponse> recentEvents(Long skuId) {
        return jdbcTemplate.query("""
            SELECT event_id, order_id, event_type, consumed_at
            FROM inventory_consumed_event
            WHERE sku_id = ?
            ORDER BY consumed_at DESC
            LIMIT 10
            """, (rs, rowNum) -> new InventoryReconciliationEventResponse(
                rs.getString("event_id"),
                rs.getLong("order_id"),
                rs.getString("event_type"),
                rs.getTimestamp("consumed_at").toLocalDateTime()
            ), skuId);
    }

    private List<InventoryRepairRecordResponse> repairRecords(Long skuId) {
        return repairRecordRepository.findTop20BySkuIdOrderByCreatedAtDesc(skuId).stream()
            .map(this::toRepairRecord)
            .toList();
    }

    private List<InventoryHealthCheckResponse> healthChecks() {
        return List.of(
            check("NEGATIVE_STOCK", countProduct("""
                SELECT COUNT(*) FROM product_sku WHERE stock < 0 OR locked_stock < 0
                """), "negative stock rows"),
            check("OUTBOX_BACKLOG", countOptional("""
                SELECT COUNT(*) FROM dwkshop_order.order_outbox_event
                WHERE publish_status = 'PENDING' AND created_at < DATE_SUB(NOW(), INTERVAL ? MINUTE)
                """, pendingMinutes), "pending order outbox rows older than threshold"),
            check("LOCK_PENDING_ORDERS", countOptional("""
                SELECT COUNT(*) FROM dwkshop_order.trade_order o
                LEFT JOIN dwkshop_product.inventory_order_item_state s ON s.order_id = o.id
                WHERE o.order_status = 'WAIT_PAY'
                  AND o.created_at < DATE_SUB(NOW(), INTERVAL ? MINUTE)
                  AND s.id IS NULL
                """, pendingMinutes), "WAIT_PAY orders without inventory state after threshold"),
            check("DEAD_LETTER_QUEUE", deadLetterCount(), "messages in product-related DLQs")
        );
    }

    private InventoryHealthCheckResponse check(String type, long count, String message) {
        return new InventoryHealthCheckResponse(type, count == 0 ? "OK" : "WARN", count, message);
    }

    private long countProduct(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private long countOptional(String sql, Object... args) {
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
            return count == null ? 0 : count;
        } catch (DataAccessException ex) {
            log.debug("inventory health check skipped: {}", ex.getMessage());
            return 0;
        }
    }

    private long deadLetterCount() {
        RabbitAdmin rabbitAdmin = rabbitAdminProvider.getIfAvailable();
        if (rabbitAdmin == null) {
            return 0;
        }
        return deadLetterQueues.stream()
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .mapToLong(name -> {
                try {
                    Properties properties = rabbitAdmin.getQueueProperties(name);
                    Object count = properties == null ? null : properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
                    return count instanceof Number number ? number.longValue() : 0;
                } catch (RuntimeException ex) {
                    log.debug("failed to inspect queue {}: {}", name, ex.getMessage());
                    return 0;
                }
            })
            .sum();
    }

    private Row mapRow(ResultSet rs, int rowNum) throws SQLException {
        int projected = rs.getInt("projected_locked");
        int actual = rs.getInt("locked_stock");
        return new Row(
            rs.getLong("sku_id"),
            rs.getString("sku_code"),
            rs.getString("sku_name"),
            rs.getLong("product_id"),
            rs.getString("product_name"),
            rs.getInt("stock"),
            projected,
            actual,
            actual - projected
        );
    }

    private InventoryRepairRecordResponse toRepairRecord(InventoryReconciliationRepairRecord record) {
        return new InventoryRepairRecordResponse(
            record.getId(),
            record.getSkuId(),
            record.getBeforeLockedStock(),
            record.getProjectedLockedStock(),
            record.getDifference(),
            record.getRepairType(),
            record.getRepairStatus(),
            record.getOperator(),
            record.getReason(),
            record.getCreatedAt()
        );
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    private record Row(
        Long skuId,
        String skuCode,
        String skuName,
        Long productId,
        String productName,
        Integer currentStock,
        Integer projectedLockedStock,
        Integer actualLockedStock,
        Integer difference
    ) {
    }
}
