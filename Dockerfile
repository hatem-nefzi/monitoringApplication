# Stage 1: Build
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests && rm -rf ~/.m2/repository


# Stage 2: Run
FROM openjdk:25-jdk-slim
WORKDIR /app
COPY --from=builder /app/target/monitoring-app-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]

