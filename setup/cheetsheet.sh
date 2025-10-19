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
docker exec -it kafka1 kafka-console-consumer \
  --bootstrap-server kafka1:19092 \
  --topic order.events \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property print.headers=true


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