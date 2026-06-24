package com.dwkshop.backend.order;

import com.dwkshop.backend.domain.entity.TradeOrder;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 24, 10, 0);

    @Test
    void cancelUnpaidClosesPaymentAndSetsCancelTime() {
        TradeOrder order = order("WAIT_PAY", "UNPAID", "UNSHIPPED", "NONE");

        OrderStateMachine.cancelUnpaid(order, NOW);

        assertThat(order.getOrderStatus()).isEqualTo("CANCELED");
        assertThat(order.getPayStatus()).isEqualTo("CLOSED");
        assertThat(order.getCancelTime()).isEqualTo(NOW);
        assertThat(order.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void payRequiresWaitingUnpaidOrder() {
        TradeOrder paidOrder = order("WAIT_SHIP", "PAID", "UNSHIPPED", "NONE");

        assertThatThrownBy(() -> OrderStateMachine.pay(paidOrder, NOW))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void payMovesOrderToWaitShip() {
        TradeOrder order = order("WAIT_PAY", "UNPAID", "UNSHIPPED", "NONE");

        OrderStateMachine.pay(order, NOW);

        assertThat(order.getOrderStatus()).isEqualTo("WAIT_SHIP");
        assertThat(order.getPayStatus()).isEqualTo("PAID");
        assertThat(order.getDeliveryStatus()).isEqualTo("UNSHIPPED");
        assertThat(order.getPayTime()).isEqualTo(NOW);
    }

    @Test
    void shipRequiresPaidWaitShipOrder() {
        TradeOrder order = order("WAIT_PAY", "UNPAID", "UNSHIPPED", "NONE");

        assertThatThrownBy(() -> OrderStateMachine.ship(order, NOW))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deliveredDeliveryUpdateFinishesOrder() {
        TradeOrder order = order("WAIT_RECEIVE", "PAID", "SHIPPED", "NONE");
        order.setDeliveryTime(NOW.minusHours(1));

        OrderStateMachine.updateDelivery(order, "DELIVERED", NOW);

        assertThat(order.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(order.getOrderStatus()).isEqualTo("FINISHED");
        assertThat(order.getFinishTime()).isEqualTo(NOW);
    }

    @Test
    void aftersaleApplyRejectAndCompleteUseAftersaleGuards() {
        TradeOrder order = order("WAIT_SHIP", "PAID", "UNSHIPPED", "NONE");

        OrderStateMachine.applyAftersale(order, NOW);
        assertThat(order.getAftersaleStatus()).isEqualTo("APPLYING");

        OrderStateMachine.rejectAftersale(order, NOW.plusMinutes(1));
        assertThat(order.getAftersaleStatus()).isEqualTo("REJECTED");

        OrderStateMachine.applyAftersale(order, NOW.plusMinutes(2));
        boolean changed = OrderStateMachine.completeAftersale(order, NOW.plusMinutes(3));

        assertThat(changed).isTrue();
        assertThat(order.getOrderStatus()).isEqualTo("WAIT_SHIP");
        assertThat(order.getPayStatus()).isEqualTo("REFUNDED");
        assertThat(order.getAftersaleStatus()).isEqualTo("REFUNDED");
    }

    @Test
    void completeAftersaleIsIdempotentForRefundedOrder() {
        TradeOrder order = order("FINISHED", "REFUNDED", "DELIVERED", "REFUNDED");

        boolean changed = OrderStateMachine.completeAftersale(order, NOW);

        assertThat(changed).isFalse();
    }

    private TradeOrder order(String orderStatus, String payStatus, String deliveryStatus, String aftersaleStatus) {
        TradeOrder order = new TradeOrder();
        order.setOrderStatus(orderStatus);
        order.setPayStatus(payStatus);
        order.setDeliveryStatus(deliveryStatus);
        order.setAftersaleStatus(aftersaleStatus);
        return order;
    }
}
