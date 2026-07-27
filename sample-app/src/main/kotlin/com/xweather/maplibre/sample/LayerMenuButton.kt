package com.xweather.maplibre.sample

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

private fun Context.dp(value: Float) = value * resources.displayMetrics.density

/**
 * A self-contained "layers" button: tapping it drops down a checkbox menu
 * (Radar / Temperature / Wind Particles) for toggling Xweather map layers.
 * Drop it into any layout (e.g. anchored top-end over a map) and set
 * [listener] to react to checkbox changes — this view owns the button, the
 * popup menu, and each layer's checked state end to end.
 *
 * Toggled layers are expected to register with a shared `XweatherTimeline`
 * (see [MainActivity]), which has its own loading indicator (the bottom
 * timeline bar) — this menu doesn't show per-layer loading state itself.
 */
class LayerMenuButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Layer(val labelResId: Int) {
        RADAR(R.string.layer_radar),
        TEMPERATURE(R.string.layer_temperature),
        WIND_PARTICLES(R.string.layer_wind_particles),
    }

    fun interface OnLayerToggleListener {
        fun onLayerToggled(layer: Layer, enabled: Boolean)
    }

    /** Notified whenever the user checks/unchecks a layer in the menu. */
    var listener: OnLayerToggleListener? = null

    private val button: ImageButton
    private val checkBoxes = mutableMapOf<Layer, CheckBox>()
    private val checkedState = mutableMapOf<Layer, Boolean>()
    private var popup: PopupWindow? = null

    init {
        val buttonSize = context.dp(48f).roundToInt()
        button = ImageButton(context).apply {
            layoutParams = LayoutParams(buttonSize, buttonSize)
            setBackgroundResource(R.drawable.bg_layer_menu_button)
            setImageResource(R.drawable.ic_layers)
            val iconPadding = context.dp(12f).roundToInt()
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            contentDescription = context.getString(R.string.layer_menu_button)
            setOnClickListener { toggleMenu() }
        }
        addView(button)
    }

    /** Sets whether [layer]'s checkbox is checked, without notifying [listener]. */
    fun setLayerChecked(layer: Layer, checked: Boolean) {
        checkedState[layer] = checked
        checkBoxes[layer]?.isChecked = checked
    }

    /** True if [layer]'s checkbox is currently checked. */
    fun isLayerChecked(layer: Layer): Boolean = checkedState[layer] ?: false

    /** Dismisses the popup menu if it's open; call from the host's `onDestroy`/`onStop`. */
    fun dismissMenu() {
        popup?.dismiss()
    }

    private fun toggleMenu() {
        val current = popup
        if (current != null && current.isShowing) {
            current.dismiss()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        val content = buildMenuContent()
        val popupWindow = PopupWindow(
            content,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            elevation = context.dp(8f)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        popup = popupWindow

        // Right-align the menu under the button instead of hanging off-screen
        // to the right, by measuring its width before it's shown.
        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val xOffset = button.width - content.measuredWidth
        popupWindow.showAsDropDown(button, xOffset, context.dp(4f).roundToInt())
    }

    private fun buildMenuContent() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_layer_menu_card)
        val padding = context.dp(12f).roundToInt()
        setPadding(padding, padding, padding, padding)

        checkBoxes.clear()
        Layer.values().forEach { layer ->
            val checkBox = CheckBox(context).apply {
                text = context.getString(layer.labelResId)
                setTextColor(ContextCompat.getColor(context, R.color.layer_menu_text))
                isChecked = checkedState[layer] ?: false
                val vPad = context.dp(6f).roundToInt()
                setPadding(0, vPad, 0, vPad)
                setOnCheckedChangeListener { _, isChecked ->
                    checkedState[layer] = isChecked
                    listener?.onLayerToggled(layer, isChecked)
                }
            }
            checkBoxes[layer] = checkBox
            addView(checkBox)
        }
    }
}
