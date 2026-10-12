package handler_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/username/private-music-library/backend/internal/handler"
	"github.com/username/private-music-library/backend/internal/middleware"
	"github.com/username/private-music-library/backend/internal/models"
)

func createTestUser(t *testing.T, pool *pgxpool.Pool, email string) uuid.UUID {
	id := uuid.New()
	_, err := pool.Exec(context.Background(),
		"INSERT INTO users (id, email, password_hash, display_name) VALUES ($1, $2, 'hash', 'User')",
		id, email)
	if err != nil {
		t.Fatalf("Failed to create test user: %v", err)
	}
	return id
}

func reqWithUser(req *http.Request, userID uuid.UUID) *http.Request {
	ctx := context.WithValue(req.Context(), middleware.UserIDKey, userID)
	return req.WithContext(ctx)
}

func TestSync_CreatePlaylistAndItems(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	syncHandler := handler.NewSyncHandler(pool)
	userID := createTestUser(t, pool, "syncuser1@mixtape.local")

	playlistID := uuid.New().String()
	artist := "Test Artist"
	album := "Test Album"
	duration := 180

	reqPayload := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{
				ID:        playlistID,
				Name:      "Roadtrip Tunes",
				UpdatedAt: time.Now().UnixMilli(),
				Tracks: []models.PlaylistTrackSyncPayload{
					{
						TrackKey: "youtube:dQw4w9WgXcQ",
						Position: 0,
						Title:    "Never Gonna Give You Up",
						Artist:   &artist,
						Album:    &album,
						Duration: &duration,
					},
				},
			},
		},
		Favorites: []models.FavoriteSyncPayload{
			{
				TrackKey:   "youtube:dQw4w9WgXcQ",
				Title:      "Never Gonna Give You Up",
				Artist:     &artist,
				IsFavorite: true,
				UpdatedAt:  time.Now().UnixMilli(),
			},
		},
	}

	bodyBytes, _ := json.Marshal(reqPayload)
	req := httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bodyBytes))
	req = reqWithUser(req, userID)
	rr := httptest.NewRecorder()

	syncHandler.Sync(rr, req)

	if rr.Code != http.StatusOK {
		t.Fatalf("Expected 200 OK, got %d: %s", rr.Code, rr.Body.String())
	}

	var resp models.SyncResponse
	if err := json.NewDecoder(rr.Body).Decode(&resp); err != nil {
		t.Fatalf("Failed to decode response: %v", err)
	}

	if len(resp.Playlists) != 1 {
		t.Fatalf("Expected 1 playlist, got %d", len(resp.Playlists))
	}
	if resp.Playlists[0].Name != "Roadtrip Tunes" {
		t.Errorf("Expected name 'Roadtrip Tunes', got '%s'", resp.Playlists[0].Name)
	}
	if len(resp.Playlists[0].Tracks) != 1 {
		t.Fatalf("Expected 1 track in playlist, got %d", len(resp.Playlists[0].Tracks))
	}
	if resp.Playlists[0].Tracks[0].TrackKey != "youtube:dQw4w9WgXcQ" {
		t.Errorf("Expected track key 'youtube:dQw4w9WgXcQ', got '%s'", resp.Playlists[0].Tracks[0].TrackKey)
	}

	if len(resp.Favorites) != 1 {
		t.Fatalf("Expected 1 favorite, got %d", len(resp.Favorites))
	}
	if !resp.Favorites[0].IsFavorite {
		t.Errorf("Expected IsFavorite to be true")
	}
}

func TestSync_IdempotentRetry(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	syncHandler := handler.NewSyncHandler(pool)
	userID := createTestUser(t, pool, "retry@mixtape.local")

	playlistID := uuid.New().String()
	now := time.Now().UnixMilli()

	reqPayload := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{
				ID:        playlistID,
				Name:      "Workout Beats",
				UpdatedAt: now,
				Tracks: []models.PlaylistTrackSyncPayload{
					{TrackKey: "hash:12345", Position: 0, Title: "Track One"},
					{TrackKey: "hash:67890", Position: 1, Title: "Track Two"},
				},
			},
		},
		Favorites: []models.FavoriteSyncPayload{
			{TrackKey: "hash:12345", Title: "Track One", IsFavorite: true, UpdatedAt: now},
		},
	}

	bodyBytes, _ := json.Marshal(reqPayload)

	// Send Request 1
	req1 := reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bodyBytes)), userID)
	rr1 := httptest.NewRecorder()
	syncHandler.Sync(rr1, req1)
	if rr1.Code != http.StatusOK {
		t.Fatalf("Request 1 failed: %d", rr1.Code)
	}

	// Send Request 2 (identical retry)
	req2 := reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bodyBytes)), userID)
	rr2 := httptest.NewRecorder()
	syncHandler.Sync(rr2, req2)
	if rr2.Code != http.StatusOK {
		t.Fatalf("Request 2 failed: %d", rr2.Code)
	}

	var resp models.SyncResponse
	json.NewDecoder(rr2.Body).Decode(&resp)

	if len(resp.Playlists) != 1 {
		t.Fatalf("Expected 1 playlist, got %d", len(resp.Playlists))
	}
	if len(resp.Playlists[0].Tracks) != 2 {
		t.Fatalf("Expected exactly 2 tracks without duplicates, got %d", len(resp.Playlists[0].Tracks))
	}
	if len(resp.Favorites) != 1 {
		t.Fatalf("Expected exactly 1 favorite, got %d", len(resp.Favorites))
	}
}

