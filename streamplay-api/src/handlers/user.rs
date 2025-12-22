use axum::{extract::State, http::StatusCode, Json};

use crate::{
    auth::{create_token, AuthUser, Claims},
    models::{JsonDataResponse, LoginRequest, LoginResponse, UpdateJsonRequest, User},
    AppState,
};

pub async fn user_login(
    State(state): State<AppState>,
    Json(payload): Json<LoginRequest>,
) -> Result<Json<LoginResponse>, (StatusCode, String)> {
    // Find user
    let user: User = sqlx::query_as("SELECT * FROM users WHERE username = ?")
        .bind(&payload.username)
        .fetch_optional(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?
        .ok_or((StatusCode::UNAUTHORIZED, "Invalid credentials".to_string()))?;

    // Verify password
    let valid = bcrypt::verify(&payload.password, &user.password_hash)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    if !valid {
        return Err((StatusCode::UNAUTHORIZED, "Invalid credentials".to_string()));
    }

    let claims = Claims::new_user(user.username.clone(), user.id, 24);
    let token = create_token(&claims, &state.config.jwt_secret)
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    Ok(Json(LoginResponse {
        token,
        username: user.username,
        is_admin: false,
    }))
}

pub async fn get_json(
    auth: AuthUser,
    State(state): State<AppState>,
) -> Result<Json<JsonDataResponse>, (StatusCode, String)> {
    let user_id = auth.0.user_id.ok_or((
        StatusCode::FORBIDDEN,
        "Admin cannot access user data".to_string(),
    ))?;

    let user: User = sqlx::query_as("SELECT * FROM users WHERE id = ?")
        .bind(user_id)
        .fetch_one(&state.pool)
        .await
        .map_err(|e| (StatusCode::INTERNAL_SERVER_ERROR, e.to_string()))?;

    let data: serde_json::Value =
        serde_json::from_str(&user.json_data).unwrap_or(serde_json::json!({}));

    Ok(Json(JsonDataResponse { data }))
}

pub async fn update_json(
    auth: AuthUser,
    State(state): State<AppState>,
    Json(payload): Json<UpdateJsonRequest>,
) -> Result<Json<JsonDataResponse>, (StatusCode, String)> {
    let user_id = auth.0.user_id.ok_or((
        StatusCode::FORBIDDEN,
        "Admin cannot modify user data".to_string(),
    ))?;

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
