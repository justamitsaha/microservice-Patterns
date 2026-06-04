# Spring Cloud Config Server Documentation

Centralized configuration provides a single source of truth for all microservices, allowing properties to be managed externally and updated at runtime without restarting services.

---

## 🏗️ Architecture Overview

1.  **Config Server (`configService`)**: Connects to a Git repository (or local storage in `native` mode) to serve properties.
2.  **Config Clients**: Microservices (Order, Customer, Gateway) fetch their properties on startup.
3.  **Spring Cloud Bus**: Uses **Kafka** as a message broker to broadcast "refresh" events to all connected clients when a property changes.

---

## 🛠️ Setup Guide

### 1. Config Server Configuration
*   **Dependency:** Ensure `spring-cloud-config-server` and `spring-cloud-starter-bus-kafka` are in `pom.xml`.
*   **Properties:** Set the Git repository URI and enable the Bus.
    ```properties
    spring.cloud.config.server.git.uri=https://github.com/justamitsaha/configurationServer.git
    spring.cloud.bus.enabled=true
    spring.cloud.stream.kafka.binder.brokers=localhost:9092
    ```

### 2. Client Application Configuration
*   **Dependency:** Add `spring-cloud-starter-config` and `spring-cloud-starter-bus-kafka`.
*   **Bootstrap:** Clients must point to the server in their local `application.properties`:
    ```properties
    spring.config.import=optional:configserver:http://localhost:8888
    ```
*   **Actuator:** Both server and clients must expose necessary endpoints:
    ```properties
    management.endpoints.web.exposure.include=health,info,refresh,busrefresh
    ```

---

## 🔄 Dynamic Runtime Updates

To update properties without a restart, follow these steps:

### Step 1: Annotate Code
Use **`@RefreshScope`** on Spring Beans (Controllers or Services) that use `@Value` or `@ConfigurationProperties`.
```java
@RestController
@RefreshScope
public class MyController {
    @Value("${app.message}")
    private String message;
}
```

### Step 2: Update Config Source
Modify the property file in the Git repository and **commit/push** the changes.

### Step 3: Trigger Refresh
Hit the **`/actuator/busrefresh`** endpoint on the **Config Server**. This will send a message via Kafka to all clients.
```bash
curl -X POST http://localhost:8888/actuator/busrefresh
```

### Step 4: Verify
Check the client API (e.g., `http://localhost:8082/customers/public/success`) to see the updated value.

---

## 🔐 Secrets Management (Encryption)

The Config Server can encrypt sensitive values (like DB passwords) so they are not stored in plain text in Git.

### 1. Setup Encryption Key
The server requires a symmetric key set in its environment or properties:
```properties
encrypt.key=45D81EC1EF61DF9AD8D3E5BB397F9
```

### 2. Encrypt a Value
Use the `/encrypt` endpoint:
```bash
curl -X POST http://localhost:8888/encrypt -H "Content-Type: text/plain" -d "my-secret-password"
```
*Result:* Returns a cipher string like `faf6fb5b...`

### 3. Store in Config File
Add the encrypted value to your `.properties` file with the **`{cipher}`** prefix:
```properties
spring.r2dbc.password={cipher}faf6fb5b51f0cb840fa5104b7a85a6bbbc8e9980da059dff5a6b6febda3e60f7
```

### 4. Automatic Decryption
When a client fetches this property, the Config Server **automatically decrypts it** and sends the plain text value to the client over the secure connection.

---

## 🔍 Troubleshooting

*   **404 on Bus Refresh:** Ensure the client has `spring-cloud-starter-bus-kafka` and Actuator is properly configured.
*   **Kafka Connection:** If the refresh hangs, check if Kafka is running (`localhost:9092`).
*   **Native Mode:** To use local files instead of Git, run the server with the `native` profile:
    ```bash
    --spring.profiles.active=native --spring.cloud.config.server.native.search-locations=file:///path/to/configs
    ```
