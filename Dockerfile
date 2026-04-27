# ─────────────────────────────────────────────
# Stage 1: Build the Spring Boot JAR
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer cache — only re-downloads deps when pom changes)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ─────────────────────────────────────────────
# Stage 2: Minimal runtime image
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose API port
EXPOSE 3001

# Health check — hits Spring Actuator endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:3001/actuator/health || exit 1

# Run the JAR
ENTRYPOINT ["java", "-jar", "-Dserver.port=3001", "app.jar"]
