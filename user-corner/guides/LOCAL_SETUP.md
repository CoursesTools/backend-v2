# Local Development Setup (Windows 11)

## Prerequisites

**Java 17**
- Download from https://adoptium.net/ (Temurin JDK 17)
- Run the installer and check "Set JAVA_HOME" during setup
- Verify: `java -version`

**Docker Desktop**
- Download from https://www.docker.com/products/docker-desktop/
- Start Docker Desktop and make sure it is running before proceeding

---

## 1. Configure Environment

Copy the `.env.example` to `.env` in the project root and fill in the required values:

```env
# Database
POSTGRES_USER=ct_user
POSTGRES_PASSWORD=ct_pass
POSTGRES_DB_NAME=ct_db

# Redis
REDIS_PASSWORD=ct_redis_pass

# ... see section 2 for remaining variables
```

---

## 2. Start PostgreSQL and Redis

```bash
docker compose up -d postgres redis
```

This starts PostgreSQL (exposed on port **5433**) and Redis using the project's `docker-compose.yml`.

Verify both containers are running: `docker ps`

---

## 3. Set Environment Variables

Add the remaining variables to your `.env` file, or set them in your IDE run configuration.

**IntelliJ:** Run > Edit Configurations > Environment Variables

### Required Variables

```env
# Database (port 5433 — mapped by docker-compose)
POSTGRES_HOST=localhost
POSTGRES_PORT=5433
POSTGRES_DB_NAME=ct_db
POSTGRES_USER=ct_user
POSTGRES_PASSWORD=ct_pass

# Redis
REDIS_HOST=localhost
REDIS_PASSWORD=ct_redis_pass

# JWT
JWT_SECRET=your-very-long-secret-key-at-least-256-bits

# OAuth
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GOOGLE_REDIRECT_URI=...
DISCORD_CLIENT_ID=...
DISCORD_CLIENT_SECRET=...
DISCORD_REDIRECT_URI=...

# App URLs
WEB_CLIENT_URL=http://localhost:3000
WEB_RECOVERY_URL=/recovery
WITHDRAWAL_URL=http://...
ACTIVATING_BOT_URL=http://...
CHANGE_TRADINGVIEW_BOT_URL=http://...
TELEGRAM_BOT_URL=http://...
ALERT_BOT_URL=http://...

# CORS
CORS_WEB_DOMAINS=http://localhost:3000

# Cookie (use these for local dev)
COOKIE_SECURE=false
COOKIE_SAME_SITE=Lax
```

> Bot and OAuth URLs can be stubbed with placeholder values if you don't need those flows locally. The app will start, but those features will not work.

---

## 4. Run the Application

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Flyway will automatically run all database migrations on first start.

### Available endpoints

| Resource  | URL                                          |
|-----------|----------------------------------------------|
| API       | http://localhost:8080/api                    |
| Swagger UI| http://localhost:8080/api/swagger-ui.html    |
| API Docs  | http://localhost:8080/api/api-docs           |

---

## Docker Compose — Full Stack

### Build & start everything

Build the JAR first, then bring up all services (PostgreSQL, Redis, backend):

Win 10/11:
```bash
gradlew bootJar && docker compose up -d --build
```

Linux/macOS:
```bash
./gradlew bootJar && docker compose up -d --build
```

The backend will be available at `http://localhost:8080/api`.

### Rebuild after code changes

```bash
# Win
gradlew bootJar && docker compose up -d --build backend

# Linux/macOS
./gradlew bootJar && docker compose up -d --build backend
```

### Stop everything

```bash
docker compose down
```

### Stop and wipe all data (volumes)

```bash
docker compose down -v
```

> Use `-v` only if you want a clean DB — it deletes all Postgres and Redis data.

### View logs

```bash
docker compose logs -f backend      # backend only
docker compose logs -f              # all services
```

---

## Checklist

| Step                  | Command / Tool                        | Verify with         |
|-----------------------|---------------------------------------|---------------------|
| Java 17 installed     | Adoptium installer                    | `java -version`     |
| Docker Desktop running| docker.com installer                  | Docker icon in tray |
| `.env` configured     | Copy `.env.example` and fill values   | —                   |
| DB & Redis started    | `docker compose up -d postgres redis` | `docker ps`         |
| Env vars set          | IDE or `.env`                         | —                   |
| App started           | `./gradlew bootRun`                   | `Started CoursesToolsApplication` in logs |

---

## Git Workflow

### Stage only tracked files (skip untracked)

```bash
git add -u
```

> `-u` stages modifications and deletions on already-tracked files only. New/untracked files are left out.

### Stage specific files

```bash
git add src/main/java/com/winworld/coursestools/controller/AuthController.java
git add .gitignore
```

### Unstage a file (keep changes, remove from commit)

```bash
git restore --staged <file>
```

### Revert a file to last commit (discard local changes)

```bash
git restore <file>
```

> This is destructive — your local edits to that file will be lost.

### Revert all tracked changes at once

```bash
git restore .
```

> Only affects tracked files. Untracked files are untouched.

### Safely push to production

1. Make sure you're on the right branch and everything is committed:
   ```bash
   git status
   git log --oneline -5
   ```

2. Pull latest from remote to avoid conflicts:
   ```bash
   git pull origin master
   ```

3. Push:
   ```bash
   git push origin master
   ```

> Never use `--force` on `master`. If push is rejected, resolve with `git pull` first.
