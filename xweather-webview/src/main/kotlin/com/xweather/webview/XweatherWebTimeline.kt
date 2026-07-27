package com.xweather.webview

import android.webkit.WebView

/**
 * Forwards to the real MapsGL JS SDK's `controller.timeline`, which natively
 * animates every added weather layer in lockstep — one shared clock, driven
 * by [play]/[stop]/[goTo].
 *
 * Get an instance via [XweatherWebMapController.timeline] rather than
 * constructing one directly.
 */
class XweatherWebTimeline internal constructor(private val webView: WebView) {

    /** True while the JS timeline is actively playing. */
    var isPlaying: Boolean = false
        internal set

    /** 0f..1f position of the current frame, as last reported by the JS timeline. */
    var position: Float = 0f
        internal set

    /** Hour-of-day (0-23) the visible date range starts at, as last reported by the JS timeline. */
    var startHour: Int = 0
        internal set

    /** Hour-of-day the visible date range ends at; may exceed 23 if the range crosses midnight. */
    var endHour: Int = 3
        internal set

    /** 0f..1f fraction of "now" within [startHour, endHour], as last reported by the JS timeline. */
    var nowFraction: Float = 0.5f
        internal set

    /** Invoked on the main thread every time the JS timeline advances a frame. */
    var onAdvance: (() -> Unit)? = null

    /** Invoked when a registered weather layer starts loading data. */
    var onLoadStart: (() -> Unit)? = null

    /** Invoked once every registered weather layer has finished loading. */
    var onLoadComplete: (() -> Unit)? = null

    /** Invoked once the timeline's visible date range ([startHour]/[endHour]/[nowFraction]) is known. */
    var onRangeChange: (() -> Unit)? = null

    /** Starts looping the timeline from its current position. */
    fun play() = evaluate("timelinePlay()")

    /** Stops looping; the current frame remains visible. */
    fun stop() = evaluate("timelineStop()")

    /** Stops any running loop and moves to whichever frame is closest to [fraction] (0f..1f). */
    fun goTo(fraction: Float) = evaluate("timelineGoTo(${fraction.coerceIn(0f, 1f)})")

    private fun evaluate(script: String) {
        webView.post { webView.evaluateJavascript(script, null) }
    }
}
