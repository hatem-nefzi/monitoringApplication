# Stage 1: Build
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

# Installer Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

COPY . .
RUN ./mvnw clean package -DskipTests && rm -rf ~/.m2

# Stage 2: Run
FROM openjdk:25-jdk-slim
WORKDIR /app

# Créer un utilisateur non-root
RUN addgroup --system appgroup && \
    adduser --system --ingroup appgroup --uid 1000 appuser

COPY --from=builder /app/target/monitoring-app-0.0.1-SNAPSHOT.jar app.jar

# Donner les droits à l'utilisateur non-root
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