func TestSync_MultiUserIsolation(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	syncHandler := handler.NewSyncHandler(pool)
	userA := createTestUser(t, pool, "usera@mixtape.local")
	userB := createTestUser(t, pool, "userb@mixtape.local")

	playlistA := uuid.New().String()
	now := time.Now().UnixMilli()

	// User A creates playlist
	reqA := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{ID: playlistA, Name: "User A Playlist", UpdatedAt: now},
		},
		Favorites: []models.FavoriteSyncPayload{
			{TrackKey: "trackA", Title: "Track A", IsFavorite: true, UpdatedAt: now},
		},
	}
	bytesA, _ := json.Marshal(reqA)
	syncHandler.Sync(httptest.NewRecorder(), reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bytesA)), userA))

	// User B attempts to overwrite User A's playlist
	reqB := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{ID: playlistA, Name: "Hacked by B", UpdatedAt: now + 5000},
		},
	}
	bytesB, _ := json.Marshal(reqB)
	rrB := httptest.NewRecorder()
	syncHandler.Sync(rrB, reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bytesB)), userB))

	// Must be forbidden (403)
	if rrB.Code != http.StatusForbidden {
		t.Fatalf("Expected 403 Forbidden for cross-user update, got %d", rrB.Code)
	}

	// User B syncs empty - should see 0 playlists and 0 favorites
	rrBGet := httptest.NewRecorder()
	syncHandler.GetSyncState(rrBGet, reqWithUser(httptest.NewRequest("GET", "/api/sync", nil), userB))
	var stateB models.SyncResponse
	json.NewDecoder(rrBGet.Body).Decode(&stateB)
	if len(stateB.Playlists) != 0 || len(stateB.Favorites) != 0 {
		t.Fatalf("User B leaked User A data: %d playlists, %d favorites", len(stateB.Playlists), len(stateB.Favorites))
	}
}

func TestSync_ConflictResolution_LastWriteWins(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	syncHandler := handler.NewSyncHandler(pool)
	userID := createTestUser(t, pool, "conflict@mixtape.local")
	playlistID := uuid.New().String()

	tServerNewer := int64(200000)
	tClientOlder := int64(100000)
	tClientNewer := int64(300000)

	// 1. First write at tServerNewer (T=200000)
	req1 := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{ID: playlistID, Name: "Server Newer Title", UpdatedAt: tServerNewer},
		},
	}
	b1, _ := json.Marshal(req1)
	syncHandler.Sync(httptest.NewRecorder(), reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(b1)), userID))

	// 2. Client sends older change at tClientOlder (T=100000)
	reqOlder := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{ID: playlistID, Name: "Stale Offline Change", UpdatedAt: tClientOlder},
		},
	}
	bOlder, _ := json.Marshal(reqOlder)
	rrOlder := httptest.NewRecorder()
	syncHandler.Sync(rrOlder, reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bOlder)), userID))

	var respOlder models.SyncResponse
	json.NewDecoder(rrOlder.Body).Decode(&respOlder)
	if len(respOlder.Playlists) != 1 || respOlder.Playlists[0].Name != "Server Newer Title" {
		t.Fatalf("Stale write erroneously overwrote newer server state: %+v", respOlder.Playlists)
	}

	// 3. Client sends newer change at tClientNewer (T=300000)
	reqNewer := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{ID: playlistID, Name: "Winning Newer Change", UpdatedAt: tClientNewer},
		},
	}
	bNewer, _ := json.Marshal(reqNewer)
	rrNewer := httptest.NewRecorder()
	syncHandler.Sync(rrNewer, reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bNewer)), userID))

	var respNewer models.SyncResponse
	json.NewDecoder(rrNewer.Body).Decode(&respNewer)
	if len(respNewer.Playlists) != 1 || respNewer.Playlists[0].Name != "Winning Newer Change" {
		t.Fatalf("Newer write failed to update server: %+v", respNewer.Playlists)
	}
}

