use axum::{
    async_trait,
    extract::FromRequestParts,
    http::{request::Parts, StatusCode},
    RequestPartsExt,
};
use axum_extra::{
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Claims {
    pub sub: String,        // username
    pub user_id: Option<i64>, // None for admin
    pub is_admin: bool,
    pub exp: usize,
}

impl Claims {
    pub fn new_admin(username: String, exp_hours: i64) -> Self {
        let exp = chrono::Utc::now()
            .checked_add_signed(chrono::Duration::hours(exp_hours))
            .expect("valid timestamp")
            .timestamp() as usize;

        Self {
            sub: username,
            user_id: None,
            is_admin: true,
            exp,
        }
    }

    pub fn new_user(username: String, user_id: i64, exp_hours: i64) -> Self {
        let exp = chrono::Utc::now()
            .checked_add_signed(chrono::Duration::hours(exp_hours))
            .expect("valid timestamp")
            .timestamp() as usize;

        Self {
            sub: username,
            user_id: Some(user_id),
            is_admin: false,
            exp,
        }
    }
}

pub fn create_token(claims: &Claims, secret: &str) -> Result<String, jsonwebtoken::errors::Error> {
    encode(
        &Header::default(),
        claims,
        &EncodingKey::from_secret(secret.as_bytes()),
    )
}

pub fn verify_token(token: &str, secret: &str) -> Result<Claims, jsonwebtoken::errors::Error> {
    let token_data = decode::<Claims>(
        token,
        &DecodingKey::from_secret(secret.as_bytes()),
        &Validation::default(),
    )?;
    Ok(token_data.claims)
}

// Extractor for authenticated users
pub struct AuthUser(pub Claims);

#[async_trait]
impl<S> FromRequestParts<S> for AuthUser
where
    S: Send + Sync,
{
    type Rejection = (StatusCode, &'static str);

    async fn from_request_parts(parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        // Get JWT secret from extensions
        let jwt_secret = parts
            .extensions
            .get::<JwtSecret>()
            .ok_or((StatusCode::INTERNAL_SERVER_ERROR, "JWT secret not configured"))?
            .0
            .clone();

        // Extract Authorization header
        let TypedHeader(Authorization(bearer)) = parts
            .extract::<TypedHeader<Authorization<Bearer>>>()
            .await
            .map_err(|_| (StatusCode::UNAUTHORIZED, "Missing authorization header"))?;

        // Verify token
        let claims = verify_token(bearer.token(), &jwt_secret)
            .map_err(|_| (StatusCode::UNAUTHORIZED, "Invalid token"))?;

        Ok(AuthUser(claims))
    }
}

// Extractor for admin only
pub struct AdminUser(pub Claims);

#[async_trait]
impl<S> FromRequestParts<S> for AdminUser
where
    S: Send + Sync,
{
    type Rejection = (StatusCode, &'static str);

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        let AuthUser(claims) = AuthUser::from_request_parts(parts, state).await?;

        if !claims.is_admin {
            return Err((StatusCode::FORBIDDEN, "Admin access required"));
        }

        Ok(AdminUser(claims))
    }
}

#[derive(Clone)]
pub struct JwtSecret(pub String);
