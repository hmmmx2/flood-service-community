# Flood Community Service

Spring Boot microservice backend for the Community platform. Serves both the `flood-website-community` web portal and the `flood-mobile-community` mobile app.

## Responsibility

Handles everything community users need: authentication, user profiles, community posts and groups, blogs, alerts feed, favourites, safety content, and push notification subscriptions.

## Tech Stack

- **Java 21** + **Spring Boot 3.2**
- **PostgreSQL 16** — primary database (`flood_community` schema)
- **Redis 7** — token cache, rate limiting, session store
- **Spring Security** + **JWT** — authentication and authorisation
- **Maven** — build tool

## Port

Runs on **http://localhost:4001** by default.

## Prerequisites

- Java 21+
- Maven (or use `mvnw` wrapper)
- Docker + Docker Compose (for local PostgreSQL + Redis)

## Quick Start (with Docker)

```bash
git clone <repo-url>
cd flood-service-community

cp .env.example .env
# Edit .env — set JWT_SECRET and JWT_REFRESH_SECRET

docker-compose up --build
```

API is available at **http://localhost:4001**

## Quick Start (without Docker)

You need a local PostgreSQL and Redis running. Then:

```bash
cp .env.example .env
# Set DATABASE_URL to your local Postgres, REDIS_URL to your local Redis

# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/login` | User login |
| POST | `/auth/register` | User registration |
| POST | `/auth/refresh` | Refresh access token |
| POST | `/auth/forgot-password` | Request password reset |
| GET | `/feed` | Alert / event feed |
| GET | `/blogs` | Blog listing |
| GET | `/blogs/featured` | Featured blogs |
| GET/PATCH | `/profile` | User profile |
| GET/PATCH | `/settings` | User notification settings |
| GET/POST/DELETE | `/favourites` | Favourite sensor nodes |
| GET | `/safety` | Safety awareness content |

## Docker Compose Services

| Service | Container | Host Port |
|---------|-----------|-----------|
| Spring Boot API | `flood-community-api` | 4001 |
| PostgreSQL 16 | `flood-community-postgres` | 5433 |
| Redis 7 | `flood-community-redis` | 6380 |

## Environment Variables

See `.env.example` for all required variables.
