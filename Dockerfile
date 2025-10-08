# Stage 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Install Maven (Alpine uses apk)
RUN apk add --no-cache maven

COPY . .
RUN ./mvnw clean package -DskipTests && rm -rf ~/.m2

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Download OpenTelemetry Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.8.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY --from=builder /app/target/monitoring-app-0.0.1-SNAPSHOT.jar app.jar

# Give permissions to non-root user
RUN chown -R 1001:0 /app

# Use UID directly so Kubernetes can verify non-root
USER 1001

EXPOSE 9090

ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]