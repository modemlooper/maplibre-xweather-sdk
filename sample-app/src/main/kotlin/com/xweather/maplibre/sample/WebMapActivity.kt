package com.xweather.maplibre.sample

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.xweather.webview.XweatherWebConfig
import com.xweather.webview.XweatherWebMapController

/**
 * The app's sole entry point: [XweatherWebMapController] driving Xweather's
 * real MapsGL vector-rendered weather engine (the same one Xweather ships
 * for iOS/web) through a WebView, with [TimelineController] on top driving
 * its [XweatherWebMapController.timeline].
 */
class WebMapActivity : AppCompatActivity() {

    private lateinit var xweatherWeb: XweatherWebMapController
    private lateinit var timelineController: TimelineController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webmap)

        val webView = findViewById<WebView>(R.id.webMapView)
        timelineController = findViewById(R.id.webTimelineController)
        // Radar tiles haven't loaded yet: show the loading spinner and block playback.
        timelineController.showLoading()

        val config = XweatherWebConfig(
            clientId = getString(R.string.xweather_client_id),
            clientSecret = getString(R.string.xweather_client_secret),
            styleUrl = getString(R.string.demo_style_url),
            centerLat = AUSTRALIA_CENTER_LAT,
            centerLon = AUSTRALIA_CENTER_LON,
            zoom = AUSTRALIA_ZOOM,
        )
        xweatherWeb = XweatherWebMapController(webView, config)
        timelineController.attachWebTimeline(xweatherWeb.timeline)

        xweatherWeb.onLoad = { xweatherWeb.addLayer("radar") }
        xweatherWeb.onError = { message -> Log.e(TAG, "MapsGL error: $message") }
    }

    override fun onDestroy() {
        timelineController.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "WebMapActivity"
        const val AUSTRALIA_CENTER_LAT = -25.2744
        const val AUSTRALIA_CENTER_LON = 133.7751
        const val AUSTRALIA_ZOOM = 2.8
    }
}
