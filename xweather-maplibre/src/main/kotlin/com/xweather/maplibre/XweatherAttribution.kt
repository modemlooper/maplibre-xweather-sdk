package com.xweather.maplibre

/**
 * Required attribution per Xweather's attribution guidelines
 * (docs/weather-api/resources/attribution): the phrase below must be
 * displayed as a hyperlink to [URL]. The logo may substitute for the word
 * "Xweather" but must never be used alone without the company name.
 */
object XweatherAttribution {
    const val TEXT = "Powered by Vaisala Xweather"
    const val URL = "https://www.xweather.com/"

    /** HTML attribution string, suitable for a MapLibre source's `attribution` field. */
    val HTML: String = "<a href=\"$URL\" target=\"_blank\">$TEXT</a>"
}
