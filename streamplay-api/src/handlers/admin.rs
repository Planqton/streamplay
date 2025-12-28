use axum::{extract::State, http::StatusCode, Json};

use crate::{
    auth::{create_token, AdminUser, Claims},
    models::{CreateUserRequest, JsonDataResponse, LoginRequest, LoginResponse, MessageResponse, UpdateJsonRequest, UpdateStationsRequest, User, UserResponse},
    AppState,
};

const STREAMPLAY_TEMPLATE: &str = r#"{
  "settings": {
    "api_sync_enabled": true,
    "audio_focus_mode": "STOP",
    "autoplay_enabled": true,
    "autosync_couchdb_startup": false,
    "autosync_json_startup": false,
    "background_effect": "BLUR",
    "couchdb_endpoint": "",
    "couchdb_password": "",
    "couchdb_show_logs": true,
    "couchdb_username": "",
    "cover_animation_style": "FLIP",
    "cover_mode": "META",
    "minimize_after_autoplay": false,
    "onboarding_done": false,
    "personal_sync_url": "",
    "resume_live_after_pause": true,
    "show_exoplayer_banner": true,
    "spotify_client_id": "",
    "spotify_client_secret": "",
    "update_available": false,
    "use_spotify_meta": true
  },
  "stations": [
    {
      "iconURL": "https://tubestatic.orf.at/mojo/1_3/storyserver//tube/common/images/apple-icons/oe3.png",
      "stationName": "ORF Hitradio Ö3",
      "streamURL": "https://ors-sn03.ors-shoutcast.at/oe3-q1a",
      "uuid": "735bb8f7-0775-4e51-ac2e-c54759f69905"
    },
    {
      "iconURL": "https://i.imgur.com/Z3rkNFc.png",
      "stationName": "Kronehit",
      "streamURL": "http://onair.krone.at/kronehit.mp3",
      "uuid": "6933918c-0949-4fb0-9e9a-ef1a4c679a88"
    },
    {
      "iconURL": "https://tubestatic.orf.at/mojo/1_3/storyserver//tube/fm4/images/touch-icon-iphone-retina.png",
      "stationName": "FM4 | ORF",
      "streamURL": "https://orf-live.ors-shoutcast.at/fm4-q1a",
      "uuid": "736cd54a-c2e4-49c2-b013-39e28ee05623"
    },
    {
      "iconURL": "https://antenneoesterreich.oe24.at/apple-touch-icon.png",
      "stationName": "Antenne Österreich",
      "streamURL": "https://frontend.streamonkey.net/antoesterreich-live",
      "uuid": "42eb23b8-83ac-4a56-accb-1f59a399c649"
    }
  ]
}"#;

pub async fn admin_login(
    State(state): State<AppState>,
    Json(payload): Json<LoginRequest>,
) -> Result<Json<LoginResponse>, (StatusCode, String)> {
    // Check admin credentials from env
    if payload.username != state.config.admin_username
        || payload.password != state.config.admin_password
    {
        return Err((
            StatusCode::UNAUTHORIZED,
            "Invalid admin credentials".to_string(),
        ));
    }

    let claims = Claims::new_admin(payload.username.clone(), 24);
    let token = create_token(&claims, &state.config.jwt_secret)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(LoginResponse {
        token,
        username: payload.username,
        is_admin: true,
    }))
}

pub async fn create_user(
    _admin: AdminUser,
    State(state): State<AppState>,
    Json(payload): Json<CreateUserRequest>,
) -> Result<Json<UserResponse>, (StatusCode, String)> {
    // Hash password
    let password_hash = bcrypt::hash(&payload.password, bcrypt::DEFAULT_COST)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    // Insert user with Streamplay template
    let result = sqlx::query(
        "INSERT INTO users (username, password_hash, json_data) VALUES (?, ?, ?)",
    )
    .bind(&payload.username)
    .bind(&password_hash)
    .bind(STREAMPLAY_TEMPLATE)
    .execute(&state.pool)
    .await
    .map_err(|e| {
        if e.to_string().contains("UNIQUE constraint failed") {
            (StatusCode::CONFLICT, "Username already exists".to_string())
        } else {
            (StatusCode::INTERNAL_SERVER_ERROR, e.to_string())
        }
    })?;

    let user_id = result.last_insert_rowid();

    // Fetch created user
    let user: User = sqlx::query_as("SELECT * FROM users WHERE id = ?")
        .bind(user_id)
        .fetch_one(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(user.into()))
}

