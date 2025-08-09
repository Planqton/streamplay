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
    const val ACTION_SHOW_BANNER = "at.plankt0n.streamplay.ACTION_SHOW_BANNER"
    const val EXTRA_COUNTDOWN_DURATION = "countdown_duration"
    const val EXTRA_BANNER_MESSAGE = "banner_message"
    const val EXTRA_BANNER_TYPE = "banner_type"
    const val UPDATE_FORCE_TAP_COUNT = 5

    const val BANNER_TYPE_INFO = 0
    const val BANNER_TYPE_SUCCESS = 1
    const val BANNER_TYPE_ERROR = 2



    //Spotify
    const val KEY_SPOTIFY_CLIENT_ID = "cc55a94b922c496a84c4a725242a313b"
    const val KEY_SPOTIFY_CLIENT_SECRET = "1893b26ecec74a4984152f0d86200b63"

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

}
