package handler

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/username/private-music-library/backend/internal/middleware"
	"github.com/username/private-music-library/backend/internal/models"
)

type SyncHandler struct {
	db *pgxpool.Pool
}

func NewSyncHandler(db *pgxpool.Pool) *SyncHandler {
	return &SyncHandler{db: db}
}

// GetSyncState returns the synchronized playlists, items, and favorites for the authenticated user.
func (h *SyncHandler) GetSyncState(w http.ResponseWriter, r *http.Request) {
	userID, ok := r.Context().Value(middleware.UserIDKey).(uuid.UUID)
	if !ok {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var since int64
	if sinceStr := r.URL.Query().Get("since"); sinceStr != "" {
		if val, err := strconv.ParseInt(sinceStr, 10, 64); err == nil && val > 0 {
			since = val
		}
	}

	state, err := h.fetchSyncResponse(r.Context(), userID, since)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to fetch sync state")
		return
	}

	respondJSON(w, http.StatusOK, state)
}

// Sync processes client updates for playlists, items, and favorites with Last-Write-Wins and tombstones.
func (h *SyncHandler) Sync(w http.ResponseWriter, r *http.Request) {
	userID, ok := r.Context().Value(middleware.UserIDKey).(uuid.UUID)
	if !ok {
		respondError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	var req models.SyncRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid sync request body")
		return
	}

	if len(req.Playlists) > 1000 || len(req.Favorites) > 5000 {
		respondError(w, http.StatusBadRequest, "sync payload exceeds maximum limit")
		return
	}

	tx, err := h.db.Begin(r.Context())
	if err != nil {
		respondError(w, http.StatusInternalServerError, "internal database error")
		return
	}
	defer tx.Rollback(r.Context())

	// 1. Process playlists
	for _, p := range req.Playlists {
		playlistUUID, err := uuid.Parse(p.ID)
		if err != nil {
			respondError(w, http.StatusBadRequest, "invalid playlist id format")
			return
		}

		name := strings.TrimSpace(p.Name)
		if len(name) > 255 {
			name = name[:255]
		}
		if name == "" && !p.Deleted {
			name = "Untitled Playlist"
		}

		clientUpdatedAt := time.UnixMilli(p.UpdatedAt)
		if p.UpdatedAt <= 0 {
			clientUpdatedAt = time.Now()
		}

		// Check existing playlist
		var ownerID uuid.UUID
		var existingUpdatedAt time.Time
		err = tx.QueryRow(r.Context(),
			"SELECT user_id, updated_at FROM playlists WHERE id = $1",
			playlistUUID,
		).Scan(&ownerID, &existingUpdatedAt)

		if err == nil {
			// Playlist exists - verify ownership
			if ownerID != userID {
				respondError(w, http.StatusForbidden, "forbidden playlist access")
				return
			}

			// Conflict resolution: Last-Write-Wins (millisecond precision)
			if p.UpdatedAt > 0 && clientUpdatedAt.UnixMilli() < existingUpdatedAt.UnixMilli() {
				// Server state is newer; skip updating server record
				continue
			}

			if p.Deleted {
				_, err = tx.Exec(r.Context(),
					"UPDATE playlists SET deleted_at = $1, updated_at = $1 WHERE id = $2 AND user_id = $3",
					clientUpdatedAt, playlistUUID, userID)
				if err != nil {
					respondError(w, http.StatusInternalServerError, "failed to delete playlist")
					return
				}
				_, err = tx.Exec(r.Context(), "DELETE FROM playlist_tracks WHERE playlist_id = $1", playlistUUID)
				if err != nil {
					respondError(w, http.StatusInternalServerError, "failed to clear playlist items")
					return
				}
			} else {
				_, err = tx.Exec(r.Context(),
					"UPDATE playlists SET name = $1, description = $2, updated_at = $3, deleted_at = NULL WHERE id = $4 AND user_id = $5",
					name, p.Description, clientUpdatedAt, playlistUUID, userID)
				if err != nil {
					respondError(w, http.StatusInternalServerError, "failed to update playlist")
					return
				}

				// Replace playlist tracks
				_, err = tx.Exec(r.Context(), "DELETE FROM playlist_tracks WHERE playlist_id = $1", playlistUUID)
				if err != nil {
					respondError(w, http.StatusInternalServerError, "failed to replace playlist tracks")
					return
				}

				for _, pt := range p.Tracks {
					logicalKey := strings.TrimSpace(pt.GetLogicalKey())
					if logicalKey == "" {
						continue
					}
					itemID := uuid.New()
					if pt.ID != nil {
						if parsed, err := uuid.Parse(*pt.ID); err == nil {
							itemID = parsed
						}
					}
					itemUpdatedAt := clientUpdatedAt
					if pt.UpdatedAt > 0 {
						itemUpdatedAt = time.UnixMilli(pt.UpdatedAt)
					}

					_, err = tx.Exec(r.Context(),
						`INSERT INTO playlist_tracks (id, playlist_id, track_id, position, title, artist, album, duration, updated_at)
						 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
						itemID, playlistUUID, logicalKey, pt.Position, pt.Title, pt.Artist, pt.Album, pt.Duration, itemUpdatedAt)
					if err != nil {
						respondError(w, http.StatusInternalServerError, "failed to insert playlist track")
						return
					}
				}
			}
		} else if err == pgx.ErrNoRows {
			// Playlist does not exist - insert new record
			if p.Deleted {
				_, err = tx.Exec(r.Context(),
					`INSERT INTO playlists (id, user_id, name, description, updated_at, deleted_at)
					 VALUES ($1, $2, $3, $4, $5, $5)`,
					playlistUUID, userID, name, p.Description, clientUpdatedAt)
			} else {
				_, err = tx.Exec(r.Context(),
					`INSERT INTO playlists (id, user_id, name, description, updated_at, deleted_at)
					 VALUES ($1, $2, $3, $4, $5, NULL)`,
					playlistUUID, userID, name, p.Description, clientUpdatedAt)
				if err == nil {
					for _, pt := range p.Tracks {
						logicalKey := strings.TrimSpace(pt.GetLogicalKey())
						if logicalKey == "" {
							continue
						}
						itemID := uuid.New()
						if pt.ID != nil {
							if parsed, err := uuid.Parse(*pt.ID); err == nil {
								itemID = parsed
							}
						}
						itemUpdatedAt := clientUpdatedAt
						if pt.UpdatedAt > 0 {
							itemUpdatedAt = time.UnixMilli(pt.UpdatedAt)
						}

						_, err = tx.Exec(r.Context(),
							`INSERT INTO playlist_tracks (id, playlist_id, track_id, position, title, artist, album, duration, updated_at)
							 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
							itemID, playlistUUID, logicalKey, pt.Position, pt.Title, pt.Artist, pt.Album, pt.Duration, itemUpdatedAt)
						if err != nil {
							respondError(w, http.StatusInternalServerError, "failed to insert playlist track")
							return
						}
					}
				}
			}
			if err != nil {
				respondError(w, http.StatusInternalServerError, "failed to create playlist")
				return
			}
		} else {
			respondError(w, http.StatusInternalServerError, "database error querying playlist")
			return
		}
	}

	// 2. Process favorites
	for _, f := range req.Favorites {
		trackKey := strings.TrimSpace(f.TrackKey)
		if trackKey == "" {
			continue
		}
		if len(trackKey) > 255 {
			trackKey = trackKey[:255]
		}

		clientUpdatedAt := time.UnixMilli(f.UpdatedAt)
		if f.UpdatedAt <= 0 {
			clientUpdatedAt = time.Now()
		}

		var existingUpdatedAt time.Time
		err = tx.QueryRow(r.Context(),
			"SELECT updated_at FROM favorites WHERE user_id = $1 AND track_key = $2",
			userID, trackKey,
		).Scan(&existingUpdatedAt)

		if err == nil {
			// Favorite exists - check timestamp conflict
			if f.UpdatedAt > 0 && clientUpdatedAt.UnixMilli() < existingUpdatedAt.UnixMilli() {
				// Server version is newer; skip update
				continue
			}

			if f.Deleted || !f.IsFavorite {
				_, err = tx.Exec(r.Context(),
					`UPDATE favorites SET is_favorite = FALSE, deleted_at = $1, updated_at = $1
					 WHERE user_id = $2 AND track_key = $3`,
					clientUpdatedAt, userID, trackKey)
			} else {
				_, err = tx.Exec(r.Context(),
					`UPDATE favorites SET is_favorite = TRUE, title = $1, artist = $2, album = $3, duration = $4, source = $5, updated_at = $6, deleted_at = NULL
					 WHERE user_id = $7 AND track_key = $8`,
					f.Title, f.Artist, f.Album, f.Duration, f.Source, clientUpdatedAt, userID, trackKey)
			}
		} else if err == pgx.ErrNoRows {
			// Insert new favorite
			favID := uuid.New()
			if f.Deleted || !f.IsFavorite {
				_, err = tx.Exec(r.Context(),
					`INSERT INTO favorites (id, user_id, track_key, title, artist, album, duration, source, is_favorite, updated_at, deleted_at)
					 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, FALSE, $9, $9)`,
					favID, userID, trackKey, f.Title, f.Artist, f.Album, f.Duration, f.Source, clientUpdatedAt)
			} else {
				_, err = tx.Exec(r.Context(),
					`INSERT INTO favorites (id, user_id, track_key, title, artist, album, duration, source, is_favorite, updated_at, deleted_at)
					 VALUES ($1, $2, $3, $4, $5, $6, $7, $8, TRUE, $9, NULL)`,
					favID, userID, trackKey, f.Title, f.Artist, f.Album, f.Duration, f.Source, clientUpdatedAt)
			}
		}

		if err != nil {
			respondError(w, http.StatusInternalServerError, "failed to synchronize favorite")
			return
		}
	}

	if err := tx.Commit(r.Context()); err != nil {
		respondError(w, http.StatusInternalServerError, "failed to commit transaction")
		return
	}

	response, err := h.fetchSyncResponse(r.Context(), userID, req.Since)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to fetch sync response")
		return
	}

	respondJSON(w, http.StatusOK, response)
}

