# Private Music Library

A local-first, private invite-only music library Android application.

## Overview
This application provides a Spotify-like music library experience. Users are invited to create an account, after which they can import audio from supported content URLs. The audio is extracted, converted, and saved entirely on the local device. The backend is merely an auth and metadata synchronization engine.

## Architecture

**Android Application (Kotlin / Compose):**
*   **Architecture:** Clean Architecture with MVVM
*   **Local DB:** Room
*   **Media Player:** Media3 / ExoPlayer with Foreground Service & MediaSession
*   **Dependency Injection:** Hilt
*   **Async/Background:** Kotlin Coroutines & WorkManager
*   **Storage:** Android Storage Access Framework

**Backend (Go / PostgreSQL):**
*   **Language:** Go 1.22
*   **Database:** PostgreSQL 16
*   **Authentication:** JWT-based
*   **Invite System:** Invite-only registration

## Requirements

### Android
*   Android Studio Ladybug or later
*   Min SDK: 26
*   Target SDK: 34

### Backend
*   Docker & Docker Compose
*   Go 1.22 (for local development)

## Setup

### 1. Backend Setup

```bash
cd private-music-library
docker-compose up -d
```
The backend API runs on port `8080` by default.

### 2. Android Setup

Open the `android/` directory in Android Studio.
Sync Gradle project.
Run the app on your device or emulator.

## License
Private
# Mixtape
