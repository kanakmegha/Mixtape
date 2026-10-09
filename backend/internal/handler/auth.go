package handler

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/username/private-music-library/backend/internal/config"
	"github.com/username/private-music-library/backend/internal/models"
	"github.com/username/private-music-library/backend/internal/utils"
	"golang.org/x/crypto/bcrypt"
)

type AuthHandler struct {
	db  *pgxpool.Pool
	cfg *config.Config
}

func NewAuthHandler(db *pgxpool.Pool, cfg *config.Config) *AuthHandler {
	return &AuthHandler{db: db, cfg: cfg}
}

func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	var req models.RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request")
		return
	}

	if req.Email == "" || req.Password == "" || req.DisplayName == "" {
		respondError(w, http.StatusBadRequest, "email, password, and display_name are required")
		return
	}
	if len(req.Password) < 6 {
		respondError(w, http.StatusBadRequest, "password must be at least 6 characters")
		return
	}

	// Determine if this is the first user
	var adminExists bool
	err := h.db.QueryRow(r.Context(), "SELECT EXISTS(SELECT 1 FROM users WHERE role = 'admin')").Scan(&adminExists)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "internal error")
		return
	}

	role := "user"
	var invID *uuid.UUID

	if !adminExists && req.InvitationToken == "" {
		// Attempt to create first admin. 
		// If concurrent request creates an admin first, the unique index idx_users_single_admin will reject this insert.
		role = "admin"
	} else if req.InvitationToken == "" {
		if !h.cfg.AllowOpenRegistration {
			respondError(w, http.StatusForbidden, "invitation required")
			return
		}
	} else {
		// 1. Verify invitation
		var parsedInvID uuid.UUID
		var expiresAt time.Time
		var usedAt *time.Time
		
		hash := sha256.Sum256([]byte(req.InvitationToken))
		hashHex := hex.EncodeToString(hash[:])

		err := h.db.QueryRow(r.Context(), "SELECT id, expires_at, used_at FROM invitations WHERE token_hash = $1", hashHex).Scan(&parsedInvID, &expiresAt, &usedAt)
		if err != nil {
			respondError(w, http.StatusForbidden, "invalid invitation")
			return
		}
		if usedAt != nil {
			respondError(w, http.StatusForbidden, "invitation already used")
			return
		}
		if time.Now().After(expiresAt) {
			respondError(w, http.StatusForbidden, "invitation expired")
			return
		}
		invID = &parsedInvID
	}

	// 2. Hash password
	pwdHash, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "internal error")
		return
	}

	// 3. Create user and mark invitation used in transaction
	tx, err := h.db.Begin(r.Context())
	if err != nil {
		respondError(w, http.StatusInternalServerError, "internal error")
		return
	}
	defer tx.Rollback(r.Context())

	userID := uuid.New()
	_, err = tx.Exec(r.Context(), "INSERT INTO users (id, email, password_hash, display_name, role) VALUES ($1, $2, $3, $4, $5)", userID, req.Email, string(pwdHash), req.DisplayName, role)
	if err != nil {
		// If it's a unique constraint violation for single_admin, we could parse it, but for simplicity we return a generic conflict or generic error
		// In Postgres, error code 23505 is unique violation.
		respondError(w, http.StatusConflict, "email already registered or admin already exists")
		return
	}

	if invID != nil {
		_, err = tx.Exec(r.Context(), "UPDATE invitations SET used_at = CURRENT_TIMESTAMP, used_by = $2 WHERE id = $1", *invID, userID)
		if err != nil {
			respondError(w, http.StatusInternalServerError, "internal error")
			return
		}
	}

	tx.Commit(r.Context())
	respondJSON(w, http.StatusCreated, map[string]string{"message": "registered successfully", "role": role})
}

func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req models.LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request")
		return
	}

	var userID uuid.UUID
	var hash string
	err := h.db.QueryRow(r.Context(), "SELECT id, password_hash FROM users WHERE email = $1", req.Email).Scan(&userID, &hash)
	if err != nil {
		respondError(w, http.StatusUnauthorized, "invalid credentials")
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(req.Password)); err != nil {
		respondError(w, http.StatusUnauthorized, "invalid credentials")
		return
	}

	// Update last login
	h.db.Exec(r.Context(), "UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = $1", userID)

	sessionID := uuid.New()
	at, _ := utils.GenerateAccessToken(userID, h.cfg.AccessTokenSecret, 15*time.Minute)
	rt, _ := utils.GenerateRefreshToken(userID, sessionID, h.cfg.RefreshTokenSecret, 7*24*time.Hour)

	// Hash refresh token for DB
	rtHash, _ := bcrypt.GenerateFromPassword([]byte(rt), bcrypt.DefaultCost)
	_, err = h.db.Exec(r.Context(), "INSERT INTO sessions (id, user_id, refresh_token_hash, device_name, platform) VALUES ($1, $2, $3, $4, $5)", sessionID, userID, string(rtHash), req.Device, req.Platform)

	respondJSON(w, http.StatusOK, models.TokenResponse{AccessToken: at, RefreshToken: rt})
}

func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req models.RefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request")
		return
	}

	claims, err := utils.VerifyToken(req.RefreshToken, h.cfg.RefreshTokenSecret)
	if err != nil {
		respondError(w, http.StatusUnauthorized, "invalid refresh token")
		return
	}

	var rtHash string
	var revokedAt *time.Time
	err = h.db.QueryRow(r.Context(), "SELECT refresh_token_hash, revoked_at FROM sessions WHERE id = $1", claims.SessionID).Scan(&rtHash, &revokedAt)
	if err != nil || revokedAt != nil {
		respondError(w, http.StatusUnauthorized, "session revoked or invalid")
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(rtHash), []byte(req.RefreshToken)); err != nil {
		respondError(w, http.StatusUnauthorized, "invalid token")
		return
	}

	h.db.Exec(r.Context(), "UPDATE sessions SET last_seen_at = CURRENT_TIMESTAMP WHERE id = $1", claims.SessionID)

	at, _ := utils.GenerateAccessToken(claims.UserID, h.cfg.AccessTokenSecret, 15*time.Minute)
	respondJSON(w, http.StatusOK, models.TokenResponse{AccessToken: at, RefreshToken: req.RefreshToken}) // Keep same RT for simplicity, or rotate. Rotating requires updating DB.
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
    var req models.RefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err == nil && req.RefreshToken != "" {
        claims, err := utils.VerifyToken(req.RefreshToken, h.cfg.RefreshTokenSecret)
        if err == nil {
            h.db.Exec(r.Context(), "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = $1", claims.SessionID)
        }
    }
	respondJSON(w, http.StatusOK, map[string]string{"message": "logged out"})
}
