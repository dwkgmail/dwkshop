# Microservices Split

This project is moving from a Spring Boot monolith to a service-oriented layout in small, reversible steps.

## Current Step

The first runnable split introduces an API Gateway and five service runtimes:

| Service | Internal port | Routed paths | Current implementation |
| --- | ---: | --- | --- |
| gateway | 8080 | all public traffic | Spring Cloud Gateway |
| auth-service | 18081 | `/api/auth/**`, `/admin/auth/**`, `/api/health` | backend image |
| product-service | 18082 | `/api/products/**`, `/api/categories/**`, `/api/search/products`, `/admin/products/**` | backend image |
| cart-service | 18083 | `/api/cart/**` | backend image |
| order-service | 18084 | `/api/orders/**`, `/admin/orders/**` | backend image |
| aftersale-service | 18085 | `/api/aftersales/**`, `/admin/aftersales/**` | backend image |

The services still share the existing backend codebase and database. This is intentional for the first step: the current order, cart, product, coupon, point, and aftersale flows share JPA entities and database transactions. Routing traffic through service names first lets frontend and deployment boundaries stabilize before data ownership is split.

## Run Locally

```bash
docker compose -f docker-compose.microservices.yml up --build
```

The frontend can continue to call `http://localhost:8080` because the gateway keeps the public port unchanged.

## Target Ownership

| Service | Owns | Reads from other services by |
| --- | --- | --- |
| auth-service | `user`, `admin_user`, token issuing | token claims |
| product-service | `product`, `product_sku`, `product_category`, `product_notice`, search index | product query API |
| cart-service | `cart_item` | product snapshot/query API |
| order-service | `trade_order`, `trade_order_item`, `trade_order_amount`, settlement sessions | product, coupon, point, address APIs and events |
| aftersale-service | `aftersale_order` | order query API and order events |
| marketing-service | `coupon`, `coupon_user` | coupon reservation/use API |
| member-service | `user_address`, `user_point_account`, `user_point_flow` | address and point APIs |

## Migration Sequence

1. Keep gateway routes stable and add service-level health checks.
2. Extract shared DTOs and error contracts into a small `common-api` module.
3. Move product controllers, services, repositories, entities, migrations, and search integration into a real product service.
4. Replace cart and order direct JPA reads of product tables with product-service APIs or product snapshot events.
5. Move cart ownership, then order ownership, then aftersale ownership.
6. Split the single database into per-service schemas after cross-service reads are removed.
7. Turn order-created and stock-related flows into RabbitMQ integration events with idempotent consumers.

## Rules During Extraction

- A service may write only its owned tables.
- Cross-service writes should be commands or events, not direct repository calls.
- Cross-service reads should use APIs or local read models.
- Keep frontend paths stable behind the gateway.
- Migrations move with the service that owns the table.