func (h *SyncHandler) fetchSyncResponse(ctx context.Context, userID uuid.UUID, since int64) (*models.SyncResponse, error) {
	var playlists []models.PlaylistSyncPayload

	var playlistRows pgx.Rows
	var err error
	if since > 0 {
		sinceTime := time.UnixMilli(since)
		playlistRows, err = h.db.Query(ctx,
			`SELECT id, name, description, updated_at, deleted_at
			 FROM playlists
			 WHERE user_id = $1 AND (updated_at >= $2 OR deleted_at >= $2)
			 ORDER BY updated_at ASC`,
			userID, sinceTime)
	} else {
		playlistRows, err = h.db.Query(ctx,
			`SELECT id, name, description, updated_at, deleted_at
			 FROM playlists
			 WHERE user_id = $1
			 ORDER BY updated_at ASC`,
			userID)
	}
	if err != nil {
		return nil, err
	}
	defer playlistRows.Close()

	for playlistRows.Next() {
		var (
			pID         uuid.UUID
			name        string
			description *string
			updatedAt   time.Time
			deletedAt   *time.Time
		)
		if err := playlistRows.Scan(&pID, &name, &description, &updatedAt, &deletedAt); err != nil {
			return nil, err
		}

		payload := models.PlaylistSyncPayload{
			ID:          pID.String(),
			Name:        name,
			Description: description,
			Deleted:     deletedAt != nil,
			UpdatedAt:   updatedAt.UnixMilli(),
			Tracks:      make([]models.PlaylistTrackSyncPayload, 0),
		}

		// If playlist is active, load tracks
		if deletedAt == nil {
			trackRows, err := h.db.Query(ctx,
				`SELECT id, track_id, position, title, artist, album, duration, updated_at
				 FROM playlist_tracks
				 WHERE playlist_id = $1 AND deleted_at IS NULL
				 ORDER BY position ASC`,
				pID)
			if err == nil {
				for trackRows.Next() {
					var (
						tID       uuid.UUID
						trackID   string
						position  int
						title     *string
						artist    *string
						album     *string
						duration  *int
						tUpdate   *time.Time
					)
					if err := trackRows.Scan(&tID, &trackID, &position, &title, &artist, &album, &duration, &tUpdate); err == nil {
						idStr := tID.String()
						tName := ""
						if title != nil {
							tName = *title
						}
						var itemUpdated int64
						if tUpdate != nil {
							itemUpdated = tUpdate.UnixMilli()
						}
						payload.Tracks = append(payload.Tracks, models.PlaylistTrackSyncPayload{
							ID:        &idStr,
							TrackKey:  trackID,
							TrackID:   trackID,
							Position:  position,
							Title:     tName,
							Artist:    artist,
							Album:     album,
							Duration:  duration,
							UpdatedAt: itemUpdated,
							Deleted:   false,
						})
					}
				}
				trackRows.Close()
			}
		}

		playlists = append(playlists, payload)
	}

	// Fetch favorites
	var favorites []models.FavoriteSyncPayload
	var favRows pgx.Rows
	if since > 0 {
		sinceTime := time.UnixMilli(since)
		favRows, err = h.db.Query(ctx,
			`SELECT track_key, title, artist, album, duration, source, is_favorite, updated_at, deleted_at
			 FROM favorites
			 WHERE user_id = $1 AND (updated_at >= $2 OR deleted_at >= $2)
			 ORDER BY updated_at ASC`,
			userID, sinceTime)
	} else {
		favRows, err = h.db.Query(ctx,
			`SELECT track_key, title, artist, album, duration, source, is_favorite, updated_at, deleted_at
			 FROM favorites
			 WHERE user_id = $1
			 ORDER BY updated_at ASC`,
			userID)
	}
	if err != nil {
		return nil, err
	}
	defer favRows.Close()

	for favRows.Next() {
		var (
			trackKey   string
			title      string
			artist     *string
			album      *string
			duration   *int
			source     *string
			isFav      bool
			updatedAt  time.Time
			deletedAt  *time.Time
		)
		if err := favRows.Scan(&trackKey, &title, &artist, &album, &duration, &source, &isFav, &updatedAt, &deletedAt); err == nil {
			isDeleted := deletedAt != nil || !isFav
			favorites = append(favorites, models.FavoriteSyncPayload{
				TrackKey:   trackKey,
				Title:      title,
				Artist:     artist,
				Album:      album,
				Duration:   duration,
				Source:     source,
				IsFavorite: isFav && deletedAt == nil,
				UpdatedAt:  updatedAt.UnixMilli(),
				Deleted:    isDeleted,
			})
		}
	}

	if playlists == nil {
		playlists = make([]models.PlaylistSyncPayload, 0)
	}
	if favorites == nil {
		favorites = make([]models.FavoriteSyncPayload, 0)
	}

	return &models.SyncResponse{
		Playlists:  playlists,
		Favorites:  favorites,
		ServerTime: time.Now().UnixMilli(),
	}, nil
}

