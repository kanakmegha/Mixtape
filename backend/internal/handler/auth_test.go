package handler_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/username/private-music-library/backend/internal/config"
	"github.com/username/private-music-library/backend/internal/database"
	"github.com/username/private-music-library/backend/internal/handler"
	"github.com/username/private-music-library/backend/internal/middleware"
	"github.com/username/private-music-library/backend/internal/models"
)

func setupTestDB(t *testing.T) *pgxpool.Pool {
	ctx := context.Background()
	os.Clearenv()
	
	// Create a fresh test database logic, or just truncate tables since it's a local container
	cfg := &config.Config{
		DatabaseURL: "postgres://postgres:postgres@127.0.0.1:5433/music_library?sslmode=disable",
		DBMaxOpenConns: 10,
		DBMaxIdleConns: 5,
		DBConnMaxLifetime: 15 * time.Minute,
		DBConnMaxIdleTime: 5 * time.Minute,
	}

	pool, err := database.NewPostgresPool(ctx, cfg)
	if err != nil {
		t.Fatalf("Failed to connect to test DB: %v", err)
	}

	err = database.RunMigrations(ctx, pool)
	if err != nil {
		t.Fatalf("Failed to run migrations: %v", err)
	}

	// Clean up for test
	_, err = pool.Exec(ctx, "TRUNCATE TABLE users, invitations, sessions, playlists, tracks, playlist_tracks CASCADE;")
	if err != nil {
		t.Fatalf("Failed to truncate tables: %v", err)
	}

	return pool
}

func TestFirstRegistrationIsAdmin(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	authHandler := handler.NewAuthHandler(pool, &config.Config{})

	reqBody := models.RegisterRequest{
		Email:       "admin@mixtape.local",
		Password:    "password123",
		DisplayName: "Admin",
	}
	bodyBytes, _ := json.Marshal(reqBody)

	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewBuffer(bodyBytes))
	rr := httptest.NewRecorder()

	authHandler.Register(rr, req)

	if rr.Code != http.StatusCreated {
		t.Fatalf("Expected 201 Created, got %d: %s", rr.Code, rr.Body.String())
	}

	var role string
	err := pool.QueryRow(context.Background(), "SELECT role FROM users WHERE email = $1", "admin@mixtape.local").Scan(&role)
	if err != nil {
		t.Fatalf("Failed to get user role: %v", err)
	}
	if role != "admin" {
		t.Fatalf("Expected role 'admin', got '%s'", role)
	}
}

func TestSecondRegistrationWithoutInviteFails(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	authHandler := handler.NewAuthHandler(pool, &config.Config{})

	// 1. Create first user
	pool.Exec(context.Background(), "INSERT INTO users (id, email, password_hash, display_name, role) VALUES ($1, 'admin@test.com', 'hash', 'admin', 'admin')", uuid.New())

	// 2. Try to create second user without invite
	reqBody := models.RegisterRequest{
		Email:       "user@mixtape.local",
		Password:    "password123",
		DisplayName: "User",
	}
	bodyBytes, _ := json.Marshal(reqBody)

	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewBuffer(bodyBytes))
	rr := httptest.NewRecorder()

	authHandler.Register(rr, req)

	if rr.Code != http.StatusForbidden {
		t.Fatalf("Expected 403 Forbidden, got %d", rr.Code)
	}
}

func TestConcurrentFirstRegistrations(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	authHandler := handler.NewAuthHandler(pool, &config.Config{})

	var wg sync.WaitGroup
	var numRequests = 10
	var successCount int
	var mu sync.Mutex

	for i := 0; i < numRequests; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			
			reqBody := models.RegisterRequest{
				Email:       "admin" + string(rune(i)) + "@mixtape.local",
				Password:    "password123",
				DisplayName: "Admin Candidate",
			}
			bodyBytes, _ := json.Marshal(reqBody)

			req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewBuffer(bodyBytes))
			rr := httptest.NewRecorder()

			authHandler.Register(rr, req)

			if rr.Code == http.StatusCreated {
				mu.Lock()
				successCount++
				mu.Unlock()
			}
		}(i)
	}

	wg.Wait()

	if successCount != 1 {
		t.Fatalf("Expected exactly 1 successful admin creation, got %d", successCount)
	}

	var adminCount int
	pool.QueryRow(context.Background(), "SELECT COUNT(*) FROM users WHERE role = 'admin'").Scan(&adminCount)
	if adminCount != 1 {
		t.Fatalf("Expected exactly 1 admin in DB, got %d", adminCount)
	}
}

