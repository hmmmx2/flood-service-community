@echo off
REM ─────────────────────────────────────────────────────────────────────────────
REM  Flood Monitoring Java Backend — Windows Startup Script
REM
REM  BEFORE RUNNING:
REM    1. Fill in DATABASE_URL below with your Neon JDBC connection string
REM       (see neon.tech → your project → Connection Details → JDBC)
REM    2. Java 17+ must be installed  (java -version to check)
REM    3. Internet access required on first run to download Maven
REM
REM  NEON JDBC FORMAT:
REM    jdbc:postgresql://HOST/DBNAME?sslmode=require&user=USER&password=PASS
REM ─────────────────────────────────────────────────────────────────────────────

REM ── DATABASE CONNECTION — use your Neon JDBC string (never commit real values) ─
REM Example: jdbc:postgresql://HOST/neondb?sslmode=require^&channel_binding=require^&user=USER^&password=PASSWORD
SET DATABASE_URL=jdbc:postgresql://YOUR_NEON_HOST/neondb?sslmode=require^&channel_binding=require^&user=neondb_owner^&password=YOUR_PASSWORD
REM ─────────────────────────────────────────────────────────────────────────────

SET JWT_SECRET=replace_with_hex_secret_same_as_crm_service
SET JWT_REFRESH_SECRET=replace_with_second_distinct_hex_secret
SET PORT=4001
SET NODE_ENV=development

echo.
echo  ╔══════════════════════════════════════════════╗
echo  ║   Flood Community Service                    ║
echo  ║   Spring Boot 3.2  ·  Java 17+               ║
echo  ╚══════════════════════════════════════════════╝
echo  Starting on http://localhost:%PORT%
echo  Press Ctrl+C to stop
echo.

call mvnw.cmd spring-boot:run -Dmaven.test.skip=true
