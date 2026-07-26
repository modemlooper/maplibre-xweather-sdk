package com.xweather.maplibre

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
 */
class XweatherMapController(
    private val style: Style,
    private val config: XweatherConfig,
) {
    private val urlBuilder = XweatherTileUrlBuilder(config)
    private val layerOpacity = mutableMapOf<String, Float>()

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

    /** Removes [layer]'s layer and backing source from the style, if present. */
    fun removeLayer(layer: XweatherLayer) {
        style.removeLayer(layerId(layer))
        style.removeSource(sourceId(layer))
        layerOpacity.remove(layer.code)
    }

    /** Sets the raster opacity of an already-added [layer]. */
    fun setOpacity(layer: XweatherLayer, opacity: Float) {
        layerOpacity[layer.code] = opacity
        (style.getLayer(layerId(layer)) as? RasterLayer)
            ?.setProperties(PropertyFactory.rasterOpacity(opacity))
    }

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

    /** Starts building a frame-swapping animation/loop for [layer]. */
    fun animator(layer: XweatherLayer): XweatherAnimator = XweatherAnimator(style, config, layer)

    private fun sourceId(layer: XweatherLayer) = "xweather-${layer.code}-source"
    private fun layerId(layer: XweatherLayer) = "xweather-${layer.code}-layer"
}