pub async fn list_users(
    _admin: AdminUser,
    State(state): State<AppState>,
) -> Result<Json<Vec<UserResponse>>, (StatusCode, String)> {
    let users: Vec<User> = sqlx::query_as("SELECT * FROM users ORDER BY created_at DESC")
        .fetch_all(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(users.into_iter().map(|u| u.into()).collect()))
}

pub async fn delete_user(
    _admin: AdminUser,
    State(state): State<AppState>,
    axum::extract::Path(user_id): axum::extract::Path<i64>,
) -> Result<Json<MessageResponse>, (StatusCode, String)> {
    let result = sqlx::query("DELETE FROM users WHERE id = ?")
        .bind(user_id)
        .execute(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    if result.rows_affected() == 0 {
        return Err((StatusCode::NOT_FOUND, "User not found".to_string()));
    }

    Ok(Json(MessageResponse {
        message: "User deleted successfully".to_string(),
    }))
}

pub async fn get_user_json(
    _admin: AdminUser,
    State(state): State<AppState>,
    axum::extract::Path(user_id): axum::extract::Path<i64>,
) -> Result<Json<JsonDataResponse>, (StatusCode, String)> {
    let user: User = sqlx::query_as("SELECT * FROM users WHERE id = ?")
        .bind(user_id)
        .fetch_optional(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?
        .ok_or((StatusCode::NOT_FOUND, "User not found".to_string()))?;

    let data: serde_json::Value =
        serde_json::from_str(&user.json_data).unwrap_or(serde_json::json!({}));

    Ok(Json(JsonDataResponse { data }))
}

pub async fn update_user_json(
    _admin: AdminUser,
    State(state): State<AppState>,
    axum::extract::Path(user_id): axum::extract::Path<i64>,
    Json(payload): Json<UpdateJsonRequest>,
) -> Result<Json<JsonDataResponse>, (StatusCode, String)> {
    // Check if user exists
    let _user: User = sqlx::query_as("SELECT * FROM users WHERE id = ?")
        .bind(user_id)
        .fetch_optional(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?
        .ok_or((StatusCode::NOT_FOUND, "User not found".to_string()))?;

    let json_string = serde_json::to_string(&payload.data)
        .map_err(|e| (StatusCode::BAD_REQUEST, e.to_string()))?;

    sqlx::query("UPDATE users SET json_data = ? WHERE id = ?")
        .bind(&json_string)
        .bind(user_id)
        .execute(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(JsonDataResponse { data: payload.data }))
}

pub async fn update_user_stations(
    _admin: AdminUser,
    State(state): State<AppState>,
    axum::extract::Path(user_id): axum::extract::Path<i64>,
    Json(payload): Json<UpdateStationsRequest>,
) -> Result<Json<JsonDataResponse>, (StatusCode, String)> {
    // Get current user data
    let user: User = sqlx::query_as("SELECT * FROM users WHERE id = ?")
        .bind(user_id)
        .fetch_optional(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?
        .ok_or((StatusCode::NOT_FOUND, "User not found".to_string()))?;

    // Parse existing JSON data
    let mut data: serde_json::Value =
        serde_json::from_str(&user.json_data).unwrap_or(serde_json::json!({}));

    // Only update stations, keep settings intact
    data["stations"] = serde_json::json!(payload.stations);

    let json_string = serde_json::to_string(&data)
        .map_err(|e| (StatusCode::BAD_REQUEST, e.to_string()))?;

    sqlx::query("UPDATE users SET json_data = ? WHERE id = ?")
        .bind(&json_string)
        .bind(user_id)
        .execute(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(JsonDataResponse { data }))
}
