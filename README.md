# flood-service-community

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-007396?logo=java&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-Upstash-DC382D?logo=redis&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-black?logo=jsonwebtokens)
![License](https://img.shields.io/badge/license-MIT-blue)

> **REST API backend for the FloodWatch community portal and mobile app.** Handles user authentication, community posts, sensor node data, blogs, groups, favourites, safety content, and push notifications for residents and community members.

---

## Overview

`flood-service-community` is a Spring Boot 3 microservice powering both **flood-website-community** and **flood-mobile-app**. It exposes the public-facing APIs that community members (residents) use to:
- Register and authenticate
- Read and post flood-related community content
- Follow sensor node water levels in their area
- Save favourite nodes for quick monitoring
- Receive push notifications for flood alerts
- Access safety and evacuation guides

---

## Features

- **User Authentication** — JWT with access (15 min) + refresh tokens (7 days), ROLE_USER / ROLE_ADMIN
- **Community Posts** — Create, read, like, comment on, and share flood-related posts
- **Groups** — Join and participate in area-specific flood monitoring groups
- **Blog** — Read official flood safety and awareness articles
- **Sensor Nodes** — View live water level data from IoT nodes
- **Favourites** — Pin and monitor selected sensor nodes
- **Safety Content** — Curated flood safety tips and evacuation routes
- **Push Notifications** — Expo-compatible push token registration and alert delivery
- **User Settings** — Configurable preferences per user
- **Profile Management** — Update display name, avatar, and personal info

---

## Tech Stack

| Technology        | Version | Purpose                            |
|-------------------|---------|------------------------------------|
| Spring Boot       | 3.2     | Application framework              |
| Java              | 17      | Runtime                            |
| Spring Security   | 6.x     | JWT authentication & authorization |
| PostgreSQL (Neon) | 15      | Primary relational database        |
| Redis (Upstash)   | 7.x     | Token blacklist, session caching   |
| Maven             | 3.9     | Build tool                         |
| JUnit 5           | 5.x     | Unit & integration testing         |
| Lombok            | 1.18    | Boilerplate reduction              |

---

## Architecture

```
flood-website-community (Next.js :3002)
flood-mobile-app        (Expo / React Native)
         │
         ▼  REST / JWT
flood-service-community (Spring Boot :4001)
         │
         ├── PostgreSQL (Neon) — user data, posts, nodes
         └── Redis (Upstash)   — token store, cache
```

---

## Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL database (Neon cloud or local)
- Redis instance (Upstash cloud or local)

---

## Getting Started

### 1. Clone and configure

```bash
# From the monorepo root
cd flood-service-community
```

### 2. Set environment variables

Create a `.env` file or set these as system environment variables:

```bash
DATABASE_URL=jdbc:postgresql://<host>/<db>?sslmode=require
DATABASE_USERNAME=your_db_user
DATABASE_PASSWORD=your_db_password
JWT_SECRET=your-256-bit-jwt-secret-minimum-32-chars
JWT_REFRESH_SECRET=your-256-bit-refresh-secret
REDIS_URL=redis://default:<token>@<host>:6379
PORT=4001
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

Or skip tests for faster startup:

```bash
./mvnw spring-boot:run -Dmaven.test.skip=true
```

The API will be available at `http://localhost:4001`.

### 4. Run tests

```bash
./mvnw test
```

---

## Environment Variables

| Variable             | Description                          | Example                              |
|----------------------|--------------------------------------|--------------------------------------|
| `DATABASE_URL`       | JDBC URL for PostgreSQL              | `jdbc:postgresql://ep-xxx.neon.tech/floodcomm` |
| `DATABASE_USERNAME`  | Database username                    | `neondb_owner`                       |
| `DATABASE_PASSWORD`  | Database password                    | `your-password`                      |
| `JWT_SECRET`         | Secret for signing access tokens     | 32+ character random string          |
| `JWT_REFRESH_SECRET` | Secret for signing refresh tokens    | 32+ character random string          |
| `REDIS_URL`          | Redis connection string              | `redis://default:token@host:6379`    |
| `PORT`               | Server port                          | `4001`                               |

---

## API Endpoints

### Authentication

| Method | Path                       | Description                     | Auth Required |
|--------|----------------------------|---------------------------------|---------------|
| POST   | `/auth/register`           | Register new user account       | No            |
| POST   | `/auth/login`              | Login (returns JWT)             | No            |
| POST   | `/auth/refresh`            | Refresh access token            | No            |
| POST   | `/auth/logout`             | Invalidate refresh token        | Yes           |
| POST   | `/auth/forgot-password`    | Initiate password reset         | No            |
| POST   | `/auth/verify-reset-code`  | Verify password reset code      | No            |
| POST   | `/auth/reset-password`     | Complete password reset         | No            |

### Community Posts

| Method | Path                     | Description                     | Auth Required |
|--------|--------------------------|---------------------------------|---------------|
| GET    | `/feed`                  | Paginated community feed        | Optional      |
| POST   | `/posts`                 | Create a new post               | Yes           |
| GET    | `/posts/{id}`            | Get post by ID                  | Optional      |
| PUT    | `/posts/{id}`            | Update a post                   | Yes (Author)  |
| DELETE | `/posts/{id}`            | Delete a post                   | Yes (Author)  |
| POST   | `/posts/{id}/like`       | Toggle like on a post           | Yes           |
| POST   | `/posts/{id}/comments`   | Add a comment                   | Yes           |

### Groups

| Method | Path                     | Description                     | Auth Required |
|--------|--------------------------|---------------------------------|---------------|
| GET    | `/groups`                | List all groups                 | Optional      |
| POST   | `/groups`                | Create a new group              | Yes           |
| GET    | `/groups/{slug}`         | Get group by slug               | Optional      |
| POST   | `/groups/{slug}/join`    | Join a group                    | Yes           |

### Sensor Nodes

| Method | Path                     | Description                     | Auth Required |
|--------|--------------------------|---------------------------------|---------------|
| GET    | `/sensors`               | List all sensor nodes           | Optional      |
| GET    | `/sensors/{id}`          | Get single node data            | Optional      |

### Favourites

| Method | Path                     | Description                     | Auth Required |
|--------|--------------------------|---------------------------------|---------------|
| GET    | `/favourites`            | Get user's favourite nodes      | Yes           |
| POST   | `/favourites`            | Add a node to favourites        | Yes           |
| DELETE | `/favourites/{nodeId}`   | Remove from favourites          | Yes           |

### Blog

| Method | Path                     | Description                     | Auth Required |
|--------|--------------------------|---------------------------------|---------------|
| GET    | `/blogs`                 | List blog articles              | Optional      |
| GET    | `/blogs/{id}`            | Get blog by ID                  | Optional      |
| POST   | `/blogs`                 | Create blog article             | Yes (ADMIN)   |

### Push Notifications

| Method | Path                     | Description                     | Auth Required |
|--------|--------------------------|---------------------------------|---------------|
| POST   | `/push/register`         | Register Expo push token        | Yes           |
| DELETE | `/push/unregister`       | Remove push token               | Yes           |

### Profile & Settings

| Method | Path                     | Description                     | Auth Required |
|--------|--------------------------|---------------------------------|---------------|
| GET    | `/profile`               | Get current user profile        | Yes           |
| PUT    | `/profile`               | Update profile                  | Yes           |
| GET    | `/settings`              | Get user settings               | Yes           |
| PUT    | `/settings`              | Update settings                 | Yes           |

---

## Project Structure

```
flood-service-community/
├── src/
│   ├── main/
│   │   ├── java/com/fyp/floodmonitoring/
│   │   │   ├── config/          # Security, CORS, Redis config
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── dto/             # Request/response DTOs
│   │   │   ├── entity/          # JPA entities
│   │   │   ├── exception/       # Global exception handler
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   ├── security/        # JWT filter, auth provider
│   │   │   └── service/         # Business logic layer
│   │   └── resources/
│   │       └── application.yml  # App configuration
│   └── test/                    # Unit and integration tests
├── Dockerfile
└── pom.xml
```

---

## Docker

```bash
# Build and run with Docker Compose
docker-compose up --build
```

Or build the image manually:

```bash
docker build -t flood-service-community .
docker run -p 4001:4001 \
  -e DATABASE_URL=... \
  -e JWT_SECRET=... \
  flood-service-community
```

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'feat: add your feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

---

## License

MIT — see [LICENSE](LICENSE) for details.
