package at.plankt0n.streamplay

import java.util.Date

object Keys {
    const val PREFS_NAME = "stream_prefs"
    const val KEY_STATIONS = "stations"
    const val KEY_STATION_LISTS = "station_lists"  // Multi-Listen-Struktur
    const val KEY_SELECTED_LIST = "selected_list"  // Aktuell ausgewählte Liste
    const val DEFAULT_LIST_NAME = "default"        // Standard-Listenname
    const val PREF_LAST_PLAYED_STREAM_INDEX = "last_played_stream_index" //Letzte Gespielte Index
    const val PREF_UPDATE_AVAILABLE = "update_available"
    const val ACTION_SHOW_COUNTDOWN = "at.plankt0n.streamplay.SHOW_COUNTDOWN"
    const val ACTION_HIDE_COUNTDOWN = "at.plankt0n.streamplay.HIDE_COUNTDOWN"
    const val ACTION_REFRESH_METADATA = "at.plankt0n.streamplay.ACTION_REFRESH_METADATA"
    const val EXTRA_COUNTDOWN_DURATION = "countdown_duration"
    const val ACTION_PLAY_STATION = "at.plankt0n.streamplay.PLAY_STATION"
    const val EXTRA_STATION_UUID = "extra_station_uuid"
    const val EXTRA_STATION_NAME = "extra_station_name"
    const val EXTRA_STATION_STREAM_URL = "extra_station_stream_url"
    const val EXTRA_STATION_ICON_URL = "extra_station_icon_url"
    const val UPDATE_FORCE_TAP_COUNT = 5

    const val PREF_AUDIO_FOCUS_MODE = "audio_focus_mode"
    const val PREF_COVER_ANIMATION_STYLE = "cover_animation_style"
    const val PREF_SCREEN_ORIENTATION = "screen_orientation"
    const val PREF_APP_LANGUAGE = "app_language"
    const val PREF_RESUME_LIVE_AFTER_PAUSE = "resume_live_after_pause"
    const val PREF_NETWORK_TYPE = "network_type"
    const val PREF_ONBOARDING_DONE = "onboarding_done"

    // UI Settings
    const val PREF_SHOW_STATION_IN_MEDIAINFO = "show_station_in_mediainfo"
    const val PREF_VISUALIZER_STYLE = "visualizer_style"
    const val PREF_SHOW_ROTATE_LOCK = "show_rotate_lock"

    // Spotify Unavailable Overlay Settings
    const val PREF_OVERLAY_ENABLED = "overlay_enabled"
    const val PREF_OVERLAY_POSITION = "overlay_position"
    const val PREF_OVERLAY_OPACITY = "overlay_opacity"

    // Audio Settings
    const val PREF_AUDIO_FOCUS_HOLD = "audio_focus_hold"  // Boolean, default false
    const val PREF_DUCK_VOLUME = "duck_volume"  // Int 5-50, default 20 (percent)

    // Equalizer Settings
    const val PREF_EQ_ENABLED = "eq_enabled"
    const val PREF_EQ_PRESET = "eq_preset"
    const val PREF_EQ_BAND_PREFIX = "eq_band_"  // eq_band_0, eq_band_1, etc.
    const val ACTION_EQUALIZER_SETTINGS_UPDATED = "at.plankt0n.streamplay.ACTION_EQUALIZER_SETTINGS_UPDATED"

    // Android Auto
    const val PREF_AUTO_AUTOPLAY = "auto_autoplay_enabled"
    const val PREF_AUTO_STOP_ON_EXIT = "auto_stop_on_exit"
    const val ACTION_AUTO_PLAY = "at.plankt0n.streamplay.ACTION_AUTO_PLAY"
    const val ACTION_AUTO_STOP = "at.plankt0n.streamplay.ACTION_AUTO_STOP"
    const val ACTION_NOTIFY_STATIONS_CHANGED = "at.plankt0n.streamplay.ACTION_NOTIFY_STATIONS_CHANGED"

