# Order pricing rules

This document defines the checkout price behavior used by cart, settlement, order creation, and payment.

## Rules

- Cart prices are real-time estimates. The cart reads the latest SKU snapshot and recalculates display amounts whenever cart items are listed or changed.
- The confirm-order page creates a short-lived settlement price snapshot. The default validity is `dwkshop.order.settlement-ttl-minutes=30`.
- A settlement token is single-use. It expires when the TTL passes, when it has already been consumed, or when the settlement store loses the session.
- Product price changes after confirmation do not change the settlement snapshot. Creating an order uses the confirmed snapshot amount and item prices.
- Order creation still validates the client-submitted `expectedPayAmount` against the settlement snapshot. A mismatch requires the user to reconfirm.
- Order creation may still fail for non-price real-time constraints, such as unavailable coupon lock, insufficient points, or inventory lock failure.
- Once an order is created, the order price is locked in `trade_order`, `trade_order_item`, and `trade_order_amount`.
- Unpaid orders are not repriced when an admin changes product prices or when an activity price ends.
- Payment validates against the persisted order payable amount only. It must not recalculate from current product prices.

## Answers to common cases

| Case | Behavior |
| --- | --- |
| Cart display after admin price change | Shows the latest price on the next cart read. |
| Confirm-order page validity | Short-lived by `settlement-ttl-minutes`, default 30 minutes. |
| Activity price ends after confirmation | Existing settlement token keeps the confirmed snapshot until TTL or single-use consumption. |
| Admin changes price while order is unpaid | Existing unpaid order amount is unchanged. |
| Submitted unpaid order | Price is locked. |
| Payment | Checks payment amount against the order amount, not the current SKU price. |
