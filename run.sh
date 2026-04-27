#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
#  Flood Monitoring Java Backend — Linux/macOS startup script
#  Usage: chmod +x run.sh && ./run.sh
# ─────────────────────────────────────────────────────────────────────────────

# Fill in your Neon JDBC connection string
export DATABASE_URL="jdbc:postgresql://ep-xxxx.region.neon.tech/neondb?sslmode=require&user=YOUR_USER&password=YOUR_PASSWORD"

# JWT secrets (same as Node.js backend)
export JWT_SECRET="cbb4c03bd27391b6406a3643c2908c5105250d6ddb676500b3876219befadc2cdf1f2f89d27ad28cb15b53269fc60f64ce512a5badf26375be21817cd65e5bd3"
export JWT_REFRESH_SECRET="562ac186d51dce69fc95e2a6242a71cb931796793787c38f8e2c801a6963fa97471d1f2991efb891291f3cf46d1d04e9e0cc9efd3da2c98278ecfca072a08a16"

export PORT=3001
export NODE_ENV=development

echo ""
echo " Flood Monitoring Java Backend"
echo " ────────────────────────────────────────────────"
echo " Starting on http://localhost:$PORT"
echo " Press Ctrl+C to stop"
echo ""

mvn spring-boot:run
