package at.plankt0n.streamplay.search

import com.google.gson.annotations.Expose

/**
 * Represents a language entry returned by the RadioBrowser API.
 */
data class RadioBrowserLanguage(
    @Expose val name: String,
    @Expose val stationcount: Int
)
