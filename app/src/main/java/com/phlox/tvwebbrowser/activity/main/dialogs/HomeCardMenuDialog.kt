package com.phlox.tvwebbrowser.activity.main.dialogs

import android.content.Context
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.phlox.tvwebbrowser.R

class HomeCardMenuDialog(context: Context, title: String, canMovePrevious: Boolean,
    canMoveNext: Boolean, onAction: (Int) -> Unit) : FocusRestoringDialog(context, R.style.BookmarksDialog) {
    private val buttons = mutableListOf<Button>()

    init {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        val textColor = ContextCompat.getColor(context, R.color.day_night_text_color_contrast)
        content.addView(TextView(context).apply {
            text = title
            textSize = 20f
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(16))
        })
        val labels = listOf(R.string.delete, R.string.edit, R.string.home_card_move_previous,
            R.string.home_card_move_next)
        labels.forEachIndexed { index, label ->
            val button = Button(context).apply {
                id = android.view.View.generateViewId()
                setText(label)
                textSize = 18f
                isAllCaps = false
                isFocusableInTouchMode = true
                setTextColor(textColor)
                setBackgroundResource(R.drawable.button_bg_selector)
                isEnabled = when (index) { 2 -> canMovePrevious; 3 -> canMoveNext; else -> true }
                alpha = if (isEnabled) 1f else 0.4f
                setOnClickListener { dismiss(); onAction(index) }
            }
            content.addView(button, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
            buttons.add(button)
        }
        setContentView(content)
        // A held confirm key must not activate the first action when it is released.
        var waitingForRelease = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A)) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) waitingForRelease = false
                if (waitingForRelease) {
                    if (event.action == KeyEvent.ACTION_UP) waitingForRelease = false
                    true
                } else false
            } else false
        }
    }

    override fun onStart() {
        super.onStart()
        val metrics = context.resources.displayMetrics
        window?.setLayout(minOf((340 * metrics.density).toInt(), (metrics.widthPixels * .9f).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT)
        buttons[1].requestFocus()
    }

}
