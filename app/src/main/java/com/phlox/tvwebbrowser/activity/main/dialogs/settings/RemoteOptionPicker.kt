package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import com.phlox.tvwebbrowser.R

/** A focusable setting row with a remote-friendly modal choice list. */
class RemoteOptionPicker @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatSpinner(context, attrs) {
    private var optionDialog: Dialog? = null
    private val indicator = ContextCompat.getDrawable(context, R.drawable.ic_keyboard_arrow_right_grey_900_18dp)

    init {
        indicator?.mutate()?.setTint(ContextCompat.getColor(context, R.color.day_night_text_color_contrast))
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundResource(R.drawable.button_bg_selector)
        minimumHeight = dp(52)
        setPadding(dp(12), dp(4), dp(40), dp(4))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val size = dp(24)
        indicator?.setBounds(width - dp(32), (height - size) / 2,
            width - dp(8), (height + size) / 2)
        indicator?.draw(canvas)
    }

    override fun performClick(): Boolean {
        val options = adapter ?: return false
        if (optionDialog?.isShowing == true || options.count == 0) return true
        val currentSelection = selectedItemPosition.coerceIn(0, options.count - 1)
        val dialog = Dialog(context, R.style.BookmarksDialog)
        optionDialog = dialog
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        panel.addView(TextView(context).apply {
            text = prompt ?: contentDescription
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.day_night_text_color_contrast))
            setPadding(dp(8), 0, dp(8), dp(16))
        })
        val choices = ListView(context).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            divider = null
            dividerHeight = dp(6)
            isFocusableInTouchMode = true
            adapter = ArrayAdapter(context, R.layout.remote_option_item,
                (0 until options.count).map { options.getItem(it).toString() })
            setItemChecked(currentSelection, true)
            setSelection(currentSelection)
            setSelector(android.R.color.transparent)
            setOnItemClickListener { _, _, position, _ ->
                dialog.dismiss()
                this@RemoteOptionPicker.setSelection(position)
            }
            setOnKeyListener { _, code, _ ->
                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
            }
        }
        val listHeight = minOf(dp(options.count * 62), (resources.displayMetrics.heightPixels * .65f).toInt())
        panel.addView(choices, LinearLayout.LayoutParams(-1, listHeight))
        dialog.setContentView(panel)
        dialog.setOnDismissListener {
            optionDialog = null
            requestFocus()
        }
        dialog.show()
        dialog.window?.setLayout(minOf(dp(560), (resources.displayMetrics.widthPixels * .85f).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT)
        choices.requestFocus()
        choices.post { choices.setSelection(currentSelection) }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.repeatCount == 0) {
                    focusSearch(if (keyCode == KeyEvent.KEYCODE_DPAD_UP) View.FOCUS_UP else View.FOCUS_DOWN)?.requestFocus()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (!event.isCanceled) performClick()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
            else -> super.onKeyUp(keyCode, event)
        }
    }

    override fun onDetachedFromWindow() {
        optionDialog?.dismiss()
        super.onDetachedFromWindow()
    }
}
