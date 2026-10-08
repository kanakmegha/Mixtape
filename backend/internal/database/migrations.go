package database

import (
	"context"
	"fmt"
	"github.com/jackc/pgx/v5/pgxpool"
	"log"
)

var migrations = []string{
	`CREATE TABLE IF NOT EXISTS schema_migrations (
		version INT PRIMARY KEY
	);`,
	`CREATE TABLE IF NOT EXISTS users (
		id UUID PRIMARY KEY,
		email VARCHAR(255) UNIQUE NOT NULL,
		password_hash VARCHAR(255) NOT NULL,
		display_name VARCHAR(255) NOT NULL,
		status VARCHAR(50) NOT NULL DEFAULT 'active',
		created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
		updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
		last_login_at TIMESTAMP WITH TIME ZONE
	);
	CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);`,
	`CREATE TABLE IF NOT EXISTS invitations (
		id UUID PRIMARY KEY,
		token_hash VARCHAR(255) NOT NULL,
		intended_email VARCHAR(255),
		creator_id UUID REFERENCES users(id) ON DELETE SET NULL,
		used_at TIMESTAMP WITH TIME ZONE,
		revoked_at TIMESTAMP WITH TIME ZONE,
		expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
		created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
	);
	CREATE INDEX IF NOT EXISTS idx_invitations_token ON invitations(token_hash);`,
	`CREATE TABLE IF NOT EXISTS sessions (
		id UUID PRIMARY KEY,
		user_id UUID REFERENCES users(id) ON DELETE CASCADE,
		refresh_token_hash VARCHAR(255) NOT NULL,
		device_name VARCHAR(255),
		platform VARCHAR(255),
		created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
		last_seen_at TIMESTAMP WITH TIME ZONE,
		revoked_at TIMESTAMP WITH TIME ZONE
	);
	CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id);
	CREATE INDEX IF NOT EXISTS idx_sessions_token ON sessions(refresh_token_hash);`,
	`CREATE TABLE IF NOT EXISTS playlists (
		id UUID PRIMARY KEY,
		user_id UUID REFERENCES users(id) ON DELETE CASCADE,
		name VARCHAR(255) NOT NULL,
		created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
		updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
		deleted_at TIMESTAMP WITH TIME ZONE
	);
	CREATE INDEX IF NOT EXISTS idx_playlists_user_id ON playlists(user_id);`,
	`CREATE TABLE IF NOT EXISTS tracks (
		id UUID PRIMARY KEY,
		user_id UUID REFERENCES users(id) ON DELETE CASCADE,
		track_id VARCHAR(255) NOT NULL,
		title VARCHAR(255) NOT NULL,
		artist VARCHAR(255),
		album VARCHAR(255),
		duration INT,
		source VARCHAR(255),
		source_identifier TEXT,
		created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
		updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
		deleted_at TIMESTAMP WITH TIME ZONE,
		UNIQUE (user_id, track_id)
	);
	CREATE INDEX IF NOT EXISTS idx_tracks_user_id ON tracks(user_id);`,
	`CREATE TABLE IF NOT EXISTS playlist_tracks (
		id UUID PRIMARY KEY,
		playlist_id UUID REFERENCES playlists(id) ON DELETE CASCADE,
		track_id VARCHAR(255) NOT NULL,
		position INT NOT NULL,
		created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
	);
	CREATE INDEX IF NOT EXISTS idx_playlist_tracks_playlist ON playlist_tracks(playlist_id);`,
	`ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(50) NOT NULL DEFAULT 'user';
	ALTER TABLE invitations ADD COLUMN IF NOT EXISTS used_by UUID REFERENCES users(id) ON DELETE SET NULL;
	CREATE UNIQUE INDEX IF NOT EXISTS idx_users_single_admin ON users (role) WHERE role = 'admin';`,
}

func RunMigrations(ctx context.Context, pool *pgxpool.Pool) error {
	log.Println("Running database migrations...")
	
	// Create migrations table if not exists
	_, err := pool.Exec(ctx, migrations[0])
	if err != nil {
		return fmt.Errorf("failed to initialize migrations table: %w", err)
	}

	var currentVersion int
	err = pool.QueryRow(ctx, "SELECT COALESCE(MAX(version), 0) FROM schema_migrations").Scan(&currentVersion)
	if err != nil {
		return fmt.Errorf("failed to get current migration version: %w", err)
	}

	for i := currentVersion + 1; i < len(migrations); i++ {
		log.Printf("Applying migration version %d\n", i)
		tx, err := pool.Begin(ctx)
		if err != nil {
			return fmt.Errorf("failed to begin transaction for migration %d: %w", i, err)
		}

		if _, err := tx.Exec(ctx, migrations[i]); err != nil {
			tx.Rollback(ctx)
			return fmt.Errorf("failed to apply migration %d: %w", i, err)
		}

		if _, err := tx.Exec(ctx, "INSERT INTO schema_migrations (version) VALUES ($1)", i); err != nil {
			tx.Rollback(ctx)
			return fmt.Errorf("failed to record migration %d: %w", i, err)
		}

		if err := tx.Commit(ctx); err != nil {
			return fmt.Errorf("failed to commit migration %d: %w", i, err)
		}
	}

	log.Println("Database migrations up to date.")
	return nil
}
