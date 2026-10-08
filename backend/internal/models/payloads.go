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

type PlaylistSyncPayload struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Deleted bool   `json:"deleted"`
	Tracks  []PlaylistTrackSyncPayload `json:"tracks"`
}

type PlaylistTrackSyncPayload struct {
	TrackID  string `json:"track_id"`
	Position int    `json:"position"`
}
