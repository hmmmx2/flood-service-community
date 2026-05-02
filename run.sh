#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
#  Flood Monitoring Java Backend — Linux/macOS startup script
#  Usage: chmod +x run.sh && ./run.sh
# ─────────────────────────────────────────────────────────────────────────────

# Fill in your Neon JDBC connection string (never commit real passwords)
export DATABASE_URL="jdbc:postgresql://YOUR_NEON_HOST/neondb?sslmode=require&user=YOUR_USER&password=YOUR_PASSWORD"

# JWT secrets — must match flood-service-crm and your NextAuth/AUTH setup
export JWT_SECRET="replace_with_hex_secret_same_as_crm_service"
export JWT_REFRESH_SECRET="replace_with_second_distinct_hex_secret"

export PORT=4001
export NODE_ENV=development

echo ""
echo " Flood Monitoring Java Backend"
echo " ────────────────────────────────────────────────"
echo " Starting on http://localhost:$PORT"
echo " Press Ctrl+C to stop"
echo ""

mvn spring-boot:run
