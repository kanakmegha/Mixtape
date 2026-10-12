package main

import (
	"context"
	"log"
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"github.com/username/private-music-library/backend/internal/config"
	"github.com/username/private-music-library/backend/internal/database"
	"github.com/username/private-music-library/backend/internal/handler"
	appmw "github.com/username/private-music-library/backend/internal/middleware"
)

func main() {
	cfg := config.Load()

	ctx := context.Background()
	pool, err := database.NewPostgresPool(ctx, cfg)
	if err != nil {
		log.Fatalf("Database connection failed: %v", err)
	}
	defer pool.Close()

	if err := database.RunMigrations(ctx, pool); err != nil {
		log.Fatalf("Migrations failed: %v", err)
	}

	authHandler := handler.NewAuthHandler(pool, cfg)
	adminHandler := handler.NewAdminHandler(pool)
	syncHandler := handler.NewSyncHandler(pool)

	r := chi.NewRouter()

	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)

	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   cfg.CORSAllowedOrigins,
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-CSRF-Token"},
		ExposedHeaders:   []string{"Link"},
		AllowCredentials: true,
		MaxAge:           300,
	}))

	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("OK"))
	})
	
	r.Get("/ready", func(w http.ResponseWriter, r *http.Request) {
		if err := pool.Ping(r.Context()); err != nil {
			http.Error(w, "Service Unavailable", http.StatusServiceUnavailable)
			return
		}
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("READY"))
	})

	r.Route("/api", func(r chi.Router) {
		// Public auth routes
		r.Group(func(r chi.Router) {
			r.Use(appmw.RateLimit)
			r.Post("/auth/register", authHandler.Register)
			r.Post("/auth/login", authHandler.Login)
			r.Post("/auth/refresh", authHandler.Refresh)
		})

		// Protected routes
		r.Group(func(r chi.Router) {
			r.Use(appmw.Auth(cfg))
			
			r.Post("/auth/logout", authHandler.Logout)

			r.Post("/invitations", adminHandler.CreateInvitation)

			r.Get("/sync", syncHandler.GetSyncState)
			r.Post("/sync", syncHandler.Sync)
			r.Post("/sync/tracks", syncHandler.SyncTracks)
			r.Post("/sync/playlists", syncHandler.SyncPlaylists)
		})
	})

	log.Printf("Server starting on port %s in %s mode...\n", cfg.Port, cfg.AppEnv)
	if err := http.ListenAndServe("0.0.0.0:"+cfg.Port, r); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
