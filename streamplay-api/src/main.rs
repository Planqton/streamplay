mod auth;
mod config;
mod db;
mod handlers;
mod models;

use axum::{
    response::Html,
    routing::{delete, get, post, put},
    Extension, Router,
};
use sqlx::SqlitePool;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

use auth::JwtSecret;
use config::Config;

#[derive(Clone)]
pub struct AppState {
    pub pool: SqlitePool,
    pub config: Config,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // Initialize tracing
    tracing_subscriber::registry()
        .with(tracing_subscriber::EnvFilter::new(
            std::env::var("RUST_LOG").unwrap_or_else(|_| "info".into()),
        ))
        .with(tracing_subscriber::fmt::layer())
        .init();

    // Load config
    let config = Config::from_env().expect("Failed to load configuration");
    tracing::info!("Configuration loaded");

    // Create database pool
    let pool = db::create_pool(&config.database_url).await?;
    tracing::info!("Database connected");

    let state = AppState {
        pool,
        config: config.clone(),
    };

    // Build router
    let app = Router::new()
        // Frontend
        .route("/", get(|| async { Html(include_str!("index.html")) }))
        // Admin routes
        .route("/api/admin/login", post(handlers::admin::admin_login))
        .route("/api/admin/users", post(handlers::admin::create_user))
        .route("/api/admin/users", get(handlers::admin::list_users))
        .route("/api/admin/users/:id", delete(handlers::admin::delete_user))
        .route("/api/admin/users/:id/data", get(handlers::admin::get_user_json))
        .route("/api/admin/users/:id/data", put(handlers::admin::update_user_json))
        .route("/api/admin/users/:id/stations", put(handlers::admin::update_user_stations))
        // User routes
        .route("/api/user/login", post(handlers::user::user_login))
        .route("/api/user/data", get(handlers::user::get_json))
        .route("/api/user/data", put(handlers::user::update_json))
        // Health check
        .route("/health", get(|| async { "OK" }))
        // State and middleware
        .layer(Extension(JwtSecret(config.jwt_secret.clone())))
        .layer(CorsLayer::new().allow_origin(Any).allow_methods(Any).allow_headers(Any))
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    let addr = format!("0.0.0.0:{}", config.server_port);
    tracing::info!("Server starting on {}", addr);

    let listener = tokio::net::TcpListener::bind(&addr).await?;
    axum::serve(listener, app).await?;

    Ok(())
}
