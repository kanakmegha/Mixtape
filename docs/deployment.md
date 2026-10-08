# Mixtape Deployment Guide (Phase 6)

This document outlines the strategy for deploying the Mixtape backend API (Go)
and database (PostgreSQL) for production, using Vercel and Neon.

## Architecture

- **Android App:** Local-first music player that extracts audio to local
  storage. Connects to the backend via HTTPS for syncing.
- **Vercel (Production Host):** Serves the Go backend API using a Dockerized
  Vercel container deployment.
- **Neon (Production DB):** Serverless Managed PostgreSQL instance for
  production data.
- **Docker Compose (Local Dev):** Development stack running Go, Postgres, and
  optionally Redis.

The Go application exposes the following essential features:

- Auth (JWT Registration/Login/Refresh)
- Admin Invitations
- Sync (Metadata/Playlists) _Note: Audio files NEVER go to the backend._

---

## 1. Setting up Neon (PostgreSQL)

Neon is a serverless Postgres platform.

1. Create a Neon Project:
   - Navigate to [neon.tech](https://neon.tech) and create a project.
   - Name the database `mixtape`.
   - Obtain the connection string. It will look like
     `postgres://<user>:<password>@<host>/mixtape?sslmode=require`.

2. Connection Pooling:
   - Neon includes a built-in PgBouncer pooler. You can use the pooled
     connection string (usually ending with
     `?sslmode=require&options=project%3D...`) if desired, but keep the Go
     connection limits low since Vercel may spawn multiple containers or
     serverless instances.
   - The Go backend limits connections using `DB_MAX_OPEN_CONNECTIONS`.

3. Migrations:
   - Ensure the API hits the DB once locally or via an ad-hoc run to execute the
     `migrate` logic on boot, or use a tool like `golang-migrate`. The current
     Go code automatically runs migrations on startup, so the first API instance
     that boots up will migrate the database.

---

## 2. Deploying to Vercel (Go API)

Vercel will host the Go backend using Vercel's container deployment feature via
`Dockerfile.vercel`.

1. In the Vercel Dashboard, create a new project and import your GitHub
   repository.
2. In the project settings, configure the following:
   - **Framework Preset**: Other
   - **Root Directory**: Select the root folder or leave empty if
     `Dockerfile.vercel` is in the root.
   - If Vercel defaults to Native Go (`api/*.go`), you must change it to deploy
     via Docker using `Dockerfile.vercel`. You can also rename
     `Dockerfile.vercel` to `Dockerfile` temporarily during deployment if Vercel
     specifically requires it.

3. **Environment Variables**: Set the following variables in Vercel Project
   Settings -> Environment Variables:
   - `APP_ENV=production`
   - `DATABASE_URL=<your-neon-connection-string>`
   - `ACCESS_TOKEN_SECRET=<secure-random-string>`
   - `REFRESH_TOKEN_SECRET=<secure-random-string>`
   - `CORS_ALLOWED_ORIGINS=https://mixtape-api.vercel.app` (Or whatever the
     deployed domain is)

   _(Optional Connection Pool Limits)_:
   - `DB_MAX_OPEN_CONNECTIONS=10`
   - `DB_MAX_IDLE_CONNECTIONS=2`

4. Deploy:
   - Vercel will build the Docker container and start the HTTP server.
   - The Go API automatically reads the Vercel-injected `$PORT` variable and
     binds to `0.0.0.0:$PORT`.

5. Verify:
   - Visit `https://<your-vercel-domain>/health`. It should return `OK`.

---

## 3. Configuring the Android App

The Android application needs to point to your new Vercel production URL.

1. Open `android/app/build.gradle.kts`.
2. In the `buildTypes { release { ... } }` block, locate `API_BASE_URL` and
   update it:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"https://<your-vercel-domain>.vercel.app/api/\"")
   ```
3. Build the release APK/AAB (`./gradlew assembleRelease`).
4. Install on a physical device.
5. Launch the app and attempt to register using an invitation code!

---

## 4. Local Development

To run the stack locally, use Docker Compose:

```bash
docker compose up --build
```

- This will start the Go API on `localhost:8081` and a local Postgres instance
  on `5432`.
- The Android app defaults to `http://10.0.2.2:8081/api/` in `debug` mode.

_Reminder: Never commit `.env` or production credentials to Git._
