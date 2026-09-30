package com.phlox.tvwebbrowser.activity.main.dialogs

import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.phlox.tvwebbrowser.R

class PageErrorDialog(context: Context, message: Int, onRetry: () -> Unit, onHome: () -> Unit) :
    FocusRestoringDialog(context, R.style.BookmarksDialog) {
    private val retry: Button

    init {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val color = ContextCompat.getColor(context, R.color.day_night_text_color_contrast)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
        }
        panel.addView(TextView(context).apply {
            setText(R.string.page_load_failed_title)
            textSize = 22f
            setTextColor(color)
        })
        panel.addView(TextView(context).apply {
            setText(message)
            textSize = 18f
            setTextColor(color)
            setPadding(0, dp(16), 0, dp(24))
        })
        fun action(label: Int, callback: () -> Unit) = Button(context).apply {
            id = View.generateViewId()
            setText(label)
            textSize = 18f
            isAllCaps = false
            isFocusableInTouchMode = true
            setTextColor(color)
            setBackgroundResource(R.drawable.button_bg_selector)
            setOnClickListener { dismiss(); callback() }
        }
        retry = action(R.string.page_retry, onRetry)
        panel.addView(retry, LinearLayout.LayoutParams(-1, dp(52)))
        panel.addView(action(R.string.home_page, onHome), LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        setContentView(panel)
        setCanceledOnTouchOutside(false)
    }

    override fun onStart() {
        super.onStart()
        val metrics = context.resources.displayMetrics
        window?.setLayout(minOf((600 * metrics.density).toInt(), (metrics.widthPixels * .85f).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT)
        retry.requestFocus()
    }
}