    // Dev Items for Android Auto
    const val KEY_DEV_FOR_YOU_ITEMS = "dev_for_you_items"
    const val KEY_DEV_WHAT_TO_LISTEN_ITEMS = "dev_what_to_listen_items"

    // StreamPlay API Settings
    const val PREF_API_ENDPOINT = "api_endpoint"
    const val DEFAULT_API_ENDPOINT = "https://streamplayapi.printspace.at"
    const val PREF_API_USERNAME = "api_username"
    const val PREF_API_PASSWORD = "api_password"
    const val PREF_API_TOKEN = "api_token"
    const val PREF_API_SYNC_ENABLED = "api_sync_enabled"
    const val PREF_API_SYNC_ERROR = "api_sync_error"
    const val PREF_API_SYNC_ERROR_MESSAGE = "api_sync_error_message"
    const val ACTION_STATIONS_UPDATED = "at.plankt0n.streamplay.ACTION_STATIONS_UPDATED"



    // Recording Settings
    const val PREF_RECORDING_ENABLED = "recording_enabled"
    const val PREF_RECORDING_PATH = "recording_path"
    const val RECORD_START_HOLD_DURATION_MS: Long = 1500L  // 1.5 seconds hold to start recording

    // Dev Settings
    const val PREF_DEV_MENU_ENABLED = "dev_menu"

    //Spotify
    const val PREF_USE_SPOTIFY_META = "use_spotify_meta"
    const val PREF_SPOTIFY_CLIENT_ID = "spotify_client_id"
    const val PREF_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"

    const val KEY_META_LOGS_PREFS = "meta_log_prefs"
    const val KEY_META_LOGS = "metadata_logs"


    // mime types and charsets and file extensions
    const val CHARSET_UNDEFINDED = "undefined"
    const val MIME_TYPE_JPG = "image/jpeg"
    const val MIME_TYPE_PNG = "image/png"
    const val MIME_TYPE_MPEG = "audio/mpeg"
    const val MIME_TYPE_HLS = "application/vnd.apple.mpegurl.audio"
    const val MIME_TYPE_M3U = "audio/x-mpegurl"
    const val MIME_TYPE_PLS = "audio/x-scpls"
    const val MIME_TYPE_XML = "text/xml"
    const val MIME_TYPE_ZIP = "application/zip"
    const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"
    const val MIME_TYPE_UNSUPPORTED = "unsupported"
    val MIME_TYPES_M3U = arrayOf("application/mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl")
    val MIME_TYPES_PLS = arrayOf("audio/x-scpls", "application/pls+xml")
    val MIME_TYPES_HLS = arrayOf("application/vnd.apple.mpegurl", "application/vnd.apple.mpegurl.audio")
    val MIME_TYPES_MPEG = arrayOf("audio/mpeg")
    val MIME_TYPES_OGG = arrayOf("audio/ogg", "application/ogg", "audio/opus")
    val MIME_TYPES_AAC = arrayOf("audio/aac", "audio/aacp")
    val MIME_TYPES_IMAGE = arrayOf("image/png", "image/jpeg")
    val MIME_TYPES_FAVICON = arrayOf("image/x-icon", "image/vnd.microsoft.icon")
    val MIME_TYPES_ZIP = arrayOf("application/zip", "application/x-zip-compressed", "multipart/x-zip")

    // default values
    val DEFAULT_DATE: Date = Date(0L)
    const val DEFAULT_RFC2822_DATE: String = "Thu, 01 Jan 1970 01:00:00 +0100" // --> Date(0)
    const val EMPTY_STRING_RESOURCE: Int = 0

    const val CONNECTED_BANNER_DURATION_MS: Long = 1000L

    const val VOLUME_SLIDER_INITIAL_HIDE_DELAY_MS: Long = 5000L
    const val VOLUME_SLIDER_POST_ADJUST_HIDE_DELAY_MS: Long = 3000L

}
