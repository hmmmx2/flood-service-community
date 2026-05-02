# ─────────────────────────────────────────────
# Stage 1: Build the Spring Boot JAR
# ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml ./
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -Dmaven.test.skip=true -q

# ─────────────────────────────────────────────
# Stage 2: Minimal runtime image
# ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

USER root
RUN apk add --no-cache curl

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/target/*.jar app.jar

# Container-aware JVM: uses 75% of the cgroup memory limit instead of host RAM.
# -Djava.security.egd speeds up startup by using /dev/urandom for token generation.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Djava.net.preferIPv4Stack=true"

USER appuser

# Railway injects PORT at runtime; expose 8080 as a documentation hint only.
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${PORT:-8080}/actuator/health" || exit 1

# PORT is injected by Railway at runtime; application.yml reads server.port: ${PORT:4001}
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
