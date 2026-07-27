package com.xweather.webview

/**
 * Credentials and base map style for the WebView-hosted MapsGL controller.
 * Host apps supply their own [clientId]/[clientSecret] — the SDK never
 * stores or bundles credentials itself.
 *
 * [styleUrl] defaults to a public, key-free MapLibre style so the SDK works
 * out of the box; pass your own for production use.
 */
data class XweatherWebConfig(
    val clientId: String,
    val clientSecret: String,
    val styleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
    val centerLat: Double = 20.0,
    val centerLon: Double = 0.0,
    val zoom: Double = 2.0,
)
