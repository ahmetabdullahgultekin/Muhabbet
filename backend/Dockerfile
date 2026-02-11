# ─── Stage 1: Build ────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Gradle files first (cache dependencies layer)
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY gradlew ./
COPY backend/build.gradle.kts backend/
COPY shared/build.gradle.kts shared/

# Download dependencies (cached if build files unchanged)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code
COPY shared/src shared/src
COPY backend/src backend/src

# Build
RUN ./gradlew :backend:bootJar --no-daemon -x test

# ─── Stage 2: Runtime ─────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create non-root user
RUN groupadd -r muhabbet && useradd -r -g muhabbet muhabbet

# Copy JAR from builder
COPY --from=builder /app/backend/build/libs/*.jar app.jar

# Switch to non-root
USER muhabbet

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
