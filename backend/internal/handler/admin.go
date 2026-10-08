package handler

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base32"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/username/private-music-library/backend/internal/middleware"
	"github.com/username/private-music-library/backend/internal/models"
)

type AdminHandler struct {
	db *pgxpool.Pool
}

func NewAdminHandler(db *pgxpool.Pool) *AdminHandler {
	return &AdminHandler{db: db}
}

func (h *AdminHandler) CreateInvitation(w http.ResponseWriter, r *http.Request) {
	userID := r.Context().Value(middleware.UserIDKey).(uuid.UUID)

	var req models.CreateInvitationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "invalid request")
		return
	}

	b := make([]byte, 20)
	rand.Read(b)
	tokenStr := base32.StdEncoding.WithPadding(base32.NoPadding).EncodeToString(b)
	
	hash := sha256.Sum256([]byte(tokenStr))
	hashHex := hex.EncodeToString(hash[:])

	id := uuid.New()
	expires := time.Now().Add(time.Duration(req.ExpiresInDays) * 24 * time.Hour)

	_, err := h.db.Exec(r.Context(), "INSERT INTO invitations (id, token_hash, intended_email, creator_id, expires_at) VALUES ($1, $2, $3, $4, $5)", id, hashHex, req.IntendedEmail, userID, expires)
	if err != nil {
		respondError(w, http.StatusInternalServerError, "internal error")
		return
	}

	respondJSON(w, http.StatusCreated, models.InvitationResponse{ID: id.String(), Token: tokenStr})
}
