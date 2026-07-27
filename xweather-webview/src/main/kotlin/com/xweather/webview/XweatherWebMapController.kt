package com.xweather.webview

import android.content.pm.ApplicationInfo
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

private const val ASSET_URL = "file:///android_asset/xweather_map.html"

/**
 * Public entry point for driving Xweather's real MapsGL JS SDK from Kotlin.
 * Wraps a [WebView] hosting MapLibre GL JS + `aerisweather.mapsgl.js` — the
 * same vector-rendered weather engine Xweather ships for iOS/web — so host
 * apps get MapsGL feature parity without any native GPU rendering code of
 * our own (MapsGL's actual rendering engine is closed-source).
 *
 * [webView] is owned by the caller (add it to your layout as normal); this
 * controller configures its settings, loads the bundled map page, and drives
 * it via a JS bridge. Construct it once the [webView] is attached.
 */
class XweatherWebMapController(
    private val webView: WebView,
    private val config: XweatherWebConfig,
) {

    /** Invoked once the MapsGL controller has finished initializing. */
    var onLoad: (() -> Unit)? = null

    /** Invoked with the queried feature (as raw JSON) when the map is tapped. */
    var onMapClick: ((lat: Double, lon: Double, featureJson: String) -> Unit)? = null

    /** Invoked with any JS-side error (thrown exceptions, uncaught errors). */
    var onError: ((message: String) -> Unit)? = null

    /**
     * The shared animation clock for this map, backed by the JS SDK's own
     * `controller.timeline` — registering a layer via [addLayer] animates it
     * in lockstep with every other registered layer.
     */
    val timeline: XweatherWebTimeline = XweatherWebTimeline(webView)

    private val hasLayerCallbacks = mutableMapOf<Int, (Boolean) -> Unit>()
    private val nextRequestId = AtomicInteger(0)

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val isDebuggable =
            (webView.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(isDebuggable)
        webView.addJavascriptInterface(Bridge(), "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                evaluate(
                    "configure(${jsString(config.clientId)}, " +
                        "${jsString(config.clientSecret)}, ${jsString(config.styleUrl)}, " +
                        "${config.centerLon}, ${config.centerLat}, ${config.zoom})",
                )
            }
        }
        webView.loadUrl(ASSET_URL)
    }

    /**
     * Adds a weather layer by its Xweather layer code (e.g. `"radar"`), or
     * updates its opacity if already added. Calls made before [onLoad] fires
     * are reported via [onError] instead of applied — wait for [onLoad]
     * before adding layers.
     */
    fun addLayer(code: String, opacity: Float = 1f) =
        evaluate("addLayer(${jsString(code)}, $opacity)")

    /** Removes a previously added weather layer. */
    fun removeLayer(code: String) = evaluate("removeLayer(${jsString(code)})")

    /** Sets the opacity of an already-added weather layer. */
    fun setOpacity(code: String, opacity: Float) =
        evaluate("setOpacity(${jsString(code)}, $opacity)")

    /** Asynchronously reports whether a weather layer with [code] is currently added. */
    fun hasLayer(code: String, callback: (Boolean) -> Unit) {
        val requestId = nextRequestId.incrementAndGet()
        hasLayerCallbacks[requestId] = callback
        evaluate("hasLayer(${jsString(code)}, $requestId)")
    }

    private fun jsString(value: String): String = JSONObject.quote(value)

    private fun evaluate(script: String) {
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onLoad() = webView.post { onLoad?.invoke() }

        @JavascriptInterface
        fun onMapClick(lat: Double, lon: Double, featureJson: String) = webView.post {
            onMapClick?.invoke(lat, lon, featureJson)
        }

        @JavascriptInterface
        fun onLoadStart() = webView.post { timeline.onLoadStart?.invoke() }

        @JavascriptInterface
        fun onLoadComplete() = webView.post { timeline.onLoadComplete?.invoke() }

        @JavascriptInterface
        fun onTimelinePlay() = webView.post { timeline.isPlaying = true }

        @JavascriptInterface
        fun onTimelineStop() = webView.post { timeline.isPlaying = false }

        @JavascriptInterface
        fun onTimelineAdvance(position: Float, date: String) = webView.post {
            timeline.position = position
            timeline.onAdvance?.invoke()
        }

        @JavascriptInterface
        fun onTimelineRange(startHour: Int, endHour: Int, nowFraction: Float) = webView.post {
            timeline.startHour = startHour
            timeline.endHour = endHour
            timeline.nowFraction = nowFraction
            timeline.onRangeChange?.invoke()
        }

        @JavascriptInterface
        fun onHasLayerResult(requestId: Int, hasLayer: Boolean) = webView.post {
            hasLayerCallbacks.remove(requestId)?.invoke(hasLayer)
        }

        @JavascriptInterface
        fun onError(message: String) = webView.post { onError?.invoke(message) }
    }
}
