package models

type RegisterRequest struct {
	Email           string `json:"email"`
	Password        string `json:"password"`
	DisplayName     string `json:"display_name"`
	InvitationToken string `json:"invitation_token"`
}

type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
	Device   string `json:"device,omitempty"`
	Platform string `json:"platform,omitempty"`
}

type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type CreateInvitationRequest struct {
	IntendedEmail *string `json:"intended_email,omitempty"`
	ExpiresInDays int     `json:"expires_in_days"`
}

type InvitationResponse struct {
	ID    string `json:"id"`
	Token string `json:"token"`
}

type TrackSyncPayload struct {
	TrackID          string  `json:"track_id"`
	Title            string  `json:"title"`
	Artist           *string `json:"artist"`
	Album            *string `json:"album"`
	Duration         *int    `json:"duration"`
	Source           *string `json:"source"`
	SourceIdentifier *string `json:"source_identifier"`
	Deleted          bool    `json:"deleted"`
}

type PlaylistTrackSyncPayload struct {
	ID        *string `json:"id,omitempty"`
	TrackKey  string  `json:"track_key"`
	TrackID   string  `json:"track_id,omitempty"` // For backward compatibility
	Position  int     `json:"position"`
	Title     string  `json:"title,omitempty"`
	Artist    *string `json:"artist,omitempty"`
	Album     *string `json:"album,omitempty"`
	Duration  *int    `json:"duration,omitempty"`
	Deleted   bool    `json:"deleted,omitempty"`
	UpdatedAt int64   `json:"updated_at,omitempty"`
}

func (p *PlaylistTrackSyncPayload) GetLogicalKey() string {
	if p.TrackKey != "" {
		return p.TrackKey
	}
	return p.TrackID
}

type PlaylistSyncPayload struct {
	ID          string                     `json:"id"`
	Name        string                     `json:"name"`
	Description *string                    `json:"description,omitempty"`
	Deleted     bool                       `json:"deleted"`
	UpdatedAt   int64                      `json:"updated_at"`
	Tracks      []PlaylistTrackSyncPayload `json:"tracks"`
}

type FavoriteSyncPayload struct {
	TrackKey   string  `json:"track_key"`
	Title      string  `json:"title"`
	Artist     *string `json:"artist,omitempty"`
	Album      *string `json:"album,omitempty"`
	Duration   *int    `json:"duration,omitempty"`
	Source     *string `json:"source,omitempty"`
	IsFavorite bool    `json:"is_favorite"`
	UpdatedAt  int64   `json:"updated_at"`
	Deleted    bool    `json:"deleted,omitempty"`
}

type SyncRequest struct {
	Playlists []PlaylistSyncPayload `json:"playlists"`
	Favorites []FavoriteSyncPayload `json:"favorites"`
	Since     int64                 `json:"since"` // Unix milliseconds timestamp (0 for full snapshot)
}

type SyncResponse struct {
	Playlists  []PlaylistSyncPayload `json:"playlists"`
	Favorites  []FavoriteSyncPayload `json:"favorites"`
	ServerTime int64                 `json:"server_time"` // Unix milliseconds timestamp
}
