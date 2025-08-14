package at.plankt0n.streamplay.search

import com.google.gson.annotations.Expose

/**
 * Represents a tag/genre entry returned by the RadioBrowser API.
 */
data class RadioBrowserTag(
    @Expose val name: String,
    @Expose val stationcount: Int
)

