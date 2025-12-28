package at.plankt0n.streamplay.data

data class ExtendedMetaInfo(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val albumReleaseDate: String,
    val spotifyUrl: String,
    val bestCoverUrl: String?,
    val durationMs: Long,
    val popularity: Int,
    val previewUrl: String?,
    val genre: String
)
