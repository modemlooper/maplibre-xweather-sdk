package com.xweather.maplibre.sample

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.xweather.maplibre.XweatherTimeline
import com.xweather.webview.XweatherWebTimeline
import kotlin.math.roundToInt

private fun Context.dp(value: Float) = value * resources.displayMetrics.density

/**
 * A self-contained "radar remote": a play/pause button, a loading spinner, and
 * an hour-tick ruler with a draggable knob, wired directly to a shared
 * [XweatherTimeline]. Drop it into any layout, call [attachTimeline] once the
 * timeline's first frames are loaded, and this view owns playback, scrubbing,
 * and the "Now" indicator end to end — hosts don't need their own play/pause
 * or scrub-tween logic. Additional layers can register with the same
 * [XweatherTimeline] afterwards (e.g. via a layer-toggle menu) and will
 * animate in sync with whatever this control is already doing.
 */
class TimelineController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    fun interface OnScrubListener {
        fun onProgressChanged(fraction: Float, fromUser: Boolean)
    }

    /** Optional hook for hosts that want to observe scrub events (e.g. to sync other UI). */
    var listener: OnScrubListener? = null

    /** Start/end hour of the visible ruler, in 24h time. */
    var startHour: Int
        get() = ruler.startHour
        set(value) {
            ruler.startHour = value
            ruler.invalidate()
        }
    var endHour: Int
        get() = ruler.endHour
        set(value) {
            ruler.endHour = value
            ruler.invalidate()
        }

    /** The fraction that represents "now" — rendered with the persistent orange "Now" tick. */
    var nowProgress: Float
        get() = ruler.nowProgress
        set(value) {
            ruler.nowProgress = value
            ruler.invalidate()
        }

    /** 0f..1f position of the knob across the [startHour, endHour] range. */
    var progress: Float
        get() = ruler.progress
        set(value) {
            ruler.progress = value
        }

    private val playProgress: CircularProgressIndicator
    private val playButton: ImageButton
    private val ruler: RulerView

    private var timeline: XweatherTimeline? = null
    private var webTimeline: XweatherWebTimeline? = null
    private var frameCount: Int = 1
    private var frameIntervalMillis: Long = 500L
    private var isPlaying = false
    private var scrubAnimator: ValueAnimator? = null
    private var scrubStep = 0f
    private var scrubTarget = 0f
    private var scrubRestartPending = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padStart = context.dp(8f).roundToInt()
        val padTop = context.dp(6f).roundToInt()
        val padEnd = context.dp(15f).roundToInt()
        val padBottom = context.dp(6f).roundToInt()
        setPadding(padStart, padTop, padEnd, padBottom)

        val iconSize = context.dp(56f).roundToInt()
        val iconFrame = FrameLayout(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
        }

        playProgress = CircularProgressIndicator(context).apply {
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
            isIndeterminate = true
            indicatorSize = iconSize
            indicatorInset = 0
            trackThickness = context.dp(2f).roundToInt()
            trackCornerRadius = context.dp(2f).roundToInt()
            setIndicatorColor(ContextCompat.getColor(context, R.color.play_ring_progress))
            trackColor = ContextCompat.getColor(context, R.color.play_ring_track)
            visibility = View.GONE
        }

        val buttonSize = context.dp(48f).roundToInt()
        val buttonPadding = context.dp(5f).roundToInt()
        playButton = ImageButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.CENTER)
            setBackgroundResource(R.drawable.bg_play_button)
            setImageResource(R.drawable.ic_play)
            setPadding(buttonPadding, buttonPadding, buttonPadding, buttonPadding)
            contentDescription = context.getString(R.string.timeline_play)
            isEnabled = false
            setOnClickListener { togglePlayback() }
        }

        iconFrame.addView(playProgress)
        iconFrame.addView(playButton)

        val rulerMarginStart = context.dp(6f).roundToInt()
        ruler = RulerView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = rulerMarginStart
            }
            onScrub = { fraction, fromUser -> onRulerScrub(fraction, fromUser) }
        }

        addView(iconFrame)
        addView(ruler)
    }

    /**
     * Attaches the shared [XweatherTimeline] this control plays/scrubs, hides
     * the loading spinner, and enables the play button. Call this once, after
     * the timeline's first registered layer(s) have finished their initial
     * load (e.g. from [XweatherTimeline.onLoadComplete]) — layers registered
     * with the same timeline later (see class doc) don't need to call this
     * again; use [showLoading]/[hideLoading] for their loading state instead.
     */
    fun attachTimeline(timeline: XweatherTimeline, frameIntervalMillis: Long) {
        this.timeline = timeline
        this.webTimeline = null
        this.frameCount = timeline.frameCount
        this.frameIntervalMillis = frameIntervalMillis
        timeline.onAdvance = { onTimelineAdvance() }
        hideLoading()

        // Frames load with every layer hidden (opacity 0); without an explicit
        // seek nothing renders until the user presses play. The last loaded
        // frame is always "current", so show it immediately and park the
        // knob at the "Now" tick to match.
        timeline.goTo(1f)
        ruler.progress = ruler.nowProgress
    }

    /**
     * Attaches a WebView-hosted [XweatherWebTimeline] instead of the native
     * raster timeline. Unlike [attachTimeline], this can be called as soon as
     * the timeline exists (no need to wait for a first load) — [showLoading]/
     * [hideLoading] are driven by [XweatherWebTimeline.onLoadStart]/
     * [XweatherWebTimeline.onLoadComplete] instead. The JS SDK already
     * advances smoothly on its own, so unlike the raster timeline's discrete
     * frame swaps, the knob mirrors [XweatherWebTimeline.position] directly
     * with no tweening.
     */
    fun attachWebTimeline(webTimeline: XweatherWebTimeline) {
        this.webTimeline = webTimeline
        this.timeline = null
        webTimeline.onAdvance = { ruler.progress = webTimeline.position }
        webTimeline.onLoadStart = { showLoading() }
        webTimeline.onLoadComplete = { hideLoading() }
        webTimeline.onRangeChange = {
            startHour = webTimeline.startHour
            endHour = webTimeline.endHour
            nowProgress = webTimeline.nowFraction
            ruler.progress = webTimeline.nowFraction
        }
    }

    /** Shows the loading spinner and disables the play button. */
    fun showLoading() {
        playButton.isEnabled = false
        playProgress.visibility = View.VISIBLE
    }

    /** Hides the loading spinner and enables the play button. */
    fun hideLoading() {
        playButton.isEnabled = true
        playProgress.visibility = View.GONE
    }

    /** Stops playback/scrubbing; call from the host's `onDestroy`. */
    fun release() {
        scrubAnimator?.cancel()
        timeline?.stop()
        webTimeline?.stop()
    }

    private fun togglePlayback() {
        isPlaying = !isPlaying
        playButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)

        val webTimeline = this.webTimeline
        if (webTimeline != null) {
            if (isPlaying) webTimeline.play() else webTimeline.stop()
            return
        }

        if (isPlaying) {
            // Resume the sweep from wherever the knob is currently parked (e.g. "Now")
            // rather than resetting to the first frame, so the first lap is shorter.
            scrubStep = 1f / (frameCount - 1).coerceAtLeast(1)
            scrubTarget = ruler.progress
            scrubRestartPending = false
            timeline?.play(intervalMillis = frameIntervalMillis)
        } else {
            scrubAnimator?.cancel()
            timeline?.stop()
        }
    }

    /**
     * Tweens the knob smoothly between frames while playing. Guarded on
     * [isPlaying] because [XweatherTimeline.onAdvance] also fires for
     * non-playback position changes (e.g. [attachTimeline]'s initial seek),
     * which should snap the knob directly rather than tween it.
     */
    private fun onTimelineAdvance() {
        if (!isPlaying) return
        if (scrubRestartPending) {
            // Reached the end last tick: snap to the beginning instead of tweening backwards.
            scrubRestartPending = false
            scrubAnimator?.cancel()
            scrubTarget = 0f
            ruler.progress = 0f
        } else {
            scrubTarget += scrubStep
            if (scrubTarget >= 1f) {
                scrubTarget = 1f
                scrubRestartPending = true
            }
            animateScrubTo(scrubTarget)
        }
    }

    private fun onRulerScrub(fraction: Float, fromUser: Boolean) {
        if (fromUser) {
            if (isPlaying) {
                isPlaying = false
                playButton.setImageResource(R.drawable.ic_play)
                scrubAnimator?.cancel()
            }
            timeline?.goTo(fraction)
            webTimeline?.goTo(fraction)
        }
        listener?.onProgressChanged(fraction, fromUser)
    }

    /** Smoothly moves the scrubber to [target] over one frame interval, instead of jumping. */
    private fun animateScrubTo(target: Float) {
        scrubAnimator?.cancel()
        scrubAnimator = ValueAnimator.ofFloat(ruler.progress, target).apply {
            duration = frameIntervalMillis
            interpolator = LinearInterpolator()
            addUpdateListener {
                ruler.progress = it.animatedValue as Float
            }
            start()
        }
    }
}

