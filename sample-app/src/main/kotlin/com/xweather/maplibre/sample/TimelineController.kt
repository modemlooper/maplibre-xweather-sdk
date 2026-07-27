package com.xweather.maplibre.sample

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.xweather.webview.XweatherWebTimeline
import kotlin.math.roundToInt

private fun Context.dp(value: Float) = value * resources.displayMetrics.density

/**
 * A self-contained "radar remote": a play/pause button, a loading spinner, and
 * an hour-tick ruler with a draggable knob, wired directly to a shared
 * [XweatherWebTimeline]. Drop it into any layout, call [attachTimeline] once
 * the timeline exists, and this view owns playback, scrubbing, and the "Now"
 * indicator end to end — hosts don't need their own play/pause logic. The JS
 * SDK's timeline already advances smoothly on its own, so the knob just
 * mirrors [XweatherWebTimeline.position] directly.
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

    private var timeline: XweatherWebTimeline? = null
    private var isPlaying = false

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
     * Attaches the [XweatherWebTimeline] this control plays/scrubs. Can be
     * called as soon as the timeline exists (no need to wait for a first
     * load) — [showLoading]/[hideLoading] are driven by
     * [XweatherWebTimeline.onLoadStart]/[XweatherWebTimeline.onLoadComplete]
     * instead.
     */
    fun attachTimeline(timeline: XweatherWebTimeline) {
        this.timeline = timeline
        timeline.onAdvance = { ruler.progress = timeline.position }
        timeline.onLoadStart = { showLoading() }
        timeline.onLoadComplete = { hideLoading() }
        timeline.onRangeChange = {
            startHour = timeline.startHour
            endHour = timeline.endHour
            nowProgress = timeline.nowFraction
            ruler.progress = timeline.nowFraction
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
        timeline?.stop()
    }

    private fun togglePlayback() {
        isPlaying = !isPlaying
        playButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        if (isPlaying) timeline?.play() else timeline?.stop()
    }

    private fun onRulerScrub(fraction: Float, fromUser: Boolean) {
        if (fromUser) {
            if (isPlaying) {
                isPlaying = false
                playButton.setImageResource(R.drawable.ic_play)
            }
            timeline?.goTo(fraction)
        }
        listener?.onProgressChanged(fraction, fromUser)
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

        // Persistent "Now" label + tick: always shown at nowProgress, independent
        // of wherever the scrub knob currently sits, so "now" stays visible while
        // scrubbing or playing instead of disappearing once the knob moves off it.
        // The tick sits between the label and the track, above it rather than
        // among the hour ticks below.
        val labelY = paddingTop + dp(8f)
        canvas.drawText("Now", nowX, labelY, nowTextPaint)
        canvas.drawLine(nowX, labelY + dp(4f), nowX, trackY, nowTickPaint)

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
