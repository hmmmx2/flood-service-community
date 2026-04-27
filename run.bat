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

REM ── DATABASE CONNECTION ───────────────────────────────────────────────────────
SET DATABASE_URL=jdbc:postgresql://ep-empty-wave-anxnq609-pooler.c-6.us-east-1.aws.neon.tech/neondb?sslmode=require^&channel_binding=require^&user=neondb_owner^&password=npg_wWQz47ALcopb
REM ─────────────────────────────────────────────────────────────────────────────

SET JWT_SECRET=cbb4c03bd27391b6406a3643c2908c5105250d6ddb676500b3876219befadc2cdf1f2f89d27ad28cb15b53269fc60f64ce512a5badf26375be21817cd65e5bd3
SET JWT_REFRESH_SECRET=562ac186d51dce69fc95e2a6242a71cb931796793787c38f8e2c801a6963fa97471d1f2991efb891291f3cf46d1d04e9e0cc9efd3da2c98278ecfca072a08a16
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