func TestSync_DeletionTombstones(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	syncHandler := handler.NewSyncHandler(pool)
	userID := createTestUser(t, pool, "tombstone@mixtape.local")
	playlistID := uuid.New().String()

	t1 := int64(100000)
	t2Delete := int64(200000)

	// 1. Create playlist
	reqCreate := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{ID: playlistID, Name: "To Delete", UpdatedAt: t1},
		},
		Favorites: []models.FavoriteSyncPayload{
			{TrackKey: "fav_track", Title: "Fav Track", IsFavorite: true, UpdatedAt: t1},
		},
	}
	bCreate, _ := json.Marshal(reqCreate)
	syncHandler.Sync(httptest.NewRecorder(), reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bCreate)), userID))

	// 2. Delete playlist and favorite at t2Delete
	reqDelete := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{ID: playlistID, Name: "To Delete", Deleted: true, UpdatedAt: t2Delete},
		},
		Favorites: []models.FavoriteSyncPayload{
			{TrackKey: "fav_track", Title: "Fav Track", IsFavorite: false, Deleted: true, UpdatedAt: t2Delete},
		},
	}
	bDelete, _ := json.Marshal(reqDelete)
	rrDelete := httptest.NewRecorder()
	syncHandler.Sync(rrDelete, reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bDelete)), userID))

	var respDelete models.SyncResponse
	json.NewDecoder(rrDelete.Body).Decode(&respDelete)

	if len(respDelete.Playlists) != 1 || !respDelete.Playlists[0].Deleted {
		t.Fatalf("Expected playlist to have Deleted=true tombstone")
	}
	if len(respDelete.Favorites) != 1 || !respDelete.Favorites[0].Deleted || respDelete.Favorites[0].IsFavorite {
		t.Fatalf("Expected favorite to have Deleted=true tombstone and IsFavorite=false")
	}

	// 3. Stale offline client tries to recreate with t1 < t2Delete
	reqStale := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{ID: playlistID, Name: "Resurrect Me", Deleted: false, UpdatedAt: t1},
		},
	}
	bStale, _ := json.Marshal(reqStale)
	rrStale := httptest.NewRecorder()
	syncHandler.Sync(rrStale, reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bStale)), userID))

	var respStale models.SyncResponse
	json.NewDecoder(rrStale.Body).Decode(&respStale)
	if !respStale.Playlists[0].Deleted {
		t.Fatalf("Deleted playlist resurrected from stale client write!")
	}
}

func TestSync_ReorderingTracks(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	syncHandler := handler.NewSyncHandler(pool)
	userID := createTestUser(t, pool, "reorder@mixtape.local")
	playlistID := uuid.New().String()

	// Initial order: Track A at pos 0, Track B at pos 1
	req1 := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{
				ID:        playlistID,
				Name:      "Reorder Test",
				UpdatedAt: 1000,
				Tracks: []models.PlaylistTrackSyncPayload{
					{TrackKey: "trackA", Position: 0, Title: "Track A"},
					{TrackKey: "trackB", Position: 1, Title: "Track B"},
				},
			},
		},
	}
	b1, _ := json.Marshal(req1)
	syncHandler.Sync(httptest.NewRecorder(), reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(b1)), userID))

	// Reorder: Track B at pos 0, Track A at pos 1
	req2 := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{
				ID:        playlistID,
				Name:      "Reorder Test",
				UpdatedAt: 2000,
				Tracks: []models.PlaylistTrackSyncPayload{
					{TrackKey: "trackB", Position: 0, Title: "Track B"},
					{TrackKey: "trackA", Position: 1, Title: "Track A"},
				},
			},
		},
	}
	b2, _ := json.Marshal(req2)
	rr2 := httptest.NewRecorder()
	syncHandler.Sync(rr2, reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(b2)), userID))

	var resp models.SyncResponse
	json.NewDecoder(rr2.Body).Decode(&resp)

	tracks := resp.Playlists[0].Tracks
	if len(tracks) != 2 {
		t.Fatalf("Expected 2 tracks, got %d", len(tracks))
	}
	if tracks[0].TrackKey != "trackB" || tracks[0].Position != 0 {
		t.Errorf("Expected trackB at pos 0, got %s at %d", tracks[0].TrackKey, tracks[0].Position)
	}
	if tracks[1].TrackKey != "trackA" || tracks[1].Position != 1 {
		t.Errorf("Expected trackA at pos 1, got %s at %d", tracks[1].TrackKey, tracks[1].Position)
	}
}

func TestSync_ValidationErrors(t *testing.T) {
	pool := setupTestDB(t)
	defer pool.Close()

	syncHandler := handler.NewSyncHandler(pool)
	userID := createTestUser(t, pool, "validation@mixtape.local")

	// Invalid UUID
	reqInvalid := models.SyncRequest{
		Playlists: []models.PlaylistSyncPayload{
			{ID: "not-a-uuid", Name: "Bad ID"},
		},
	}
	bInvalid, _ := json.Marshal(reqInvalid)
	rr := httptest.NewRecorder()
	syncHandler.Sync(rr, reqWithUser(httptest.NewRequest("POST", "/api/sync", bytes.NewReader(bInvalid)), userID))

	if rr.Code != http.StatusBadRequest {
		t.Fatalf("Expected 400 Bad Request for invalid UUID, got %d", rr.Code)
	}
}
