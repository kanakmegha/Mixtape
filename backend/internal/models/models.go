package models

import (
	"time"
	"github.com/google/uuid"
)

type User struct {
	ID           uuid.UUID `json:"id"`
	Email        string    `json:"email"`
	PasswordHash string    `json:"-"`
	DisplayName  string    `json:"display_name"`
	Status       string    `json:"status"`
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`
	LastLoginAt  *time.Time `json:"last_login_at,omitempty"`
}

type Invitation struct {
	ID            uuid.UUID  `json:"id"`
	TokenHash     string     `json:"-"`
	IntendedEmail *string    `json:"intended_email,omitempty"`
	CreatorID     *uuid.UUID `json:"creator_id,omitempty"`
	UsedAt        *time.Time `json:"used_at,omitempty"`
	RevokedAt     *time.Time `json:"revoked_at,omitempty"`
	ExpiresAt     time.Time  `json:"expires_at"`
	CreatedAt     time.Time  `json:"created_at"`
}

type Track struct {
	ID               uuid.UUID  `json:"id"`
	UserID           uuid.UUID  `json:"user_id"`
	TrackID          string     `json:"track_id"`
	Title            string     `json:"title"`
	Artist           *string    `json:"artist"`
	Album            *string    `json:"album"`
	Duration         *int       `json:"duration"`
	Source           *string    `json:"source"`
	SourceIdentifier *string    `json:"source_identifier"`
	CreatedAt        time.Time  `json:"created_at"`
	UpdatedAt        time.Time  `json:"updated_at"`
	DeletedAt        *time.Time `json:"deleted_at,omitempty"`
}

type Playlist struct {
	ID        uuid.UUID  `json:"id"`
	UserID    uuid.UUID  `json:"user_id"`
	Name      string     `json:"name"`
	CreatedAt time.Time  `json:"created_at"`
	UpdatedAt time.Time  `json:"updated_at"`
	DeletedAt *time.Time `json:"deleted_at,omitempty"`
}

type PlaylistTrack struct {
	ID         uuid.UUID `json:"id"`
	PlaylistID uuid.UUID `json:"playlist_id"`
	TrackID    string    `json:"track_id"`
	Position   int       `json:"position"`
	CreatedAt  time.Time `json:"created_at"`
}
