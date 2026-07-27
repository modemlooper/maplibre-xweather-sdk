package com.xweather.webview

/**
 * Credentials and base map style for the WebView-hosted MapsGL controller.
 * Host apps supply their own [clientId]/[clientSecret] — the SDK never
 * stores or bundles credentials itself.
 *
 * [styleUrl] defaults to a public, key-free MapLibre style so the SDK works
 * out of the box; pass your own for production use.
 *
 * MapLibre's on-map attribution control is disabled (there's no toggle for
 * this — it's always off), so host apps must surface the required
 * attribution themselves elsewhere in the UI (e.g. a Settings/About screen):
 * "Powered by Vaisala Xweather" hyperlinked to https://www.xweather.com/,
 * plus whatever [styleUrl]'s basemap provider requires (e.g. CARTO styles
 * require "© CARTO" / "© OpenStreetMap contributors").
 */
data class XweatherWebConfig(
    val clientId: String,
    val clientSecret: String,
    val styleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
    val centerLat: Double = 20.0,
    val centerLon: Double = 0.0,
    val zoom: Double = 2.0,
    val minZoom: Double? = null,
    val bounds: XweatherMapBounds? = null,
)

/**
 * A hard camera-pan clamp (MapLibre GL JS's `maxBounds`) — the map won't
 * scroll past these edges. Pass to [XweatherWebConfig.bounds] to keep the
 * map framed on a region of interest.
 */
data class XweatherMapBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)
