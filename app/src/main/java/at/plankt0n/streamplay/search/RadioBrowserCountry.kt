package at.plankt0n.streamplay.search

import com.google.gson.annotations.Expose

/**
 * Represents a country entry returned by the RadioBrowser API.
 */
data class RadioBrowserCountry(
    @Expose val name: String,
    @Expose val iso_3166_1: String,
    @Expose val stationcount: Int
)
