package com.xweather.maplibre.sample

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
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
    private lateinit var layersButton: ImageButton

    // Parallel to R.array.layer_labels, top to bottom stacking order; radar
    // is on by default.
    private val layerCodes = arrayOf(
        "radar",
        "satellite",
        "wind-particles",
        "lightning-strikes",
        "hail-threats",
        "temperatures",
    )
    private val layerChecked = booleanArrayOf(true, false, false, false, false, false)

    // Parallel to layerCodes: where each layer stacks relative to others.
    // Each is pinned directly beneath the one above it in the stack (falling
    // back to on top if that layer isn't currently added), chaining down to
    // radar at the very top — omitting beforeId there adds it above
    // everything, so re-toggling radar always restores it to the top.
    // Temperature is anchored to "boundary_county", the demo style's county
    // boundary layer, instead of chaining to hail: every other admin
    // boundary and place-name label in demo_style_url is layered above that
    // one, so this both keeps temperature under all of them and — since the
    // rest of the chain sits at/near the top of the whole map — leaves it
    // the bottom-most weather layer too. Tied to the "dark-matter-gl-style"
    // style currently in demo_style_url; a different base style would need
    // a different anchor layer id.
    private val layerBeforeIds = arrayOf(
        null,
        "radar",
        "satellite",
        "wind-particles",
        "lightning-strikes",
        "boundary_county",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webmap)

        val webView = findViewById<WebView>(R.id.webMapView)
        timelineController = findViewById(R.id.webTimelineController)
        layersButton = findViewById(R.id.layersButton)
        // Radar tiles haven't loaded yet: show the loading spinner and block playback.
        timelineController.showLoading()
        // Layer toggles need a ready controller too, so hold off until onLoad.
        layersButton.isEnabled = false

        val config = XweatherWebConfig(
            clientId = getString(R.string.xweather_client_id),
            clientSecret = getString(R.string.xweather_client_secret),
            styleUrl = getString(R.string.demo_style_url),
            centerLat = AUSTRALIA_CENTER_LAT,
            centerLon = AUSTRALIA_CENTER_LON,
            zoom = AUSTRALIA_ZOOM,
        )
        xweatherWeb = XweatherWebMapController(webView, config)
        timelineController.attachTimeline(xweatherWeb.timeline)

        layersButton.setOnClickListener { showLayersMenu() }

        xweatherWeb.onLoad = {
            xweatherWeb.addLayer(layerCodes[0], beforeId = layerBeforeIds[0])
            layersButton.isEnabled = true
        }
        xweatherWeb.onError = { message -> Log.e(TAG, "MapsGL error: $message") }
    }

    override fun onDestroy() {
        timelineController.release()
        super.onDestroy()
    }

    private fun showLayersMenu() {
        val labels = resources.getStringArray(R.array.layer_labels)
        AlertDialog.Builder(this)
            .setTitle(R.string.layers_dialog_title)
            .setMultiChoiceItems(labels, layerChecked) { _, which, checked ->
                layerChecked[which] = checked
                val code = layerCodes[which]
                if (checked) {
                    xweatherWeb.addLayer(code, beforeId = layerBeforeIds[which])
                } else {
                    xweatherWeb.removeLayer(code)
                }
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private companion object {
        const val TAG = "WebMapActivity"
        const val AUSTRALIA_CENTER_LAT = -25.2744
        const val AUSTRALIA_CENTER_LON = 133.7751
        const val AUSTRALIA_ZOOM = 2.8
    }
}
