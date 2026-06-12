1. List topics
    ```bash
    docker exec -it kafka1 kafka-topics --list --bootstrap-server kafka1:9092
    ```
   Topics:
   - __consumer_offsets
   - _schemas
   - springCloudBus
   - config-bus-topic
   - order.events
   - order.events.dlt
   - order.events.proto
   - order.events.retry
2. Check Specific topic