// Backward-compatible endpoints
func (h *SyncHandler) SyncTracks(w http.ResponseWriter, r *http.Request) {
	userID := r.Context().Value(middleware.UserIDKey).(uuid.UUID)

	var req []models.TrackSyncPayload
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request")
		return
	}

	tx, err := h.db.Begin(r.Context())
	if err != nil {
		respondError(w, http.StatusInternalServerError, "internal error")
		return
	}
	defer tx.Rollback(r.Context())

	for _, t := range req {
		if t.Deleted {
			_, err = tx.Exec(r.Context(), "UPDATE tracks SET deleted_at = CURRENT_TIMESTAMP WHERE user_id = $1 AND track_id = $2", userID, t.TrackID)
		} else {
			query := `INSERT INTO tracks (id, user_id, track_id, title, artist, album, duration, source, source_identifier)
					  VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
					  ON CONFLICT (user_id, track_id) DO UPDATE SET
					  title = EXCLUDED.title, artist = EXCLUDED.artist, album = EXCLUDED.album,
					  duration = EXCLUDED.duration, source = EXCLUDED.source, source_identifier = EXCLUDED.source_identifier,
					  updated_at = CURRENT_TIMESTAMP, deleted_at = NULL`
			_, err = tx.Exec(r.Context(), query, uuid.New(), userID, t.TrackID, t.Title, t.Artist, t.Album, t.Duration, t.Source, t.SourceIdentifier)
		}
		if err != nil {
			respondError(w, http.StatusInternalServerError, "failed to sync tracks")
			return
		}
	}

	tx.Commit(r.Context())

	rows, err := h.db.Query(r.Context(), "SELECT track_id, title, artist, album, duration, source, source_identifier FROM tracks WHERE user_id = $1 AND deleted_at IS NULL", userID)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "failed to fetch tracks")
		return
	}
	defer rows.Close()

	var tracks []models.TrackSyncPayload
	for rows.Next() {
		var p models.TrackSyncPayload
		if err := rows.Scan(&p.TrackID, &p.Title, &p.Artist, &p.Album, &p.Duration, &p.Source, &p.SourceIdentifier); err == nil {
			p.Deleted = false
			tracks = append(tracks, p)
		}
	}

	respondJSON(w, http.StatusOK, tracks)
}

func (h *SyncHandler) SyncPlaylists(w http.ResponseWriter, r *http.Request) {
	// Adapt to call Sync with playlists
	userID := r.Context().Value(middleware.UserIDKey).(uuid.UUID)

	var req []models.PlaylistSyncPayload
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request")
		return
	}

	syncReq := models.SyncRequest{
		Playlists: req,
	}

	bodyBytes, _ := json.Marshal(syncReq)
	r.Body = io.NopCloser(strings.NewReader(string(bodyBytes)))
	// Delegate to Sync
	h.Sync(w, r)
	_ = userID
}
