# Streamplay API

Eine RESTful API für die Streamplay-App, geschrieben in Rust mit Axum.

## Features

- **Admin-Verwaltung**: User erstellen, auflisten, löschen
- **User-Authentifizierung**: JWT-basierte Anmeldung
- **Daten-Sync**: JSON-Daten pro User speichern und abrufen
- **SQLite-Datenbank**: Leichtgewichtige, eingebettete Datenbank
- **Docker-Support**: Multi-Arch Images (ARM64/AMD64)

## API-Endpunkte

| Methode | Endpunkt | Beschreibung |
|---------|----------|--------------|
| POST | `/api/admin/login` | Admin-Login |
| POST | `/api/admin/users` | User erstellen |
| GET | `/api/admin/users` | Alle User auflisten |
| DELETE | `/api/admin/users/:id` | User löschen |
| GET | `/api/admin/users/:id/data` | User-Daten abrufen |
| PUT | `/api/admin/users/:id/data` | User-Daten aktualisieren |
| POST | `/api/user/login` | User-Login |
| GET | `/api/user/data` | Eigene Daten abrufen |
| PUT | `/api/user/data` | Eigene Daten aktualisieren |
| GET | `/health` | Health-Check |

## Installation

### Voraussetzungen

- Rust 1.70+ (für lokale Entwicklung)
- Docker & Docker Compose (für Container-Deployment)

### Lokale Entwicklung

1. **Repository klonen**
   ```bash
   git clone https://github.com/your-username/streamplay-api.git
   cd streamplay-api
   ```

2. **Umgebungsvariablen konfigurieren**
   ```bash
   cp .env.example .env
   # .env bearbeiten und sichere Werte setzen
   ```

3. **Starten**
   ```bash
   cargo run
   ```

### Mit Docker

1. **Mit Docker Compose starten**
   ```bash
   docker-compose up -d
   ```

2. **Oder manuell bauen**
   ```bash
   docker build -t streamplay-api .
   docker run -p 3000:3000 \
     -e ADMIN_USERNAME=admin123 \
     -e ADMIN_PASSWORD=password123 \
     -e JWT_SECRET=secret123 \
     -v $(pwd)/data:/app/data \
     streamplay-api
   ```

## Konfiguration

Umgebungsvariablen in `.env`:

| Variable | Beschreibung | Standard |
|----------|--------------|----------|
| `ADMIN_USERNAME` | Admin-Benutzername | (erforderlich) |
| `ADMIN_PASSWORD` | Admin-Passwort | (erforderlich) |
| `JWT_SECRET` | Secret für JWT-Tokens | (erforderlich) |
| `SERVER_PORT` | Server-Port | `3000` |
| `DATABASE_URL` | SQLite-Datenbankpfad | `sqlite:data.db?mode=rwc` |
| `RUST_LOG` | Log-Level | `info` |

## Testen

### Test-Skript ausführen

```bash
# API im Docker starten
./test.sh

# Oder manuell testen
./upload.sh
```

### Manuell testen

1. **Admin-Login**
   ```bash
   curl -X POST http://localhost:3000/api/admin/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin123","password":"password123"}'
   ```

2. **User erstellen** (mit Admin-Token)
   ```bash
   curl -X POST http://localhost:3000/api/admin/users \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <ADMIN_TOKEN>" \
     -d '{"username":"testuser","password":"testpass"}'
   ```

3. **User-Login**
   ```bash
   curl -X POST http://localhost:3000/api/user/login \
     -H "Content-Type: application/json" \
     -d '{"username":"testuser","password":"testpass"}'
   ```

4. **Daten speichern** (mit User-Token)
   ```bash
   curl -X PUT http://localhost:3000/api/user/data \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <USER_TOKEN>" \
     -d '{"data":{"settings":{},"stations":[]}}'
   ```

## Build

### Lokal bauen

```bash
cargo build --release
```

### Docker-Image bauen

```bash
# Für ARM64 (z.B. Raspberry Pi, Apple Silicon)
./build.sh

# Multi-Arch Build
./buildx.sh
```

## Deployment

### Mit Portainer

Die Datei `portainer-stack.yml` kann direkt in Portainer als Stack importiert werden.

### Produktions-Hinweise

- Sichere Passwörter verwenden
- JWT_SECRET sollte ein langer, zufälliger String sein
- HTTPS mit Reverse-Proxy (nginx, Traefik) einrichten
- Datenbank-Volume regelmäßig sichern

## Projektstruktur

```
streamplay-api/
├── src/
│   ├── main.rs        # Einstiegspunkt, Router-Setup
│   ├── config.rs      # Konfiguration laden
│   ├── db.rs          # Datenbankverbindung
│   ├── auth.rs        # JWT-Authentifizierung
│   ├── models.rs      # Datenmodelle
│   └── handlers/      # API-Handler
│       ├── mod.rs
│       ├── admin.rs   # Admin-Endpunkte
│       └── user.rs    # User-Endpunkte
├── data/              # SQLite-Datenbank
├── Cargo.toml         # Rust-Dependencies
├── Dockerfile         # Multi-Stage Build
├── docker-compose.yml # Lokale Entwicklung
└── .env.example       # Beispiel-Konfiguration
```

## Lizenz

MIT
