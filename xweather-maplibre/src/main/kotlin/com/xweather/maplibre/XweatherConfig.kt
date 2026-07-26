package com.xweather.maplibre

/**
 * Credentials and endpoint configuration for the Xweather Raster Maps API.
 * Host apps supply their own [clientId]/[clientSecret] — the SDK never
 * bundles or stores credentials itself.
 */
data class XweatherConfig(
    val clientId: String,
    val clientSecret: String,
    val host: String = "api.xweather.com",
    val subdomains: List<String> = listOf("1", "2", "3", "4"),
    val format: String = "png",
)
