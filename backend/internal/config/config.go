package config

import (
	"os"
	"strconv"
	"time"

	"github.com/joho/godotenv"
)

type Config struct {
	Port                string
	AppEnv              string
	DatabaseURL         string
	DBMaxOpenConns      int
	DBMaxIdleConns      int
	DBConnMaxLifetime   time.Duration
	DBConnMaxIdleTime   time.Duration
	AccessTokenSecret   string
	RefreshTokenSecret  string
	CORSAllowedOrigins  []string
	AllowOpenRegistration bool
}

func Load() *Config {
	// Attempt to load .env file. Ignore error if it doesn't exist (e.g. in production)
	_ = godotenv.Load()

	// Parse basic settings
	port := getEnv("PORT", "8081")
	env := getEnv("APP_ENV", "development")
	dbURL := getEnv("DATABASE_URL", "")

	// Parse pooling settings
	maxOpen, _ := strconv.Atoi(getEnv("DB_MAX_OPEN_CONNECTIONS", "25"))
	maxIdle, _ := strconv.Atoi(getEnv("DB_MAX_IDLE_CONNECTIONS", "25"))
	
	maxLifetimeMinutes, _ := strconv.Atoi(getEnv("DB_CONN_MAX_LIFETIME_MIN", "15"))
	maxIdleTimeMinutes, _ := strconv.Atoi(getEnv("DB_CONN_MAX_IDLE_TIME_MIN", "5"))

	// Secrets
	atSecret := getEnv("ACCESS_TOKEN_SECRET", "super-secret-access-token-key-for-dev")
	rtSecret := getEnv("REFRESH_TOKEN_SECRET", "super-secret-refresh-token-key-for-dev")
    
    // CORS
    corsOrigin := getEnv("CORS_ALLOWED_ORIGINS", "*")
    var origins []string
    if corsOrigin != "" {
        // split by comma in real app, simplistic for now
        origins = []string{corsOrigin}
    }

	// Registration mode: defaults to open registration (true)
	allowOpenRegStr := getEnv("ALLOW_OPEN_REGISTRATION", "true")
	allowOpenReg, err := strconv.ParseBool(allowOpenRegStr)
	if err != nil {
		allowOpenReg = true
	}

	return &Config{
		Port:                  port,
		AppEnv:                env,
		DatabaseURL:           dbURL,
		DBMaxOpenConns:        maxOpen,
		DBMaxIdleConns:        maxIdle,
		DBConnMaxLifetime:     time.Duration(maxLifetimeMinutes) * time.Minute,
		DBConnMaxIdleTime:     time.Duration(maxIdleTimeMinutes) * time.Minute,
		AccessTokenSecret:     atSecret,
		RefreshTokenSecret:    rtSecret,
		CORSAllowedOrigins:    origins,
		AllowOpenRegistration: allowOpenReg,
	}
}

func getEnv(key, fallback string) string {
	if value, exists := os.LookupEnv(key); exists {
		return value
	}
	return fallback
}
