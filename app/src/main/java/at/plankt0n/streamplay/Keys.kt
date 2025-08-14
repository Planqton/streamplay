package at.plankt0n.streamplay

import java.util.Date

object Keys {
    const val PREFS_NAME = "stream_prefs"
    const val KEY_STATIONS = "stations"
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