/** The hour-tick ruler + draggable knob; owns only drawing and touch, no map/animator awareness. */
private class RulerView(context: Context) : View(context) {

    var onScrub: ((fraction: Float, fromUser: Boolean) -> Unit)? = null

    var startHour: Int = 8
    var endHour: Int = 11
    private val minorTicksPerHour = 4

    /** 0f..1f position of the knob across the [startHour, endHour] range. */
    var progress: Float = 0.39f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** The fraction that represents "now" — rendered with the orange "Now" label. */
    var nowProgress: Float = 0.39f

    private fun dp(v: Float) = context.dp(v)

    private val trackStrokeWidth = dp(3f)
    private val knobRadius = dp(10f)
    private val majorTickLength = dp(8f)
    private val minorTickLength = dp(4f)

    /**
     * Half of the first/last hour label's text (e.g. "11AM") can be wider than the
     * knob-based margin, which would clip it against this view's own edge — so the
     * inset is whichever is larger: room for the knob, or room for those labels.
     */
    private val horizontalInset: Float
        get() {
            val labelHalfWidth = maxOf(
                hourLabelPaint.measureText(formatHourLabel(startHour)),
                hourLabelPaint.measureText(formatHourLabel(endHour)),
            ) / 2f
            return maxOf(knobRadius + dp(4f), labelHalfWidth + dp(2f))
        }

