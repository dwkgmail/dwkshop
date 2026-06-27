package com.dwkshop.backend.product;

import com.dwkshop.backend.domain.entity.InventoryReconciliationRepairRecord;
import com.dwkshop.backend.domain.entity.ProductSku;
import com.dwkshop.backend.domain.repository.InventoryReconciliationRepairRecordRepository;
import com.dwkshop.backend.audit.AdminOperationLogService;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
    private final OrderClient orderClient;
    private final AdminOperationLogService operationLogService;
    private final ObjectProvider<RabbitAdmin> rabbitAdminProvider;
    private final List<String> deadLetterQueues;
    private final int pendingMinutes;

    public InventoryReconciliationService(
        JdbcTemplate jdbcTemplate,
        ProductSkuRepository productSkuRepository,
        InventoryReconciliationRepairRecordRepository repairRecordRepository,
        OrderClient orderClient,
        AdminOperationLogService operationLogService,
        ObjectProvider<RabbitAdmin> rabbitAdminProvider,
        @Value("${dwkshop.inventory-reconciliation.pending-minutes:10}") int pendingMinutes,
        @Value("#{'${dwkshop.inventory-reconciliation.dead-letter-queues:dwkshop.inventory.product.dead,dwkshop.refund.approved.product.dead}'.split(',')}") List<String> deadLetterQueues
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.productSkuRepository = productSkuRepository;
        this.repairRecordRepository = repairRecordRepository;
        this.orderClient = orderClient;
        this.operationLogService = operationLogService;
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
        Map<String, Object> beforeSnapshot = snapshotSku(sku, row.projectedLockedStock());
        productSkuRepository.updateLockedStock(skuId, row.projectedLockedStock());
        ProductSku saved = productSkuRepository.findById(skuId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SKU does not exist"));

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
        InventoryRepairRecordResponse response = toRepairRecord(repairRecordRepository.save(record));
        operationLogService.record("INVENTORY_REPAIR", "SKU", skuId, beforeSnapshot, snapshotSku(saved, row.projectedLockedStock()), trim(reason));
        return response;
    }

    private Map<String, Object> snapshotSku(ProductSku sku, Integer projectedLockedStock) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("skuId", sku.getId());
        snapshot.put("skuCode", sku.getSkuCode());
        snapshot.put("skuName", sku.getSkuName());
        snapshot.put("stock", sku.getStock());
        snapshot.put("lockedStock", sku.getLockedStock());
        snapshot.put("projectedLockedStock", projectedLockedStock);
        return snapshot;
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
        List<InventoryStateRow> states = jdbcTemplate.query("""
            SELECT order_id, quantity, state, updated_at
            FROM inventory_order_item_state
            WHERE sku_id = ?
            ORDER BY updated_at DESC
            LIMIT 10
            """, (rs, rowNum) -> new InventoryStateRow(
                rs.getLong("order_id"),
                rs.getInt("quantity"),
                rs.getString("state"),
                rs.getTimestamp("updated_at").toLocalDateTime()
            ), skuId);
        Map<Long, InventoryOrderSummary> summaries = orderClient.getInventoryOrderSummaries(
                states.stream().map(InventoryStateRow::orderId).toList()
            ).stream()
            .collect(Collectors.toMap(InventoryOrderSummary::id, Function.identity(), (left, right) -> left));
        return states.stream()
            .map(state -> new InventoryReconciliationOrderResponse(
                state.orderId(),
                summaries.get(state.orderId()) == null ? null : summaries.get(state.orderId()).orderNo(),
                state.quantity(),
                state.state(),
                state.updatedAt()
            ))
            .toList();
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
        InventoryOrderHealth orderHealth = orderClient.getInventoryOrderHealth(pendingMinutes);
        return List.of(
            check("NEGATIVE_STOCK", countProduct("""
                SELECT COUNT(*) FROM product_sku WHERE stock < 0 OR locked_stock < 0
                """), "negative stock rows"),
            check("OUTBOX_BACKLOG", orderHealth.pendingOutboxBacklog(), "pending order outbox rows older than threshold"),
            check("LOCK_PENDING_ORDERS", countOrdersWithoutInventoryState(orderHealth.waitPayOrderIds()), "WAIT_PAY orders without inventory state after threshold"),
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

    private long countOrdersWithoutInventoryState(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        return orderIds.stream()
            .filter(orderId -> {
                Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inventory_order_item_state WHERE order_id = ?",
                    Long.class,
                    orderId
                );
                return count == null || count == 0;
            })
            .count();
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

    private record InventoryStateRow(
        Long orderId,
        Integer quantity,
        String state,
        LocalDateTime updatedAt
    ) {
    }
}
