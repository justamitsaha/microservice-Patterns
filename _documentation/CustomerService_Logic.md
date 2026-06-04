# Customer Service Business Logic

The `customerService` manages customer profiles and acts as an aggregator to provide a unified view of a customer and their purchase history.

---

## 1. Domain Model: `CustomerEntity`
Each customer is represented by:
*   **Identity:** Unique `id` (UUID) and `email`.
*   **Security:** `passwordHash` and `passwordSalt` (using PBKDF2).
*   **Metadata:** `name` and `createdAt`.

---

## 2. Core Workflows

### A. Customer Registration
1.  **Input:** Takes name, email, and plain-text password.
2.  **Security Processing:** Performs **65,536 iterations** of PBKDF2 hashing.
    *   *Implementation Note:* This is CPU-intensive and is offloaded to the **`boundedElastic`** scheduler to avoid blocking the Reactor event loop.
3.  **Persistence:** Saves the entity to the database via R2DBC.
4.  **Error Handling:** Detects duplicate emails and returns a `409 Conflict`.

### B. Login & Authentication
1.  **Validation:** Fetches user by email. Re-computes the hash using the stored salt and compares it in **constant-time** to prevent timing attacks.
2.  **JWT Issuance:**
    *   **Access Token:** Short-lived token for API authentication.
    *   **Refresh Token:** Long-lived token stored in an **HttpOnly, Secure cookie** for session renewal.

### C. Data Aggregation (`getWithOrders`)
This is the service's primary business function. When a customer is fetched by ID:
1.  **Local Fetch:** Retrieves customer profile from the local DB.
2.  **Remote Fetch:** Concurrently calls the **Order Service** (`GET /orders?customerId=...`) via a load-balanced `WebClient`.
3.  **Resilience:** The remote call is wrapped in a Resilience4j pipeline:
    *   **Retry:** 2 attempts on network errors.
    *   **Circuit Breaker:** Stops calls if the Order Service is unstable.
    *   **Bulkhead:** Limits concurrent requests to Order Service.
    *   **Timeout:** Fails fast after 2 seconds.
4.  **Fallback:** If the Order Service call fails, it returns a placeholder order (`SERVICE_UNAVAILABLE`) so the customer profile can still be displayed.

---

## 3. Key Components
*   **`CustomerController`**: Exposes REST endpoints and handles WebFlux return types.
*   **`CustomerService`**: Orchestrates the hashing, aggregation logic, and resilience patterns.
*   **`CustomerRepository`**: Interface for reactive R2DBC database operations.
*   **`GlobalExceptionHandler`**: Standardizes error responses across the service.
