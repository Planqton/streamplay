# StreamPlay

A modern Android internet radio streaming app with a beautiful, intuitive interface.

## Features

### Core Functionality
- Stream internet radio stations with high-quality audio
- Manage your personal station library with drag & drop reordering
- Discover new stations via integrated radio browser
- View song history with automatic metadata logging

### Player
- Background playback with media notification controls
- Equalizer integration
- Customizable background effects (Fade, Aqua, Radial, Sunset, Forest, Diagonal, Spotlight, Blur)
- Cover animations (Flip, Fade)
- Volume control with mute toggle

### Android Auto
- Full Android Auto support
- Autoplay on connection
- Optional stop on disconnect

### Personalization
- Multi-language support (German, English)
- Screen orientation lock options
- Station shortcuts on home screen
- Import/Export settings and stations (JSON)

### Sync & Cloud
- StreamPlay API sync for cross-device synchronization
- Personal JSON URL sync
- Auto-sync on startup

### Metadata
- Live song information display
- Spotify metadata integration (cover art, album info)
- Manual song logging/bookmarking
- Searchable song history with filters

## Screenshots

*Coming soon*

## Installation

### From Releases
1. Download the latest APK from [Releases](https://github.com/Planqton/streamplay/releases)
2. Enable "Install from unknown sources" in your Android settings
3. Install the APK

### Build from Source
```bash
git clone https://github.com/Planqton/streamplay.git
cd streamplay
./gradlew assembleDebug
```

The APK will be located at `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- Android 7.0 (API 24) or higher
- Internet connection for streaming

## Tech Stack

- **Language:** Kotlin
- **Media:** Media3 ExoPlayer
- **UI:** Material Design 3, ViewPager2, RecyclerView
- **Image Loading:** Glide
- **Networking:** OkHttp
- **Architecture:** Fragments, Services, BroadcastReceivers

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Acknowledgments

- [Radio Browser API](https://www.radio-browser.info/) for station discovery
- [Spotify Web API](https://developer.spotify.com/documentation/web-api/) for metadata enrichment
