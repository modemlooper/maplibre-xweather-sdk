package com.xweather.maplibre

import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

private const val TILE_SIZE = 256
private const val TILEJSON_VERSION = "2.1.0"

/**
 * Public entry point for adding Xweather weather layers to a MapLibre
 * [Style]. Host apps never touch [RasterSource]/[RasterLayer]/tile-URL
 * construction directly — this is the whole point of the SDK boundary.
 *
 * Get a [Style] instance via `mapLibreMap.getStyle { style -> ... }` (or the
 * `onStyleLoaded` callback of `setStyle`), then construct this controller.
 * [mapView] is needed so [timeline] can tell when a loaded frame's tiles have
 * actually finished downloading, rather than just when they've been requested.
 */
class XweatherMapController(
    private val mapView: MapView,
    private val style: Style,
    private val config: XweatherConfig,
) {
    private val urlBuilder = XweatherTileUrlBuilder(config)
    private val layerOpacity = mutableMapOf<String, Float>()

    /**
     * The shared animation clock for this map: register layers with it (via
     * [XweatherTimeline.addLayer]) to animate them together in lockstep,
     * instead of each layer running its own independent loop. Mirrors
     * `mapController.timeline` in Xweather's MapsGL Apple SDK.
     */
    val timeline: XweatherTimeline by lazy { XweatherTimeline(mapView, style, config) }

    /** Adds [layer] to the style, or updates its opacity if already added. */
    fun addLayer(layer: XweatherLayer, opacity: Float = 1f, offset: String = "current") {
        val sourceId = sourceId(layer)
        val layerId = layerId(layer)

        if (style.getSource(sourceId) == null) {
            val tileSet = TileSet(TILEJSON_VERSION, *urlBuilder.tileUrlTemplates(layer, offset).toTypedArray()).apply {
                attribution = XweatherAttribution.HTML
            }
            style.addSource(RasterSource(sourceId, tileSet, TILE_SIZE))
        }

        if (style.getLayer(layerId) == null) {
            style.addLayer(RasterLayer(layerId, sourceId))
        }

        setOpacity(layer, opacity)
    }

    /**
     * Adds a layer by its raw Xweather layer code (e.g. `"radar"`,
     * `"satellite-geocolor"`) instead of a typed [XweatherLayer] constant.
     * Equivalent to `addLayer(XweatherLayer.Custom(code), ...)` — useful for
     * layer codes not yet modeled in [XweatherLayer], or when the layer to
     * show is only known at runtime (e.g. driven by app config or user input).
     */
    fun addLayer(code: String, opacity: Float = 1f, offset: String = "current") =
        addLayer(XweatherLayer.Custom(code), opacity, offset)

    /** Removes [layer]'s layer and backing source from the style, if present. */
    fun removeLayer(layer: XweatherLayer) {
        style.removeLayer(layerId(layer))
        style.removeSource(sourceId(layer))
        layerOpacity.remove(layer.code)
    }

    /**
     * Removes a layer previously added by raw layer code, e.g. via
     * `addLayer(code)`. Equivalent to `removeLayer(XweatherLayer.Custom(code))`.
     */
    fun removeLayer(code: String) = removeLayer(XweatherLayer.Custom(code))

    /** Sets the raster opacity of an already-added [layer]. */
    fun setOpacity(layer: XweatherLayer, opacity: Float) {
        layerOpacity[layer.code] = opacity
        (style.getLayer(layerId(layer)) as? RasterLayer)
            ?.setProperties(PropertyFactory.rasterOpacity(opacity))
    }

    /** Sets the raster opacity of an already-added layer by its raw layer code. */
    fun setOpacity(code: String, opacity: Float) = setOpacity(XweatherLayer.Custom(code), opacity)

    /** True if a layer with the given raw layer [code] is currently added to the style. */
    fun hasLayer(code: String): Boolean = style.getLayer(layerId(XweatherLayer.Custom(code))) != null

    /** True if [layer] is currently added to the style. */
    fun hasLayer(layer: XweatherLayer): Boolean = style.getLayer(layerId(layer)) != null

    /**
     * Re-stacks the given layers bottom-to-top by removing and re-adding
     * them in list order (MapLibre stacks each new `addLayer` above the
     * previous one). Layers not already added via [addLayer] are skipped.
     */
    fun setLayerOrder(layersBottomToTop: List<XweatherLayer>) {
        val toReorder = layersBottomToTop.filter { style.getSource(sourceId(it)) != null }

        toReorder.forEach { layer -> style.removeLayer(layerId(layer)) }
        toReorder.forEach { layer ->
            style.addLayer(RasterLayer(layerId(layer), sourceId(layer)))
            setOpacity(layer, layerOpacity[layer.code] ?: 1f)
        }
    }

    private fun sourceId(layer: XweatherLayer) = "xweather-${layer.code}-source"
    private fun layerId(layer: XweatherLayer) = "xweather-${layer.code}-layer"
}
