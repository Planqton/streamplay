package at.plankt0n.streamplay.search

import com.google.gson.annotations.Expose

/**
 * Represents a codec entry returned by the RadioBrowser API.
 */
data class RadioBrowserCodec(
    @Expose val name: String,
    @Expose val stationcount: Int
)
