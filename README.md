# StreamPlay

An Android streaming audio application.

## Build Requirements

- Android Studio Giraffe or later
- Android SDK with API 35
- Gradle 8+

## Building

Run `./gradlew assembleDebug` to build the debug APK. The project uses
`spotifyClientId` and `spotifyClientSecret` Gradle properties for optional
Spotify metadata features. Create a `local.properties` file in the project root
and add:

```
spotifyClientId=YOUR_CLIENT_ID
spotifyClientSecret=YOUR_CLIENT_SECRET
```

If these values are omitted, Spotify lookups will be disabled.

## Usage

Install the APK on your device and launch *StreamPlay*. Configure stations in the
app and start streaming your favourite content.
