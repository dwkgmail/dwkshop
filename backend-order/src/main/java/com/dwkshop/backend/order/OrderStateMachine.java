package com.dwkshop.backend.order;

import com.dwkshop.backend.domain.entity.TradeOrder;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class OrderStateMachine {

    static final String ORDER_WAIT_PAY = "WAIT_PAY";
    static final String ORDER_WAIT_SHIP = "WAIT_SHIP";
    static final String ORDER_WAIT_RECEIVE = "WAIT_RECEIVE";
    static final String ORDER_FINISHED = "FINISHED";
    static final String ORDER_CANCELED = "CANCELED";

    static final String PAY_UNPAID = "UNPAID";
    static final String PAY_PAID = "PAID";
    static final String PAY_CLOSED = "CLOSED";
    static final String PAY_REFUNDED = "REFUNDED";

    static final String DELIVERY_UNSHIPPED = "UNSHIPPED";
    static final String DELIVERY_SHIPPED = "SHIPPED";
    static final String DELIVERY_IN_TRANSIT = "IN_TRANSIT";
    static final String DELIVERY_DELIVERED = "DELIVERED";

    static final String AFTERSALE_NONE = "NONE";
    static final String AFTERSALE_APPLYING = "APPLYING";
    static final String AFTERSALE_REJECTED = "REJECTED";
    static final String AFTERSALE_PARTIAL_REFUNDED = "PARTIAL_REFUNDED";
    static final String AFTERSALE_REFUNDED = "REFUNDED";

    private OrderStateMachine() {
    }

    static void initializeCreated(TradeOrder order) {
        order.setOrderStatus(ORDER_WAIT_PAY);
        order.setPayStatus(PAY_UNPAID);
        order.setDeliveryStatus(DELIVERY_UNSHIPPED);
        order.setAftersaleStatus(AFTERSALE_NONE);
    }

    static void cancelUnpaid(TradeOrder order, LocalDateTime now) {
        require(ORDER_WAIT_PAY.equals(order.getOrderStatus()) && PAY_UNPAID.equals(order.getPayStatus()), "当前订单不可取消");
        order.setOrderStatus(ORDER_CANCELED);
        order.setPayStatus(PAY_CLOSED);
        order.setCancelTime(now);
        order.setUpdatedAt(now);
    }

    static void expirePayment(TradeOrder order, LocalDateTime now) {
        require(ORDER_WAIT_PAY.equals(order.getOrderStatus()) && PAY_UNPAID.equals(order.getPayStatus()), "当前订单不可支付");
        order.setOrderStatus(ORDER_CANCELED);
        order.setPayStatus(PAY_CLOSED);
        order.setCancelTime(now);
        order.setUpdatedAt(now);
    }

    static void pay(TradeOrder order, LocalDateTime now) {
        require(ORDER_WAIT_PAY.equals(order.getOrderStatus()) && PAY_UNPAID.equals(order.getPayStatus()), "当前订单不可支付");
        order.setOrderStatus(ORDER_WAIT_SHIP);
        order.setPayStatus(PAY_PAID);
        order.setDeliveryStatus(DELIVERY_UNSHIPPED);
        order.setPayTime(now);
        order.setUpdatedAt(now);
    }

    static void ship(TradeOrder order, LocalDateTime now) {
        require(ORDER_WAIT_SHIP.equals(order.getOrderStatus()) && PAY_PAID.equals(order.getPayStatus()), "当前订单不可发货");
        order.setOrderStatus(ORDER_WAIT_RECEIVE);
        order.setDeliveryStatus(DELIVERY_SHIPPED);
        order.setDeliveryTime(now);
        order.setUpdatedAt(now);
    }

    static void updateDelivery(TradeOrder order, String targetStatus, LocalDateTime now) {
        require(!DELIVERY_UNSHIPPED.equals(order.getDeliveryStatus()) && order.getDeliveryTime() != null, "订单尚未发货");
        order.setDeliveryStatus(targetStatus);
        if (DELIVERY_DELIVERED.equals(targetStatus)) {
            order.setOrderStatus(ORDER_FINISHED);
            order.setFinishTime(now);
        } else {
            order.setOrderStatus(ORDER_WAIT_RECEIVE);
        }
        order.setUpdatedAt(now);
    }

    static void applyAftersale(TradeOrder order, LocalDateTime now) {
        require(PAY_PAID.equals(order.getPayStatus()), "只有已支付订单可以申请退款");
        require(!AFTERSALE_REFUNDED.equals(order.getAftersaleStatus()), "订单已退款");
        require(AFTERSALE_NONE.equals(order.getAftersaleStatus()) || AFTERSALE_REJECTED.equals(order.getAftersaleStatus()) || AFTERSALE_PARTIAL_REFUNDED.equals(order.getAftersaleStatus()), "订单已有处理中的售后申请");
        order.setAftersaleStatus(AFTERSALE_APPLYING);
        order.setUpdatedAt(now);
    }

    static void rejectAftersale(TradeOrder order, LocalDateTime now) {
        require(AFTERSALE_APPLYING.equals(order.getAftersaleStatus()), "订单售后状态不是处理中");
        order.setAftersaleStatus(AFTERSALE_REJECTED);
        order.setUpdatedAt(now);
    }

    static boolean completeAftersale(TradeOrder order, LocalDateTime now) {
        return completeAftersale(order, now, true);
    }

    static boolean completeAftersale(TradeOrder order, LocalDateTime now, boolean fullRefund) {
        if (AFTERSALE_REFUNDED.equals(order.getAftersaleStatus())) {
            return false;
        }
        require(AFTERSALE_APPLYING.equals(order.getAftersaleStatus()), "订单售后状态不是处理中");
        if (fullRefund) {
            order.setPayStatus(PAY_REFUNDED);
            order.setAftersaleStatus(AFTERSALE_REFUNDED);
        } else {
            order.setAftersaleStatus(AFTERSALE_PARTIAL_REFUNDED);
        }
        order.setUpdatedAt(now);
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
