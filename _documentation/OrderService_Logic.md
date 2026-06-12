# Order Service Business Logic

The `reactiveOrderService` manages the lifecycle of orders and ensures reliable, event-driven communication with other services using the Transactional Outbox Pattern.

---

## 1. Domain Model: `OrderEntity`
Represents a purchase made by a customer:
*   **Attributes:** `orderId`, `customerId`, `amount`, `status` (e.g., PLACED), and `createdAt`.

---

## 2. Core Workflows

### A. Order Placement (Transactional Outbox)
The service implements the **Transactional Outbox Pattern** to ensure that local database updates and Kafka events are consistent.
1.  **Discount Logic:** Applies a configurable discount (fetched from Config Server via `@Value`) to the order amount.
2.  **Transaction:**
    *   Saves the `OrderEntity` to the database.
    *   Creates and saves an `OrderOutboxEntity` containing the serialized event payload.
3.  **Consistency:** By saving both to the same DB in one transaction, we guarantee that an event is only "queued" if the order is successfully saved.

### B. Reliable Event Dispatch (`OutboxPublisher`)
Events can be dispatched to Kafka in two ways:
1.  **Automatic Polling:** Every second (configurable), the `OutboxPublisher` polls for `PENDING` records and sends them to Kafka.
2.  **Manual Trigger (API):** The service exposes a `POST /orders/outbox/publish` endpoint. This allows external systems or the UI to force an immediate dispatch of all pending outbox records without waiting for the next poll cycle.

### C. Kafka Resilience (Retry & DLQ)
The service integrates with Kafka's built-in resilience features:
1.  **Retry Topic:** If a consumer fails to process the order event, it is sent to a `.retry` topic for another attempt.
2.  **Dead Letter Queue (DLQ):** After exhaustive retries, the event is moved to a `.dlt` topic for manual inspection or recovery.

---

## 3. Key Components
*   **`OrderController`**: Handles order placement and retrieval (filtered by customer).
*   **`OrderService`**: Contains the business rules (discounts, validation).
*   **`OutboxService`**: Manages the atomic transaction of Order + Outbox record.
*   **`OutboxPublisher`**: Reactive background component for reliable Kafka delivery.
*   **`OrderRepository` & `OrderOutboxRepository`**: Reactive interfaces for DB access.
