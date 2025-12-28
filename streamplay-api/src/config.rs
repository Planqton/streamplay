use std::env;

#[derive(Clone)]
pub struct Config {
    pub admin_username: String,
    pub admin_password: String,
    pub jwt_secret: String,
    pub database_url: String,
    pub server_port: u16,
}

impl Config {
    pub fn from_env() -> Result<Self, String> {
        let admin_username = env::var("ADMIN_USERNAME")
            .map_err(|_| "ADMIN_USERNAME must be set")?;

        let admin_password = env::var("ADMIN_PASSWORD")
            .map_err(|_| "ADMIN_PASSWORD must be set")?;

        let jwt_secret = env::var("JWT_SECRET")
            .unwrap_or_else(|_| "default-secret-change-in-production".to_string());

        let database_url = env::var("DATABASE_URL")
            .unwrap_or_else(|_| "sqlite:data.db?mode=rwc".to_string());

        let server_port = env::var("SERVER_PORT")
            .unwrap_or_else(|_| "3000".to_string())
            .parse()
            .map_err(|_| "SERVER_PORT must be a valid port number")?;

        Ok(Self {
            admin_username,
            admin_password,
            jwt_secret,
            database_url,
            server_port,
        })
    }
}
