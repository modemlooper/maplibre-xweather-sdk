package com.xweather.maplibre

/**
 * Builds Xweather Raster Maps tile URL templates:
 * `https://maps{subdomain}.{host}/{clientId}_{clientSecret}/{layer}/{z}/{x}/{y}/{offset}.{format}`
 *
 * MapLibre's raster sources load-balance across a list of full tile URL
 * templates (like a TileJSON `tiles` array) rather than a `{s}`-style
 * subdomain placeholder, so this returns one template per configured
 * subdomain instead of a single templated string.
 */
internal class XweatherTileUrlBuilder(private val config: XweatherConfig) {

    fun tileUrlTemplates(layer: XweatherLayer, offset: String = "current"): List<String> =
        config.subdomains.map { subdomain ->
            "https://maps$subdomain.${config.host}/${config.clientId}_${config.clientSecret}/" +
                "${layer.code}/{z}/{x}/{y}/$offset.${config.format}"
        }
}
