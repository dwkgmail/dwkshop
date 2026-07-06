package com.dwkshop.backend.domain.repository;

import com.dwkshop.backend.domain.entity.TradeOrder;
import com.dwkshop.backend.order.dto.UserOrderCountResponse;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    List<TradeOrder> findAllByOrderByIdDesc();

    List<TradeOrder> findByUserIdOrderByIdDesc(Long userId);

    Optional<TradeOrder> findByIdAndUserId(Long id, Long userId);

    Optional<TradeOrder> findByUserIdAndClientRequestId(Long userId, String clientRequestId);

    List<TradeOrder> findByIdIn(Collection<Long> ids);

    @Query("""
        select new com.dwkshop.backend.order.dto.UserOrderCountResponse(o.userId, count(o))
        from TradeOrder o
        where o.userId in :userIds
        group by o.userId
        """)
    List<UserOrderCountResponse> countByUserIds(@Param("userIds") Collection<Long> userIds);

    @Query("select o.id from TradeOrder o where o.orderStatus = :status and o.createdAt < :cutoff order by o.createdAt asc")
    List<Long> findStaleOrderIdsByStatus(
        @Param("status") String status,
        @Param("cutoff") LocalDateTime cutoff,
        Pageable pageable
    );

    @Query("""
        select o.id from TradeOrder o
        where o.orderStatus = :orderStatus
          and o.payStatus = :payStatus
          and o.payExpireTime is not null
          and o.payExpireTime <= :now
        order by o.payExpireTime asc, o.id asc
        """)
    List<Long> findExpiredUnpaidOrderIds(
        @Param("orderStatus") String orderStatus,
        @Param("payStatus") String payStatus,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );
}
