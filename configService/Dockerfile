# ================================
# 1️⃣ Build Stage
# ================================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom + download deps first (cache benefit)
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline

# Copy source and build the jar
COPY src ./src
RUN mvn -q -e -B clean package -DskipTests

# ================================
# 2️⃣ Run Stage (Slim JDK 21)
# ================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Expose Config Server port
EXPOSE 8888

# Add a non-root user for security
RUN addgroup --system spring && adduser -S spring -G spring
USER spring:spring

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# JVM optimization flags for containers
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75 \
  -XX:InitialRAMPercentage=50 \
  -Duser.timezone=UTC \
"

# For OTEL Auto-instrumentation (optional)
# ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Build
#docker build -t justamitsaha/config-service:v1 .

# Run (local test)
#docker run -p 8888:8888 justamitsaha/config-service:v1
