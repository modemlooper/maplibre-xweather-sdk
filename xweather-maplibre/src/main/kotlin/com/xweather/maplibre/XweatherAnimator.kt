package com.xweather.maplibre

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

private const val TILE_SIZE = 256
private const val TILEJSON_VERSION = "2.1.0"

/**
 * DIY frame-swapping loop for a single [XweatherLayer]: Xweather's raster
 * API has no built-in timeline (unlike MapsGL's), so this pre-loads one
 * source/layer per time offset and toggles opacity on a timer.
 */
class XweatherAnimator internal constructor(
    private val style: Style,
    config: XweatherConfig,
    private val layer: XweatherLayer,
) {
    private val urlBuilder = XweatherTileUrlBuilder(config)
    private var frameLayerIds: List<String> = emptyList()
    private var currentFrame = 0
    private var timer: Timer? = null

    /** Pre-loads one frame per offset string (e.g. `"-30m"`, `"-20m"`, `"-10m"`, `"current"`). */
    fun loadFrames(offsets: List<String>): XweatherAnimator {
        clearFrames()
        frameLayerIds = offsets.mapIndexed { index, offset ->
            val sourceId = "xweather-${layer.code}-anim-$index-source"
            val frameLayerId = "xweather-${layer.code}-anim-$index-layer"
            val tileSet = TileSet(TILEJSON_VERSION, *urlBuilder.tileUrlTemplates(layer, offset).toTypedArray()).apply {
                attribution = XweatherAttribution.HTML
            }
            style.addSource(RasterSource(sourceId, tileSet, TILE_SIZE))
            style.addLayer(
                RasterLayer(frameLayerId, sourceId).apply {
                    setProperties(PropertyFactory.rasterOpacity(0f))
                },
            )
            frameLayerId
        }
        currentFrame = 0
        return this
    }

    /**
     * Convenience overload generating [count] evenly spaced frames ending at
     * `"current"`, [intervalMinutes] apart.
     *
     * NOTE: the exact offset string format (e.g. whether it's `"-30m"`,
     * `"-1800s"`, or something else) has not yet been confirmed against
     * `/docs/maps/reference/forecast-layer-intervals` — verify before
     * relying on this in production, per PLAN.md.
     */
    fun loadFrames(count: Int, intervalMinutes: Int): XweatherAnimator {
        val offsets = (count - 1 downTo 0).map { stepsBack ->
            if (stepsBack == 0) "current" else "-${stepsBack * intervalMinutes}m"
        }
        return loadFrames(offsets)
    }

    /** Starts looping through loaded frames, showing one every [intervalMillis]. */
    fun play(intervalMillis: Long = 500L) {
        stop()
        if (frameLayerIds.isEmpty()) return
        timer = fixedRateTimer(period = intervalMillis, initialDelay = 0) {
            showFrame(currentFrame)
            currentFrame = (currentFrame + 1) % frameLayerIds.size
        }
    }

    /** Stops looping; the currently shown frame remains visible. */
    fun stop() {
        timer?.cancel()
        timer = null
    }

    private fun showFrame(index: Int) {
        frameLayerIds.forEachIndexed { i, frameLayerId ->
            val opacity = if (i == index) 1f else 0f
            (style.getLayer(frameLayerId) as? RasterLayer)
                ?.setProperties(PropertyFactory.rasterOpacity(opacity))
        }
    }

    private fun clearFrames() {
        stop()
        frameLayerIds.forEach { frameLayerId -> style.removeLayer(frameLayerId) }
        frameLayerIds = emptyList()
    }
}
