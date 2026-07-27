package com.xweather.maplibre

import android.os.Handler
import android.os.Looper
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import kotlin.math.roundToInt

private const val TILE_SIZE = 256
private const val TILEJSON_VERSION = "2.1.0"

/**
 * A single shared clock that drives every weather layer registered with it,
 * in lockstep — one [play]/[stop]/[goTo] controls all of them together,
 * mirroring Xweather's MapsGL SDK (`mapController.timeline` in the iOS/
 * MapsGL Apple SDK), rather than each layer running its own independent
 * animation loop.
 *
 * That distinction matters here: Xweather's Raster Maps API has no built-in
 * timeline (unlike MapsGL's GPU-rendered layers), so frames are simulated by
 * pre-loading one raster source per time offset per layer and swapping
 * opacity. Giving each layer its own timer let multiple layers drift out of
 * sync with each other, and multiplied concurrent tile-loading pressure
 * (every layer's frames competing for the same request queue at once) badly
 * enough that frames were still loading mid-playback instead of before it
 * started. A single shared clock avoids both: one loop, and layers share one
 * loading signal ([onLoadStart]/[onLoadComplete]) instead of guessing
 * per-layer readiness independently.
 *
 * Get an instance via [XweatherMapController.timeline] rather than
 * constructing one directly.
 */
