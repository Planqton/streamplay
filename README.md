# StreamPlay

A feature-rich Android internet radio streaming app with real-time audio visualization, Spotify integration, recording capabilities, and Android Auto support.

![Android](https://img.shields.io/badge/Android-8.0+-green.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)
![Media3](https://img.shields.io/badge/Media3-ExoPlayer-orange.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

---

## Table of Contents
- [Features](#features)
- [Screenshots](#screenshots)
- [Installation](#installation)
- [Configuration](#configuration)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Permissions](#permissions)
- [Contributing](#contributing)
- [License](#license)

---

## Features

### Streaming & Playback
- High-quality audio streaming with Media3 ExoPlayer
- Support for multiple formats: **MP3, OGG, AAC, HLS, M3U, PLS**
- Background playback with persistent media notifications
- Automatic reconnection on network interruptions
- Live metadata extraction from ICY streams (artist & title)
- Resume live stream after pause
- Network type control (WiFi-only, mobile-only, or all)

### Station Management
| Feature | Description |
|---------|-------------|
| **Library** | Add, edit, delete stations with drag & drop reordering |
| **Discovery** | Find new stations via RadioBrowser API |
| **Filters** | Search by country, language, genre, codec |
| **Import/Export** | JSON files or URLs (including GitHub raw) |
| **Shortcuts** | Pin favorite stations to home screen |

### Audio Enhancements

#### Equalizer
5-band equalizer with independent gain control (-15 to +15 dB):
- **Presets:** Flat, Rock, Pop, Jazz, Classical, Bass Boost, Treble Boost, Vocal, Custom
- Real-time adjustment with persistent settings

#### Visualizer
16 stunning real-time FFT visualization styles:

| | | | |
|:---:|:---:|:---:|:---:|
| Bars | Wave | Circle | Line |
| Spectrum | Rings | Blob | Mirror |
| DNA | Fountain | Equalizer | Radar |
| Pulse | Plasma | Orbits | Blur Motion |

- 32-band frequency analysis with smoothing
- Color adaptation from album art
- Peak hold with decay effect

#### Audio Focus
- **Stop:** Pause when other apps need audio
- **Hold:** Continue playing alongside other apps
- **Duck:** Lower volume (configurable 5-50%)

### Visual Customization

#### Background Effects
9 dynamic background styles based on cover art colors:
- Fade, Aqua, Radial, Sunset, Forest, Diagonal, Spotlight, Blur, Visualizer

#### Cover Display
- **Station Mode:** Show station icon
- **Metadata Mode:** Show album artwork from stream
- **Animations:** None, Flip, Fade
- Automatic color palette extraction

### Metadata & History

#### Song Logging
- Automatic song history with timestamps
- Manual bookmarking for favorites
- Searchable history with filters:
  - Time: All, Last hour, Today, Yesterday, Week, Month, Custom range
  - Text: Search by title, artist, station
  - Type: All entries or manual saves only

#### Spotify Integration
Enhance metadata with Spotify API (optional):
- Album artwork in high resolution
- Album name, release date, popularity
- Direct "Open in Spotify" link
- Genre information
- Requires Spotify Developer credentials

#### Lyrics
- Fetch lyrics from LRCLIB API (free, no API key required)
- Synced lyrics (LRC format) and plain text
- Automatic caching (50 items)
- Instrumental track detection

### Recording
- Record streams directly to device storage
- Save location: `Downloads/StreamPlay/`
- Automatic file naming: `StationName_YYYY-MM-DD_HH-mm-ss.ext`
- Format detection from stream
- Recording status indicator
- Works on Android 10+ (MediaStore) and older versions

### Android Auto
- Full Android Auto integration
- Browse and control stations from car display
- Media metadata and artwork display
- **Auto-play:** Start playback when entering car mode (optional)
- **Auto-stop:** Stop playback when exiting car mode (optional)

### Cloud Sync
Synchronize across multiple devices:
- Connect to StreamPlay API (self-hostable)
- Push: Upload stations + settings to server
- Pull: Download profile from server
- Auto-sync on app startup (configurable)
- Secure authentication with username/password

### Additional Features
- **Languages:** German, English (system language detection)
- **Orientation:** Auto, Portrait lock, Landscape lock
- **Updates:** Automatic GitHub release checking
- **Developer Mode:** Crash logs, debug tools (5-second hold on Settings)

---

## Screenshots

*Coming soon*

---

## Installation

### From GitHub Releases
1. Go to [Releases](https://github.com/Planqton/streamplay/releases)
2. Download the latest APK
3. Enable "Install from unknown sources" if prompted
4. Install and enjoy!

### Build from Source
```bash
# Clone the repository
git clone https://github.com/Planqton/streamplay.git
cd streamplay

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

---

## Configuration

### Spotify Integration (Optional)
To enable enhanced metadata:

1. Create account at [developer.spotify.com](https://developer.spotify.com)
2. Create a new application in the dashboard
3. Copy **Client ID** and **Client Secret**
4. In StreamPlay: `Settings → Spotify Meta → Enable → Enter credentials`

### Cloud Sync (Optional)
To sync across devices:

1. Set up StreamPlay API server (or use default endpoint)
2. In StreamPlay: `Settings → StreamPlay API`
3. Enter endpoint, username, password
4. Use **Push** to upload or **Pull** to download

### Network Settings
Control how the app uses your data:

| Setting | Description |
|---------|-------------|
| All Networks | Stream over WiFi and mobile data |
| WiFi Only | Only stream when connected to WiFi |
| Mobile Only | Only stream over mobile data |

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 1.9 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| Media Playback | Media3 ExoPlayer 1.6.1 |
| Networking | OkHttp 4.12.0 |
| Image Loading | Glide 4.16.0 |
| Serialization | Gson |
| UI Framework | Material Design 3, AndroidX |
| Color Extraction | AndroidX Palette |

---

## Architecture

```
app/src/main/java/at/plankt0n/streamplay/
│
├── adapter/              # RecyclerView Adapters
│   ├── StationListAdapter
│   ├── CoverPageAdapter
│   ├── ShortcutAdapter
│   └── ...
│
├── data/                 # Data Classes
│   ├── StationItem
│   ├── MetaLogEntry
│   ├── ShortcutItem
│   └── EqualizerPreset
│
├── helper/               # Utility Classes
│   ├── PreferencesHelper     # SharedPreferences wrapper
│   ├── MediaServiceController # Service communication
│   ├── EqualizerHelper       # Audio equalizer
│   ├── SpotifyMetaReader     # Spotify API client
│   ├── LyricsHelper          # LRCLIB API client
│   ├── StreamRecordHelper    # Recording functionality
│   ├── LiveCoverHelper       # Background effects
│   ├── MetaLogHelper         # Song history
│   ├── IcyStreamReader       # ICY metadata parsing
│   └── ...
│
├── ui/                   # Fragments & UI
│   ├── PlayerFragment        # Main player screen
│   ├── StationsFragment      # Station management
│   ├── DiscoverFragment      # Station discovery
│   ├── MetaLogFragment       # Song history
│   ├── SettingsPageFragment  # Settings
│   ├── EqualizerBottomSheet  # EQ controls
│   ├── LyricsBottomSheet     # Lyrics display
│   └── ...
│
├── view/                 # Custom Views
│   └── VisualizerView        # Audio visualization
│
├── viewmodel/            # ViewModels
│   └── UITrackViewModel      # Track info state
│
├── StreamingService.kt   # MediaLibraryService
├── MainActivity.kt       # Entry point
└── Keys.kt               # Constants
```

---

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Stream audio, fetch metadata |
| `FOREGROUND_SERVICE` | Background playback |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media service |
| `WAKE_LOCK` | Prevent sleep during playback |
| `POST_NOTIFICATIONS` | Playback notifications (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Import stations (Android 9-) |
| `WRITE_EXTERNAL_STORAGE` | Recording (Android 9-) |

---

## Contributing

Contributions are welcome!

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

### Development Setup
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 35

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Acknowledgments

- [RadioBrowser API](https://www.radio-browser.info/) - Station discovery database
- [LRCLIB](https://lrclib.net/) - Free lyrics API
- [Spotify Web API](https://developer.spotify.com/documentation/web-api/) - Metadata enhancement
- [Media3 ExoPlayer](https://github.com/androidx/media) - Media playback engine
- [Glide](https://github.com/bumptech/glide) - Image loading library
- [OkHttp](https://github.com/square/okhttp) - HTTP client

---

## Support

Found a bug or have a feature request? [Open an issue](https://github.com/Planqton/streamplay/issues)

---

<p align="center">
  <b>Made with ❤️ for radio enthusiasts</b>
</p>