    private val elapsedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_track_elapsed)
        strokeWidth = trackStrokeWidth
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val remainingPaint = Paint(elapsedPaint).apply {
        color = ContextCompat.getColor(context, R.color.timeline_track_remaining)
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_tick)
        strokeWidth = dp(1.5f)
    }
    private val minorTickPaint = Paint(majorTickPaint).apply {
        strokeWidth = dp(1f)
        alpha = 160
    }
    private val nowTickPaint = Paint(majorTickPaint).apply {
        color = ContextCompat.getColor(context, R.color.timeline_now)
    }
    private val hourLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_label)
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_knob)
        style = Paint.Style.FILL
    }
    private val nowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_now)
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val nowDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.timeline_now)
        style = Paint.Style.FILL
    }

    private var isDragging = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dp(44f).roundToInt() + paddingTop + paddingBottom
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    private fun usableWidth() = (width - paddingLeft - paddingRight - 2 * horizontalInset)
        .coerceAtLeast(0f)

    private fun xForFraction(fraction: Float) =
        paddingLeft + horizontalInset + fraction * usableWidth()

    private fun fractionForX(x: Float): Float {
        val usable = usableWidth()
        if (usable <= 0f) return 0f
        return ((x - paddingLeft - horizontalInset) / usable).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0) return

        val trackY = paddingTop + dp(22f)
        val tickTop = trackY + dp(4f)
        val hourLabelY = tickTop + majorTickLength + dp(10f)

        val startX = paddingLeft + horizontalInset
        val endX = width - paddingRight - horizontalInset
        val knobX = xForFraction(progress)
        val nowX = xForFraction(nowProgress)

        // Track: elapsed (dark) to the left of the knob, remaining (light) to the right.
        canvas.drawLine(startX, trackY, knobX, trackY, elapsedPaint)
        canvas.drawLine(knobX, trackY, endX, trackY, remainingPaint)

        // Ticks + hour labels.
        val totalHours = endHour - startHour
        val totalTicks = totalHours * minorTicksPerHour
        for (i in 0..totalTicks) {
            val fraction = i.toFloat() / totalTicks
            val x = xForFraction(fraction)
            val isMajor = i % minorTicksPerHour == 0
            val paint = if (isMajor) majorTickPaint else minorTickPaint
            val length = if (isMajor) majorTickLength else minorTickLength
            canvas.drawLine(x, tickTop, x, tickTop + length, paint)
            if (isMajor) {
                val hour = startHour + i / minorTicksPerHour
                canvas.drawText(formatHourLabel(hour), x, hourLabelY, hourLabelPaint)
            }
        }

        // Persistent "Now" tick + label: always shown at nowProgress, independent
        // of wherever the scrub knob currently sits, so "now" stays visible while
        // scrubbing or playing instead of disappearing once the knob moves off it.
        val labelY = paddingTop + dp(8f)
        canvas.drawLine(nowX, tickTop, nowX, tickTop + majorTickLength, nowTickPaint)
        canvas.drawText("Now", nowX, labelY, nowTextPaint)
        canvas.drawCircle(nowX, labelY + dp(6f), dp(1.5f), nowDotPaint)

        canvas.drawCircle(knobX, trackY, knobRadius, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x)
            }
            MotionEvent.ACTION_MOVE -> if (isDragging) updateFromTouch(event.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun updateFromTouch(x: Float) {
        progress = fractionForX(x)
        onScrub?.invoke(progress, true)
    }

    private fun formatHourLabel(hour24: Int): String {
        val h = hour24 % 24
        val suffix = if (h < 12) "AM" else "PM"
        val h12 = when (h % 12) {
            0 -> 12
            else -> h % 12
        }
        return "$h12$suffix"
    }
}
