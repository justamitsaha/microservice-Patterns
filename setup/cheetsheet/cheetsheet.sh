###### Important Docker Commands
docker compose up -d --build  #After modifying docker-compose.yml
docker compose up -d #Start containers in detached mode
docker compose down #Stop and remove containers, networks, images, and volumes created by docker-compose up
docker compose ps #List containers
docker compose logs -f gateway-service #Follow logs of a specific container
docker exec -it kafka1 bash #Access the bash shell of a specific container
docker stats #Display a live stream of container resource usage statistics
docker compose -f docker-compose-observability.yaml down #Stop and remove containers for a specific compose file
docker compose -f docker-compose-observability.yaml up -d #Start containers for a specific
docker compose up -d --force-recreate --pull always web-app #Recreate containers and pull the latest images for web-app service
docker-compose restart SERVICE
docker-compose up -d --build --force-recreate SERVICE


######Kafka Topic Management
#1. Create topics
docker exec -it kafka1 kafka-topics \
  --create \
  --topic smsEvent \
  --bootstrap-server kafka1:9092 \
  --partitions 3 \
  --replication-factor 3

docker exec -it kafka1 kafka-topics \
  --create \
  --topic emailEvent \
  --bootstrap-server kafka1:9092 \
  --partitions 3 \
  --replication-factor 3

#3. Verify topics
docker exec -it kafka1 kafka-topics --list --bootstrap-server kafka1:9092

docker exec --interactive --tty kafka1  kafka-topics --bootstrap-server kafka1:19092 --describe --topic emailEvent
docker exec --interactive --tty kafka1  kafka-topics --bootstrap-server kafka1:19092 --describe --topic smsEvent

#consumer
docker exec -it kafka1 kafka-console-consumer   --bootstrap-server kafka1:19092   --topic order.events  \
 --from-beginning   --property print.key=true   --property print.value=true   --property print.headers=true

docker exec -it kafka1 kafka-console-consumer   --bootstrap-server kafka1:19092   --topic order.events.proto \
  --from-beginning   --property print.key=true   --property print.value=true   --property print.headers=true

#producer
docker exec -it kafka1 kafka-console-producer \
  --bootstrap-server kafka1:19092 \
  --topic emailEvent \
  --property "parse.key=true" \
  --property "key.separator=:"

1:{"paymentUuid":2001,"paymentStatus":"SUCCESS","amount":750,"createdDate":"2025-10-02T12:10:00","updatedDate":"2025-10-02T12:15:00"}

#without key
docker exec -it kafka1 kafka-console-producer \
  --bootstrap-server kafka1:19092 \
  --topic emailEvent

{"paymentUuid":2001,"paymentStatus":"SUCCESS","amount":750,"createdDate":"2025-10-02T12:10:00","updatedDate":"2025-10-02T12:15:00"}


docker exec -it kafka1 kafka-topics \
  --delete \
  --topic emailProducer-in-0 \
  --bootstrap-server kafka1:19092


curl -i -X OPTIONS http://localhost:8085/customers   -H "Origin: http://localhost:4200"
curl -i -X GET  http://localhost:8085/customers   -H "Origin: http://localhost:4200"
curl -i -X GET  http://localhost:8081/customers   -H "Origin: http://localhost:4200"

curl -X POST http://localhost:8080/actuator/busrefresh
