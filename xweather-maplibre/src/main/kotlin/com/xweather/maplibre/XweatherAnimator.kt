package com.xweather.maplibre

import android.os.Handler
import android.os.Looper
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlin.math.roundToInt

private const val TILE_SIZE = 256
private const val TILEJSON_VERSION = "2.1.0"

/**
 * DIY frame-swapping loop for a single [XweatherLayer]: Xweather's raster
 * API has no built-in timeline (unlike MapsGL's), so this pre-loads one
 * source/layer per time offset and toggles opacity on a timer.
 */
class XweatherAnimator internal constructor(
    private val mapView: MapView,
    private val style: Style,
    config: XweatherConfig,
    private val layer: XweatherLayer,
) {
    private val urlBuilder = XweatherTileUrlBuilder(config)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val baseLayerId = "xweather-${layer.code}-layer"
    private var frameLayerIds: List<String> = emptyList()
    private var currentFrame = 0
    private var timer: Timer? = null
    private var idleListener: MapView.OnDidBecomeIdleListener? = null

    /**
     * Pre-loads one frame per offset string (e.g. `"-30m"`, `"-20m"`, `"-10m"`, `"current"`).
     *
     * Adding the sources/layers only *starts* their tile downloads — it does not wait
     * for them to finish. If [onFramesReady] is given, it's invoked once the map has
     * no more pending tile/style work (via [MapView.OnDidBecomeIdleListener]), so
     * callers can hold off enabling playback until every frame actually has pixels,
     * instead of playing through frames that are still blank mid-download.
     */
    fun loadFrames(offsets: List<String>, onFramesReady: (() -> Unit)? = null): XweatherAnimator {
        clearFrames()
        // The animated frames (including a "current" one) now own this layer's
        // rendering, so hide the static layer XweatherMapController.addLayer may
        // have added — otherwise it stays visible underneath every frame,
        // making the sweep look like tiles are stacking on each other.
        (style.getLayer(baseLayerId) as? RasterLayer)
            ?.setProperties(PropertyFactory.rasterOpacity(0f))
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
        if (onFramesReady != null) awaitIdle(onFramesReady)
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
    fun loadFrames(count: Int, intervalMinutes: Int, onFramesReady: (() -> Unit)? = null): XweatherAnimator {
        val offsets = (count - 1 downTo 0).map { stepsBack ->
            if (stepsBack == 0) "current" else "-${stepsBack * intervalMinutes}m"
        }
        return loadFrames(offsets, onFramesReady)
    }

    /** Registers a one-shot listener that fires once all pending tile/style loads finish. */
    private fun awaitIdle(onFramesReady: () -> Unit) {
        idleListener?.let(mapView::removeOnDidBecomeIdleListener)
        val listener = object : MapView.OnDidBecomeIdleListener {
            override fun onDidBecomeIdle() {
                mapView.removeOnDidBecomeIdleListener(this)
                idleListener = null
                onFramesReady()
            }
        }
        idleListener = listener
        mapView.addOnDidBecomeIdleListener(listener)
    }

    /**
     * Starts looping through loaded frames, showing one every [intervalMillis].
     * [onFrame], if given, is invoked on the main thread after each frame swap
     * with the shown frame's index and the total frame count, so callers can
     * keep other UI (e.g. a scrubber) in sync with the animation.
     */
    fun play(intervalMillis: Long = 500L, onFrame: ((index: Int, total: Int) -> Unit)? = null) {
        stop()
        if (frameLayerIds.isEmpty()) return
        val total = frameLayerIds.size
        timer = fixedRateTimer(period = intervalMillis, initialDelay = 0) {
            mainHandler.post {
                showFrame(currentFrame)
                onFrame?.invoke(currentFrame, total)
                currentFrame = (currentFrame + 1) % total
            }
        }
    }

    /** Stops looping; the currently shown frame remains visible. */
    fun stop() {
        timer?.cancel()
        timer = null
    }

    /**
     * Stops any running loop and shows whichever loaded frame is closest to
     * [fraction] (0f..1f across the loaded frames), for scrubbing.
     */
    fun seekToFraction(fraction: Float) {
        if (frameLayerIds.isEmpty()) return
        stop()
        currentFrame = (fraction.coerceIn(0f, 1f) * (frameLayerIds.size - 1)).roundToInt()
        showFrame(currentFrame)
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
        idleListener?.let(mapView::removeOnDidBecomeIdleListener)
        idleListener = null
        frameLayerIds.forEach { frameLayerId -> style.removeLayer(frameLayerId) }
        frameLayerIds = emptyList()
    }
}
