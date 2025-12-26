# Dynamic updates to configuration properties in Spring Cloud Config Server
1. Add these in the pom.xml of Config Server
   ```xml
           <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bus-kafka</artifactId>
        </dependency>
   ```
2. In the application.properties of Config Server, add these properties
   ```properties
    spring.cloud.bus.enabled=${SPRING_CLOUD_BUS_ENABLED:true}
    spring.cloud.bus.refresh.enabled=${SPRING_CLOUD_BUS_REFRESH_ENABLED:true}
    spring.cloud.stream.kafka.binder.brokers=${SPRING_KAFKA_BOOTSTRAP:localhost:9092,localhost:9093,localhost:9094}
   ```
3. In the client application, add these in the pom.xml
   ```xml
           <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bus-kafka</artifactId>
        </dependency>
   ```
4. Both Config Server and client must have Actuator.
    ```xml
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    ```
    Without this, /actuator/busrefresh will not work.

5. Annotate the field in the client application with `@RefreshScope`
      ```java
       @Slf4j
       @RestController
       @RequestMapping("/customers")
       @RequiredArgsConstructor
       @RefreshScope
       public class CustomerController {
    
           private final CustomerService service;
           private final CustomerRepository repository;
           private final CustomerServiceUtil customerServiceUtil;
    
           @Value("${app.simulation.num:0}")
           private int num;
       }
      ```
6. In the application.properties of client application, add these properties
    ```properties
    spring.cloud.bus.enabled=${SPRING_CLOUD_BUS_ENABLED:true}
    spring.cloud.bus.refresh.enabled=${SPRING_CLOUD_BUS_REFRESH_ENABLED:true}
    spring.cloud.stream.kafka.binder.brokers=${SPRING_CLOUD_STREAM_KAFKA_BROKERS:localhost:9092,localhost:9093,localhost:9094}
   
    management.endpoints.web.exposure.include=busrefresh,refresh

    app.simulation.num=${APP_SIMULATION_NUM:5}
    ```
7. Make changes to the properties file in Config Server repository and commit the changes
8. Hit the bus refresh endpoint of Config Server to propagate the changes to all client applications
   ```curl -X POST http://localhost:8888/actuator/busrefresh```
9. Verify the changes in the client application ```http://localhost:8082/customers/public/success```

# Encrypting values in Config Server
1. Step 1 encrypt
    ```curl -X POST http://localhost:8888/encrypt -H "Content-Type: text/plain" -d "10"```

2. Step 1 decrypt to verify 
     ```curl -X POST http://localhost:8888/decrypt -H "Content-Type: text/plain" -d "faf6fb5b51f0cb840fa5104b7a85a6bbbc8e9980da059dff5a6b6febda3e60f7"```

3.  Step 2 Set value in properties file

4.  Step 3 Do bus refresh
     ```curl -X POST http://localhost:8888/actuator/busrefresh```