func TestRegistrationWithValidInvite(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	// 1. Create admin
	adminID := uuid.New()
	pool.Exec(context.Background(), "INSERT INTO users (id, email, password_hash, display_name, role) VALUES ($1, 'admin@test.com', 'hash', 'admin', 'admin')", adminID)

	// 2. Create invite (token="testtoken", hash expected by manual calculation or just simulate handler)
	adminHandler := handler.NewAdminHandler(pool)
	
	reqBody := models.CreateInvitationRequest{
		ExpiresInDays: 30,
	}
	bodyBytes, _ := json.Marshal(reqBody)
	req := httptest.NewRequest("POST", "/invitations", bytes.NewBuffer(bodyBytes))
	ctx := context.WithValue(req.Context(), middleware.UserIDKey, adminID) // fake middleware
	req = req.WithContext(ctx)
	rr := httptest.NewRecorder()
	
	adminHandler.CreateInvitation(rr, req)
	
	if rr.Code != http.StatusCreated {
		t.Fatalf("Failed to create invite: %s", rr.Body.String())
	}
	
	var invRes models.InvitationResponse
	json.Unmarshal(rr.Body.Bytes(), &invRes)

	// 3. Register user with this token
	authHandler := handler.NewAuthHandler(pool, &config.Config{})
	regReq := models.RegisterRequest{
		Email:           "user@mixtape.local",
		Password:        "password123",
		DisplayName:     "User",
		InvitationToken: invRes.Token,
	}
	regBodyBytes, _ := json.Marshal(regReq)

	req2 := httptest.NewRequest("POST", "/api/auth/register", bytes.NewBuffer(regBodyBytes))
	rr2 := httptest.NewRecorder()

	authHandler.Register(rr2, req2)

	if rr2.Code != http.StatusCreated {
		t.Fatalf("Expected 201 Created, got %d: %s", rr2.Code, rr2.Body.String())
	}

	var role string
	pool.QueryRow(context.Background(), "SELECT role FROM users WHERE email = 'user@mixtape.local'").Scan(&role)
	if role != "user" {
		t.Fatalf("Expected role 'user', got '%s'", role)
	}

	// 4. Verify invite is used
	var usedAt *time.Time
	pool.QueryRow(context.Background(), "SELECT used_at FROM invitations WHERE id = $1", invRes.ID).Scan(&usedAt)
	if usedAt == nil {
		t.Fatalf("Expected used_at to be set")
	}

	// 5. Try reusing token
	req3 := httptest.NewRequest("POST", "/api/auth/register", bytes.NewBuffer(regBodyBytes))
	rr3 := httptest.NewRecorder()
	authHandler.Register(rr3, req3)

	if rr3.Code != http.StatusForbidden {
		t.Fatalf("Expected 403 Forbidden on reuse, got %d", rr3.Code)
	}
}

func TestUserCanCreateInvite(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	// 1. Create a regular user
	userID := uuid.New()
	pool.Exec(context.Background(), "INSERT INTO users (id, email, password_hash, display_name, role) VALUES ($1, 'user@test.com', 'hash', 'user', 'user')", userID)

	// 2. User creates invite
	adminHandler := handler.NewAdminHandler(pool)
	
	reqBody := models.CreateInvitationRequest{
		ExpiresInDays: 7,
	}
	bodyBytes, _ := json.Marshal(reqBody)
	req := httptest.NewRequest("POST", "/invitations", bytes.NewBuffer(bodyBytes))
	ctx := context.WithValue(req.Context(), middleware.UserIDKey, userID) 
	req = req.WithContext(ctx)
	rr := httptest.NewRecorder()
	
	adminHandler.CreateInvitation(rr, req)
	
	if rr.Code != http.StatusCreated {
		t.Fatalf("Failed to create invite as normal user: %s", rr.Body.String())
	}
}
