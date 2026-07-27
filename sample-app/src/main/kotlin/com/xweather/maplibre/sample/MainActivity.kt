package com.xweather.maplibre.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.xweather.maplibre.XweatherConfig
import com.xweather.maplibre.XweatherLayer
import com.xweather.maplibre.XweatherMapController
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var timelineController: TimelineController
    private lateinit var layerMenuButton: LayerMenuButton

    // Set once the map's style finishes loading (setStyle's callback is async); the
    // layer menu can be tapped before that happens, so toggles are guarded with `?.`.
    private var xweatherMap: XweatherMapController? = null

    // True once timelineController has been attached to the shared timeline for the
    // first time — later onLoadComplete firings (e.g. a menu layer added afterwards)
    // should just clear the spinner, not re-run the one-time attach/seek-to-now setup.
    private var timelineAttached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        MapLibre.getInstance(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        timelineController = findViewById(R.id.timelineController)
        // Radar frames haven't loaded yet: show the loading spinner and block playback.
        timelineController.showLoading()

        layerMenuButton = findViewById(R.id.layerMenuButton)
        layerMenuButton.listener = LayerMenuButton.OnLayerToggleListener { layer, enabled ->
            val timeline = xweatherMap?.timeline ?: return@OnLayerToggleListener
            val xweatherLayer = xweatherLayerFor(layer)
            if (enabled) timeline.addLayer(xweatherLayer) else timeline.removeLayer(xweatherLayer)
        }

        mapView.getMapAsync { map ->
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(AUSTRALIA_CENTER, AUSTRALIA_ZOOM))

            map.setStyle(getString(R.string.demo_style_url)) { style: Style ->
                val config = XweatherConfig(
                    clientId = getString(R.string.xweather_client_id),
                    clientSecret = getString(R.string.xweather_client_secret),
                )
                val xweatherMap = XweatherMapController(mapView, style, config)
                this.xweatherMap = xweatherMap

                // One shared clock drives every registered layer together (mirrors
                // `mapController.timeline` in Xweather's MapsGL Apple SDK) — layers
                // toggled on later via the layer menu register with this same
                // instance and animate in sync with the radar sweep below, instead
                // of each layer running its own independent, unsynced loop.
                val timeline = xweatherMap.timeline
                timeline.configure(frameCount = FRAME_COUNT, frameIntervalMinutes = FRAME_INTERVAL_MINUTES)
                timeline.onLoadStart = { timelineController.showLoading() }
                timeline.onLoadComplete = {
                    if (!timelineAttached) {
                        timelineController.attachTimeline(timeline, FRAME_INTERVAL_MILLIS)
                        timelineAttached = true
                    } else {
                        timelineController.hideLoading()
                    }
                }
                timeline.addLayer(XweatherLayer.Radar.Global)
            }
        }
    }

    /**
     * Maps each layer-menu entry to the Xweather Raster Maps layer it toggles.
     * [LayerMenuButton.Layer.WIND_PARTICLES] has no true particle-animation
     * equivalent in this raster-tile API (that's a MapsGL/vector-engine
     * feature, out of scope here per PLAN.md) — [XweatherLayer.Wind.Speed] is
     * the closest raster stand-in; it's still frame-animated like the others,
     * just not rendered as flowing particles.
     */
    private fun xweatherLayerFor(layer: LayerMenuButton.Layer): XweatherLayer = when (layer) {
        LayerMenuButton.Layer.RADAR -> XweatherLayer.Radar.Standard
        LayerMenuButton.Layer.TEMPERATURE -> XweatherLayer.Temperature.Surface
        LayerMenuButton.Layer.WIND_PARTICLES -> XweatherLayer.Wind.Speed
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        timelineController.release()
        layerMenuButton.dismissMenu()
        mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        // Every layer (the always-on radar sweep and anything toggled via the layer
        // menu) shares this one frame count/interval via XweatherMapController.timeline.
        // Frame count stays modest to keep concurrent tile-source/request pressure
        // low (see XweatherTimeline's doc comment) — but the interval between frames
        // must stay small too: at a coarse interval (e.g. 15m), fast-moving
        // precipitation shifts a large, visually discontinuous distance between
        // frames, which reads as the radar "jumping" forward in time rather than
        // sweeping smoothly. 5m keeps that per-step delta small without adding more
        // concurrent sources — it just covers a shorter total time range (55m here).
        const val FRAME_COUNT = 12
        const val FRAME_INTERVAL_MINUTES = 5
        const val FRAME_INTERVAL_MILLIS = 1000L

        private val AUSTRALIA_CENTER = LatLng(-25.2744, 133.7751)
        private const val AUSTRALIA_ZOOM = 2.8
    }
}
