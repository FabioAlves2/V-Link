# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/*.jar app.jar
# logback-spring.xml's rolling file appender (prod profile) writes to ./logs, and
# FileStorageService writes event images under app.upload.dir (/app/uploads, see
# docker-compose.yml) — both directories must exist and be owned by the non-root user
# before the app starts, or Docker auto-creates them as root when the volume mounts.
RUN mkdir -p /app/logs /app/uploads && chown -R spring:spring /app
USER spring

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