class XweatherTimeline internal constructor(
    private val mapView: MapView,
    private val style: Style,
    config: XweatherConfig,
) {
    private class LayerFrames(val sourceIds: List<String>, val layerIds: List<String>, val baseLayerId: String)

    private val urlBuilder = XweatherTileUrlBuilder(config)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Registration order is preserved so newly added layers stack above earlier ones,
    // same as XweatherMapController.addLayer.
    private val layers = LinkedHashMap<String, LayerFrames>()
    private val pendingLoads = mutableSetOf<String>()
    private var idleListener: MapView.OnDidBecomeIdleListener? = null

    private var offsets: List<String> = emptyList()
    private var currentIndex = 0
    private var playRunnable: Runnable? = null

    /** Number of frames spanning the configured time range; set via [configure]. */
    var frameCount: Int = 0
        private set

    /** Minutes between each frame; set via [configure]. */
    var frameIntervalMinutes: Int = 0
        private set

    /** 0f..1f position of the currently shown frame across the configured range. */
    val position: Float
        get() = if (offsets.size <= 1) 1f else currentIndex.toFloat() / (offsets.size - 1)

    /** True while [play] has an active loop running. */
    val isPlaying: Boolean
        get() = playRunnable != null

    /** True while any registered layer still has frames loading. */
    val isLoading: Boolean
        get() = pendingLoads.isNotEmpty()

    /** Invoked on the main thread every time the shown frame changes. */
    var onAdvance: (() -> Unit)? = null

    /** Invoked when the first pending layer starts loading frames. */
    var onLoadStart: (() -> Unit)? = null

    /** Invoked once every pending layer's frames have finished loading. */
    var onLoadComplete: (() -> Unit)? = null

    init {
        configure(frameCount = 22, frameIntervalMinutes = 10)
    }

    /**
     * Sets the time range every registered layer animates across: [frameCount]
     * frames, [frameIntervalMinutes] apart, ending at `"current"`. Re-loads
     * frames for any already-registered layers at the new offsets.
     */
    fun configure(frameCount: Int, frameIntervalMinutes: Int) {
        this.frameCount = frameCount
        this.frameIntervalMinutes = frameIntervalMinutes
        offsets = (frameCount - 1 downTo 0).map { stepsBack ->
            if (stepsBack == 0) "current" else "-${stepsBack * frameIntervalMinutes}m"
        }
        currentIndex = offsets.lastIndex.coerceAtLeast(0)
        val activeCodes = layers.keys.toList()
        activeCodes.forEach { code -> loadLayer(code) }
    }

    /** Registers [layer] with the timeline, pre-loading its frames at the configured offsets. */
    fun addLayer(layer: XweatherLayer) {
        loadLayer(layer.code)
    }

    /** Unregisters [layer], stopping its animation and removing its frame layers/sources. */
    fun removeLayer(layer: XweatherLayer) {
        val wasPending = pendingLoads.remove(layer.code)
        layers.remove(layer.code)?.let { frames ->
            frames.layerIds.forEach { style.removeLayer(it) }
            frames.sourceIds.forEach { style.removeSource(it) }
        }
        // If this was the last thing we were waiting on, resolve the loading state now —
        // otherwise onLoadComplete would never fire (it's only triggered by the idle
        // listener, which has nothing left to wait for) and a bound UI spinner would spin forever.
        if (wasPending && pendingLoads.isEmpty()) {
            onLoadComplete?.invoke()
        }
    }

    /** True if [layer] is currently registered with the timeline. */
    fun hasLayer(layer: XweatherLayer): Boolean = layers.containsKey(layer.code)

    private fun loadLayer(code: String) {
        layers[code]?.let { frames ->
            frames.layerIds.forEach { style.removeLayer(it) }
            frames.sourceIds.forEach { style.removeSource(it) }
        }

        val baseLayerId = "xweather-$code-layer"
        // The animated frames (including a "current" one) own this layer's rendering,
        // so hide the static layer XweatherMapController.addLayer may have added —
        // otherwise it stays visible underneath every frame.
        (style.getLayer(baseLayerId) as? RasterLayer)?.setProperties(PropertyFactory.rasterOpacity(0f))

        val frames = offsets.mapIndexed { index, offset ->
            val sourceId = "xweather-$code-timeline-$index-source"
            val frameLayerId = "xweather-$code-timeline-$index-layer"
            val tileSet = TileSet(TILEJSON_VERSION, *urlBuilder.tileUrlTemplates(XweatherLayer.Custom(code), offset).toTypedArray()).apply {
                attribution = XweatherAttribution.HTML
            }
            style.addSource(RasterSource(sourceId, tileSet, TILE_SIZE))
            style.addLayer(
                RasterLayer(frameLayerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.rasterOpacity(if (index == currentIndex) 1f else 0f),
                        // Hard-cut between frames instead of MapLibre's default 300ms
                        // cross-fade, which would double-expose two fully-opaque frames.
                        PropertyFactory.rasterFadeDuration(0f),
                    )
                },
            )
            sourceId to frameLayerId
        }
        layers[code] = LayerFrames(frames.map { it.first }, frames.map { it.second }, baseLayerId)

        beginLoading(code)
    }

    private fun beginLoading(code: String) {
        val wasEmpty = pendingLoads.isEmpty()
        pendingLoads.add(code)
        if (wasEmpty) onLoadStart?.invoke()

        if (idleListener == null) {
            val listener = object : MapView.OnDidBecomeIdleListener {
                override fun onDidBecomeIdle() {
                    mapView.removeOnDidBecomeIdleListener(this)
                    idleListener = null
                    if (pendingLoads.isNotEmpty()) {
                        pendingLoads.clear()
                        onLoadComplete?.invoke()
                    }
                }
            }
            idleListener = listener
            mapView.addOnDidBecomeIdleListener(listener)
        }
    }

    /**
     * Starts looping through configured frames together, showing one every
     * [intervalMillis], across every registered layer at once.
     */
    fun play(intervalMillis: Long = 500L) {
        stop()
        if (offsets.size <= 1) return
        val runnable = object : Runnable {
            override fun run() {
                currentIndex = (currentIndex + 1) % offsets.size
                showFrame(currentIndex)
                onAdvance?.invoke()
                mainHandler.postDelayed(this, intervalMillis)
            }
        }
        playRunnable = runnable
        mainHandler.postDelayed(runnable, intervalMillis)
    }

    /** Stops looping; the currently shown frame remains visible. */
    fun stop() {
        playRunnable?.let(mainHandler::removeCallbacks)
        playRunnable = null
    }

    /** Stops any running loop and shows whichever configured frame is closest to [fraction] (0f..1f). */
    fun goTo(fraction: Float) {
        if (offsets.isEmpty()) return
        stop()
        currentIndex = (fraction.coerceIn(0f, 1f) * (offsets.size - 1)).roundToInt()
        showFrame(currentIndex)
        onAdvance?.invoke()
    }

    private fun showFrame(index: Int) {
        layers.values.forEach { frames ->
            frames.layerIds.forEachIndexed { i, frameLayerId ->
                val opacity = if (i == index) 1f else 0f
                (style.getLayer(frameLayerId) as? RasterLayer)?.setProperties(PropertyFactory.rasterOpacity(opacity))
            }
        }
    }
}
