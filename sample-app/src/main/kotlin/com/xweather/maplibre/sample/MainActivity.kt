package com.xweather.maplibre.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.xweather.maplibre.XweatherAnimator
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

    override fun onCreate(savedInstanceState: Bundle?) {
        MapLibre.getInstance(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)

        timelineController = findViewById(R.id.timelineController)
        // Radar frames haven't loaded yet: show the loading spinner and block playback.
        timelineController.showLoading()

        mapView.getMapAsync { map ->
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(AUSTRALIA_CENTER, AUSTRALIA_ZOOM))

            map.setStyle(getString(R.string.demo_style_url)) { style: Style ->
                val config = XweatherConfig(
                    clientId = getString(R.string.xweather_client_id),
                    clientSecret = getString(R.string.xweather_client_secret),
                )
                val xweatherMap = XweatherMapController(mapView, style, config)
                xweatherMap.addLayer(XweatherLayer.Radar.Global, opacity = 0.8f)

                lateinit var radarAnimator: XweatherAnimator
                radarAnimator = xweatherMap.animator(XweatherLayer.Radar.Global)
                    .loadFrames(
                        count = RADAR_FRAME_COUNT,
                        intervalMinutes = RADAR_FRAME_INTERVAL_MINUTES,
                        onFramesReady = {
                            timelineController.attachAnimator(radarAnimator, RADAR_FRAME_COUNT, RADAR_FRAME_INTERVAL_MILLIS)
                        },
                    )
            }
        }
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
        mapView.onDestroy()
        super.onDestroy()
    }

    private companion object {
        const val RADAR_FRAME_COUNT = 22
        const val RADAR_FRAME_INTERVAL_MINUTES = 10
        const val RADAR_FRAME_INTERVAL_MILLIS = 1000L

        private val AUSTRALIA_CENTER = LatLng(-25.2744, 133.7751)
        private const val AUSTRALIA_ZOOM = 2.8
    }
}
